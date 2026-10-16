#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define MIN(a,b) ((a)<(b)?(a):(b))

// Defer tasks until wait and execute in reverse order, exercising the same
// partitioning as multiple workers without relying on scheduling luck.
typedef struct { uint8_t numThreads; } ThreadPool;
static void (*work[8])(void*);
static void* params[8];
static int queued, dispatches;
static void ThreadPool_run(ThreadPool* pool, void (*fn)(void*), void* arg) {
    (void)pool; assert(queued < 8); work[queued]=fn; params[queued++]=arg; dispatches++;
}
static void ThreadPool_wait(ThreadPool* pool) {
    (void)pool; while(queued) { --queued; work[queued](params[queued]); }
}
#include "bc_decoder.h"
#include "bc_layout.h"

static int cases;
static void pixels(int w,int h,int bc,bool signedFormat,bool padded) {
    const int blockSize=(bc==1 || bc==4)?8:16;
    size_t columns=(w+3)/4,rows=(h+3)/4;
    size_t pitch=(columns+(padded?3:0))*blockSize;
    size_t bytes=pitch*rows,output=(size_t)w*h*4;
    uint8_t* allocation=malloc(bytes+4);
    uint8_t* input=allocation+4; // intentionally not 8-byte aligned
    uint8_t* actual=malloc(output+16),*serial=malloc(output+16);
    assert(allocation&&actual&&serial);
    memset(input,0xa5,bytes);memset(actual,0xcd,output+16);memset(serial,0xef,output+16);
    for(size_t y=0;y<rows;y++)for(size_t x=0;x<columns;x++) {
        uint8_t* b=input+y*pitch+x*blockSize;memset(b,0,blockSize);
        uint8_t red=(uint8_t)(1+(x+y*7)%120), green=(uint8_t)(1+(x*3+y)%120);
        if(bc<=3) {
            // Solid red RGB565 with index zero. BC2/3 alpha is fully opaque.
            b[bc==1?1:9]=0xf8;
            if(bc==2)memset(b,0xff,8);
            if(bc==3)b[0]=255;
        } else {
            b[0]=signedFormat?(uint8_t)(-(int)red):red;
            if(bc==5)b[8]=signedFormat?(uint8_t)(-(int)green):green;
        }
    }
    ThreadPool pool={8};dispatches=0;
    BCDecoder_decodePitched(input,actual,w,h,bc,!signedFormat,pitch,&pool);
    if((size_t)w*h<16384)assert(dispatches==0);
    if(w==257&&h==259)assert(dispatches>1);
    BCDecoder_decodePitched(input,serial,w,h,bc,!signedFormat,pitch,NULL);
    assert(!memcmp(actual,serial,output));
    for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
        uint8_t* pixel=actual+((size_t)y*w+x)*4;
        if(bc<=3)assert(pixel[0]==0&&pixel[1]==0&&pixel[2]==255&&pixel[3]==255);
        else {
            int red=1+(x/4+(y/4)*7)%120,green=1+((x/4)*3+y/4)%120;
            assert(pixel[0]==(uint8_t)(signedFormat?-red:red));
            assert(pixel[1]==(bc==4?0:(uint8_t)(signedFormat?-green:green)));
            assert(pixel[2]==0&&pixel[3]==(signedFormat?127:255));
        }
    }
    for(size_t i=output;i<output+16;i++)assert(actual[i]==0xcd&&serial[i]==0xef);
    free(allocation);free(actual);free(serial);cases++;
}

static void colorAndSignedEdges(void) {
    BCColor color={0,0,0xffffffffu};
    for(int i=0;i<16;i++)assert(BCDecoder_getColorIdx(&color,i)==3);
    uint8_t block[16]={0},output[64];
    // BC1 transparent selector versus RGB opaque black.
    memset(block+4,0xff,4);
    BCDecoder_decode(block,output,4,4,1,false,NULL);
    for(int i=0;i<16;i++)assert(output[i*4+3]==0);
    BCDecoder_decode(block,output,4,4,1,true,NULL);
    for(int i=0;i<16;i++)assert(output[i*4+3]==255);
    // SNORM -128 must decode as -127, not wrap into an UNORM positive value.
    memset(block,0,16);block[0]=128;
    BCDecoder_decode(block,output,4,4,4,false,NULL);
    for(int i=0;i<16;i++)assert((int8_t)output[i*4]==-127&&output[i*4+3]==127);
    // Verify the six-interpolant branch (index 2), and signed extremes 6/7.
    uint64_t data=0;data=127u|((uint64_t)129<<8);
    for(int i=0;i<16;i++)data|=(uint64_t)2<<(16+3*i);
    memcpy(block,&data,8);BCDecoder_decode(block,output,4,4,4,false,NULL);
    for(int i=0;i<16;i++)assert((int8_t)output[i*4]==90);
    for(int index=6;index<=7;index++) {
        data=128u|((uint64_t)127<<8);
        for(int i=0;i<16;i++)data|=(uint64_t)index<<(16+3*i);
        memcpy(block,&data,8);BCDecoder_decode(block,output,4,4,4,false,NULL);
        for(int i=0;i<16;i++)assert((int8_t)output[i*4]==(index==6?-127:127));
    }
}

static void layouts(void) {
    VkBufferImageCopy r={0};BCUploadLayout l;
    r.imageExtent=(VkExtent3D){5,7,1};r.imageSubresource.layerCount=6;
    r.bufferRowLength=12;r.bufferImageHeight=12;
    assert(BCUploadLayout_get(&r,8,400,&l));
    assert(l.sourceRowPitch==24&&l.sourceSlicePitch==72&&l.sourceSize==400);
    assert(l.outputSliceSize==140&&l.outputSize==840&&l.slices==6);
    assert(!BCUploadLayout_get(&r,8,399,&l));
    r.imageExtent.depth=2;assert(!BCUploadLayout_get(&r,8,1000,&l));
    r.imageSubresource.layerCount=1;assert(BCUploadLayout_get(&r,8,112,&l)&&l.slices==2);
    r.bufferRowLength=6;assert(!BCUploadLayout_get(&r,8,1000,&l));
    r.bufferRowLength=4;assert(!BCUploadLayout_get(&r,8,1000,&l));
    r.bufferRowLength=0;r.bufferImageHeight=0;r.imageExtent=(VkExtent3D){1,1,1};
    assert(BCUploadLayout_get(&r,8,8,&l)&&l.outputSize==4&&l.sourceSize==8);
    assert(!BCUploadLayout_get(&r,8,7,&l));
    r.imageExtent.width=0;assert(!BCUploadLayout_get(&r,8,8,&l));
    r.imageExtent=(VkExtent3D){65535,65535,1};assert(!BCUploadLayout_get(&r,16,UINT64_MAX,&l));
    r.imageExtent=(VkExtent3D){4,4,1};r.imageSubresource.layerCount=UINT32_MAX;
    assert(!BCUploadLayout_get(&r,16,UINT64_MAX,&l));
    r.imageSubresource.layerCount=1;r.bufferRowLength=0xfffffffcu;r.bufferImageHeight=0xfffffffcu;
    assert(!BCUploadLayout_get(&r,16,15,&l));
    assert(BCDecoder_outputFormat(VK_FORMAT_BC1_RGB_SRGB_BLOCK)==VK_FORMAT_B8G8R8A8_SRGB);
    assert(BCDecoder_outputFormat(VK_FORMAT_BC3_UNORM_BLOCK)==VK_FORMAT_B8G8R8A8_UNORM);
    assert(BCDecoder_outputFormat(VK_FORMAT_BC4_UNORM_BLOCK)==VK_FORMAT_R8G8B8A8_UNORM);
    assert(BCDecoder_outputFormat(VK_FORMAT_BC5_SNORM_BLOCK)==VK_FORMAT_R8G8B8A8_SNORM);
    assert(BCDecoder_outputFormat(VK_FORMAT_BC7_UNORM_BLOCK)==VK_FORMAT_UNDEFINED);
    assert(BCDecoder_outputFormat(VK_FORMAT_ASTC_4x4_UNORM_BLOCK)==VK_FORMAT_UNDEFINED);
    assert(BCDecoder_outputFormat(VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK)==VK_FORMAT_UNDEFINED);
    VkFormat views[]={VK_FORMAT_BC1_RGB_UNORM_BLOCK,VK_FORMAT_BC1_RGBA_UNORM_BLOCK,
            VK_FORMAT_R32G32_UINT,VK_FORMAT_BC1_RGB_SRGB_BLOCK};
    assert(BCDecoder_mapViewFormats(views,4,VK_FORMAT_B8G8R8A8_UNORM)==2);
    assert(views[0]==VK_FORMAT_B8G8R8A8_UNORM&&views[1]==VK_FORMAT_B8G8R8A8_SRGB);
    views[0]=VK_FORMAT_R32G32_UINT;
    assert(BCDecoder_mapViewFormats(views,1,VK_FORMAT_R8G8B8A8_SNORM)==1);
    assert(views[0]==VK_FORMAT_R8G8B8A8_SNORM);
    assert(BCDecoder_mapViewFormats(NULL,0,VK_FORMAT_R8G8B8A8_SNORM)==0);
}

int main(void) {
    int sizes[][2]={{1,1},{3,7},{4,4},{4,256},{128,128},{257,259}};
    for(size_t i=0;i<sizeof(sizes)/sizeof(sizes[0]);i++)for(int bc=1;bc<=5;bc++)
        for(int padded=0;padded<2;padded++) {
            pixels(sizes[i][0],sizes[i][1],bc,false,padded);
            if(bc>=4)pixels(sizes[i][0],sizes[i][1],bc,true,padded);
        }
    colorAndSignedEdges();layouts();
    printf("PASS: %d BC pixel/stride/worker scenarios plus format, bounds and SNORM edges\n",cases);
}
