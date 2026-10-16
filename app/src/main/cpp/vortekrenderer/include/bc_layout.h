#ifndef VORTEK_BC_LAYOUT_H
#define VORTEK_BC_LAYOUT_H

#include <stdint.h>
#include <stdbool.h>
#include "vulkan/vulkan_core.h"

typedef struct BCUploadLayout {
    uint64_t sourceRowPitch;
    uint64_t sourceSlicePitch;
    uint64_t sourceSize;
    uint64_t outputSliceSize;
    uint32_t outputSize;
    uint32_t slices;
} BCUploadLayout;

// Validate all arithmetic before mapping or reading the compressed allocation.
// Output is tightly packed RGBA/BGRA, irrespective of source row/slice padding.
static inline bool BCUploadLayout_get(const VkBufferImageCopy* r, uint32_t blockSize,
        uint64_t available, BCUploadLayout* layout) {
    uint32_t w = r->imageExtent.width, h = r->imageExtent.height;
    uint32_t depth = r->imageExtent.depth, layers = r->imageSubresource.layerCount;
    if (!w || !h || w > UINT16_MAX || h > UINT16_MAX || !depth || !layers ||
            (depth != 1 && layers != 1) || (blockSize != 8 && blockSize != 16)) return false;
    if ((r->bufferRowLength && (r->bufferRowLength < w || r->bufferRowLength % 4)) ||
            (r->bufferImageHeight && (r->bufferImageHeight < h || r->bufferImageHeight % 4))) return false;
    uint64_t rowTexels = r->bufferRowLength ? r->bufferRowLength : w;
    uint64_t sliceTexels = r->bufferImageHeight ? r->bufferImageHeight : h;
    uint64_t rowPitch = ((rowTexels + 3) / 4) * blockSize;
    uint64_t sliceRows = (sliceTexels + 3) / 4;
    if (rowPitch > UINT64_MAX / sliceRows) return false;
    uint64_t slicePitch = rowPitch * sliceRows;
    uint64_t slices = (uint64_t)depth * layers;
    uint64_t outputSlice = (uint64_t)w * h * 4;
    if (outputSlice > UINT32_MAX || slices > UINT32_MAX / outputSlice) return false;
    uint64_t lastSlice = ((uint64_t)(h + 3) / 4 - 1) * rowPitch + ((w + 3) / 4) * blockSize;
    if (lastSlice > available || (slices - 1) > (available - lastSlice) / slicePitch) return false;
    layout->sourceRowPitch = rowPitch;
    layout->sourceSlicePitch = slicePitch;
    layout->sourceSize = (slices - 1) * slicePitch + lastSlice;
    layout->outputSliceSize = outputSlice;
    layout->outputSize = (uint32_t)(outputSlice * slices);
    layout->slices = (uint32_t)slices;
    return true;
}

// Only formats actually decoded by BCDecoder may be emulated. Other formats
// (ETC, ASTC, BC6/7) must retain their native support or fail normally.
static inline VkFormat BCDecoder_outputFormat(VkFormat format) {
    switch (format) {
        case VK_FORMAT_BC1_RGB_UNORM_BLOCK:
        case VK_FORMAT_BC1_RGBA_UNORM_BLOCK:
        case VK_FORMAT_BC2_UNORM_BLOCK:
        case VK_FORMAT_BC3_UNORM_BLOCK:
            return VK_FORMAT_B8G8R8A8_UNORM;
        case VK_FORMAT_BC1_RGB_SRGB_BLOCK:
        case VK_FORMAT_BC1_RGBA_SRGB_BLOCK:
        case VK_FORMAT_BC2_SRGB_BLOCK:
        case VK_FORMAT_BC3_SRGB_BLOCK:
            return VK_FORMAT_B8G8R8A8_SRGB;
        case VK_FORMAT_BC4_UNORM_BLOCK:
        case VK_FORMAT_BC5_UNORM_BLOCK:
            return VK_FORMAT_R8G8B8A8_UNORM;
        case VK_FORMAT_BC4_SNORM_BLOCK:
        case VK_FORMAT_BC5_SNORM_BLOCK:
            return VK_FORMAT_R8G8B8A8_SNORM;
        default:
            return VK_FORMAT_UNDEFINED;
    }
}

static inline uint32_t BCDecoder_mapViewFormats(VkFormat* formats, uint32_t count, VkFormat fallback) {
    uint32_t written = 0;
    for (uint32_t i = 0; i < count; i++) {
        VkFormat converted = BCDecoder_outputFormat(formats[i]);
        if (converted == VK_FORMAT_UNDEFINED) continue;
        bool duplicate = false;
        for (uint32_t j = 0; j < written; j++) if (formats[j] == converted) duplicate = true;
        if (!duplicate) formats[written++] = converted;
    }
    // Block-texel integer aliases cannot be used with the expanded image.
    if (count && !written) { formats[0] = fallback; written = 1; }
    return written;
}

#endif
