// based on SwiftShader (https://github.com/google/swiftshader/blob/master/src/Device/BC_Decoder.cpp)

#ifndef BC_DECODER_H
#define BC_DECODER_H

#define BC_BLOCK 4u
#define BC_DECODER_MAX_TASKS 8
#define BC_DECODER_MIN_BLOCKS_PER_TASK 1024
#define BC_ROUNDUP(x) (x + (-x & 3))
#define BC_ROUNDDOWN(x) (x & ~3)

typedef struct BCColor {
    uint16_t c0;
    uint16_t c1;
    uint32_t idx;
} BCColor;

typedef struct BCTask {
    const uint8_t* src;
    uint8_t* dst;
    uint16_t width;
    uint16_t startY;
    uint16_t endY;
    size_t sourceRowPitch;
    uint8_t bcN;
    bool isAlpha;
    bool isSigned;
} BCTask;

static inline uint32_t BCDecoder_getColorIdx(const BCColor* color, int i) {
    int offset = i << 1;
    return (color->idx >> offset) & 0x3u;
}

static inline uint8_t BCDecoder_getChannelIdx(uint64_t data, int i) {
    int offset = i * 3 + 16;
    return (uint8_t)((data & (0x7ull << offset)) >> offset);
}

static inline uint8_t BCDecoder_getAlpha(uint64_t data, int i) {
    int offset = i << 2;
    int alpha = (data & (0xFull << offset)) >> offset;
    return (uint8_t)(alpha | (alpha << 4));
}

static inline void BCDecoder_extract565(uint8_t* c, uint32_t c565) {
    c[0] = ((c565 & 0x001F) << 3) | ((c565 & 0x001C) >> 2);
    c[1] = ((c565 & 0x07E0) >> 3) | ((c565 & 0x0600) >> 9);
    c[2] = ((c565 & 0xF800) >> 8) | ((c565 & 0xE000) >> 13);
}

static inline void BCDecoder_decodeAlpha(uint64_t data, uint8_t* dst, int x, int y, int width, int endY, int stride) {
    dst += 3;
    for (int j = 0, srcOffset; j < BC_BLOCK && (y + j) < endY; j++, dst += stride) {
        uint8_t* dstRow = dst;
        srcOffset = j * BC_BLOCK;
        for (int i = 0; i < BC_BLOCK && (x + i) < width; i++, dstRow += 4) {
            *dstRow = BCDecoder_getAlpha(data, srcOffset + i);
        }
    }
}

static inline void BCDecoder_decodeChannel(uint64_t data, uint8_t* dst, int x, int y, int width, int endY, int stride, int channel, bool isSigned) {
    int c[8] = {0};

    if (isSigned) {
        c[0] = (int8_t)(data & 0xFF);
        c[1] = (int8_t)((data & 0xFF00) >> 8);
        // BC SNORM defines -128 as -127, including before interpolation.
        if (c[0] == -128) c[0] = -127;
        if (c[1] == -128) c[1] = -127;
    }
    else {
        c[0] = (uint8_t)(data & 0xFF);
        c[1] = (uint8_t)((data & 0xFF00) >> 8);
    }

    if (c[0] > c[1]) {
        for (int i = 2; i < 8; i++) {
            c[i] = ((8 - i) * c[0] + (i - 1) * c[1]) / 7;
        }
    }
    else {
        for (int i = 2; i < 6; i++) {
            c[i] = ((6 - i) * c[0] + (i - 1) * c[1]) / 5;
        }

        c[6] = isSigned ? -127 : 0;
        c[7] = isSigned ?  127 : 255;
    }

    for (int i, j = 0, dstOffset, srcOffset; j < BC_BLOCK && (y + j) < endY; j++) {
        dstOffset = j * stride + channel;
        srcOffset = j * BC_BLOCK;
        for (i = 0; i < BC_BLOCK && (x + i) < width; i++) {
            dst[dstOffset + (i * 4)] = (uint8_t)c[BCDecoder_getChannelIdx(data, srcOffset + i)];
        }
    }
}

static inline void BCDecoder_decodeColor(const BCColor* color, uint8_t* dst, int x, int y, int width, int endY, int stride, bool hasAlphaChannel, bool hasSeparateAlpha) {
    uint8_t c[4][4] = {0};
    c[0][3] = 255;
    c[1][3] = 255;
    c[2][3] = 255;
    c[3][3] = 255;

    BCDecoder_extract565(c[0], color->c0);
    BCDecoder_extract565(c[1], color->c1);

    if (hasSeparateAlpha || (color->c0 > color->c1)) {
        for (int i = 0; i < 4; i++) {
            c[2][i] = ((c[0][i] * 2) + c[1][i]) / 3;
            c[3][i] = ((c[1][i] * 2) + c[0][i]) / 3;
        }
    }
    else {
        for (int i = 0; i < 4; i++) c[2][i] = (c[0][i] + c[1][i]) >> 1;
        if (hasAlphaChannel) c[3][3] = 0;
    }

    for (int i, j = 0, idx, dstOffset, srcOffset; j < BC_BLOCK && (y + j) < endY; j++) {
        dstOffset = j * stride;
        srcOffset = j * BC_BLOCK;
        for (i = 0; i < BC_BLOCK && (x + i) < width; i++, dstOffset += 4) {
            idx = BCDecoder_getColorIdx(color, srcOffset + i);
            dst[dstOffset+0] = c[idx][0];
            dst[dstOffset+1] = c[idx][1];
            dst[dstOffset+2] = c[idx][2];
            dst[dstOffset+3] = c[idx][3];
        }
    }
}

static inline void BCDecoder_decodeThread(void* param) {
    const BCTask* task = param;
    const int stride = task->width * 4;
    const int blockSize = (task->bcN == 1 || task->bcN == 4) ? 8 : 16;

    for (int y = task->startY; y < task->endY; y += BC_BLOCK) {
        const uint8_t* row = task->src + ((y - task->startY) / BC_BLOCK) * task->sourceRowPitch;
        uint8_t* dstRow = task->dst + (y - task->startY) * stride;
        for (int x = 0; x < task->width; x += BC_BLOCK) {
            const uint8_t* block = row + (x / BC_BLOCK) * blockSize;
            uint8_t* dst = dstRow + x * 4;
            BCColor color;
            uint64_t first, second;
            // A Vulkan buffer offset need not have uint64_t alignment.
            memcpy(&first, block, sizeof(first));
            if (task->bcN <= 3) {
                memcpy(&color, block + (task->bcN == 1 ? 0 : 8), sizeof(color));
                BCDecoder_decodeColor(&color, dst, x, y, task->width, task->endY,
                        stride, task->isAlpha, task->bcN != 1);
                if (task->bcN == 2)
                    BCDecoder_decodeAlpha(first, dst, x, y, task->width, task->endY, stride);
                else if (task->bcN == 3)
                    BCDecoder_decodeChannel(first, dst, x, y, task->width, task->endY, stride, 3, false);
            }
            else {
                // BC4/5 use RGBA output; missing channels are (0, 0, 1).
                for (int j = 0; j < BC_BLOCK && y + j < task->endY; j++) {
                    for (int i = 0; i < BC_BLOCK && x + i < task->width; i++) {
                        uint8_t* pixel = dst + j * stride + i * 4;
                        pixel[0] = pixel[1] = pixel[2] = 0;
                        pixel[3] = task->isSigned ? 127 : 255;
                    }
                }
                BCDecoder_decodeChannel(first, dst, x, y, task->width, task->endY, stride, 0, task->isSigned);
                if (task->bcN == 5) {
                    memcpy(&second, block + 8, sizeof(second));
                    BCDecoder_decodeChannel(second, dst, x, y, task->width, task->endY, stride, 1, task->isSigned);
                }
            }
        }
    }
}

static inline void BCDecoder_decodePitched(const uint8_t* src, uint8_t* dst, int width, int height,
        int bcN, bool isNoAlphaU, size_t sourceRowPitch, ThreadPool* threadPool) {
    if (width <= 0 || height <= 0 || width > UINT16_MAX || height > UINT16_MAX || bcN < 1 || bcN > 5) return;
    const bool isAlpha = (bcN == 1) && !isNoAlphaU;
    const bool isSigned = (bcN == 4 || bcN == 5) && !isNoAlphaU;
    const int stride = width * 4;
    const int blockRows = (height + 3) / 4;
    const int blockColumns = (width + 3) / 4;
    const int blockSize = (bcN == 1 || bcN == 4) ? 8 : 16;
    if (sourceRowPitch < (size_t)blockColumns * blockSize) return;

    // Tiny and narrow mip levels stay on the caller: no heap allocation or
    // worker wakeups. Large images keep bounded parallel decoding.
    int numTasks = 1;
    if (threadPool && threadPool->numThreads > 1) {
        numTasks = MIN(BC_DECODER_MAX_TASKS, threadPool->numThreads);
        numTasks = MIN(numTasks, blockRows);
        numTasks = MIN(numTasks, (blockRows * blockColumns) / BC_DECODER_MIN_BLOCKS_PER_TASK);
        if (numTasks < 1) numTasks = 1;
    }
    BCTask tasks[BC_DECODER_MAX_TASKS] = {0};
    const int rowsPerTask = blockRows / numTasks;
    for (int i = 0; i < numTasks; i++) {
        BCTask* task = &tasks[i];
        task->bcN = bcN;
        task->width = width;
        task->startY = i * rowsPerTask * BC_BLOCK;
        task->endY = i < numTasks - 1 ? task->startY + rowsPerTask * BC_BLOCK : height;
        task->isAlpha = isAlpha;
        task->isSigned = isSigned;
        task->sourceRowPitch = sourceRowPitch;
        task->src = src + (task->startY / BC_BLOCK) * sourceRowPitch;
        task->dst = dst + (size_t)task->startY * stride;
        if (numTasks > 1) ThreadPool_run(threadPool, BCDecoder_decodeThread, task);
        else BCDecoder_decodeThread(task);
    }
    if (numTasks > 1) ThreadPool_wait(threadPool);
}

static inline void BCDecoder_decode(const uint8_t* src, uint8_t* dst, int width, int height,
        int bcN, bool isNoAlphaU, ThreadPool* threadPool) {
    const int blockSize = (bcN == 1 || bcN == 4) ? 8 : 16;
    BCDecoder_decodePitched(src, dst, width, height, bcN, isNoAlphaU,
            ((size_t)width + 3) / 4 * blockSize, threadPool);
}
#endif
