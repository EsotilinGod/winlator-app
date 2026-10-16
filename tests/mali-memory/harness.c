#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include <sys/mman.h>
#include "vulkan/vulkan_core.h"
#define CACHE_MIN_IMAGE_WIDTH 1024
#define MEMFREE(p) free(p)
typedef struct { VkMemoryType* memoryTypes; int memoryTypeCount; } DeviceMemoryInfo;
static VkMemoryType types[4];
static DeviceMemoryInfo deviceMemoryInfo={types,4};
static VkExternalMemoryFeatureFlags features;
static bool compatible=true;
static void external(VkPhysicalDevice p,const VkPhysicalDeviceExternalBufferInfo* i,VkExternalBufferProperties* o) {
 assert(i->usage & VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
 assert(i->usage & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
 o->externalMemoryProperties.externalMemoryFeatures=features;
 o->externalMemoryProperties.compatibleHandleTypes=compatible?i->handleType:0;
}
typedef struct { uint64_t allocationSize; int fd; } ResourceMemory;
typedef struct { ResourceMemory* memory; uint64_t memoryOffset; } Bound;
typedef Bound TextureDecoder_BoundBuffer;
typedef struct { int width,height; VkFormat format; VkImage image; } TextureDecoder_Image;
typedef struct { Bound* srcBuffer; TextureDecoder_Image* dstImage; VkImageLayout dstImageLayout; VkBufferImageCopy region; } TextureDecoder_BufferImageCopy;
typedef struct { TextureDecoder_BufferImageCopy* next; } ArrayDeque;
typedef struct { ArrayDeque bufferImageCopies; void* decompressedData; uint32_t bufferSize; int imageCacheSize; void* threadPool; VkDevice device; VkQueue queue; VkCommandBuffer commandBuffer; VkBuffer buffer; VkFence fence; } TextureDecoder;
static int creates,hashes,reads,decodes,writes,submits,unmaps;
static bool cacheHit,failCreate;
static char input[1024*1024];
static bool ArrayDeque_isEmpty(ArrayDeque* q){return !q->next;}
static void* ArrayDeque_removeFirst(ArrayDeque* q){void* p=q->next;q->next=NULL;return p;}
static void* mockMmap(void* p,size_t n,int prot,int flags,int fd,long o){return input;}
static int mockMunmap(void* p,size_t n){unmaps++;return 0;}
#define mmap mockMmap
#define munmap mockMunmap
static void getBCInfo(VkFormat f,int* bc,int* block,bool* alpha){*bc=1;*block=8;*alpha=false;}
static bool createDecodeBuffer(TextureDecoder* t,uint32_t n){creates++;free(t->decompressedData);t->decompressedData=NULL;t->bufferSize=0;if(failCreate)return false;t->decompressedData=malloc(n);assert(t->decompressedData);t->bufferSize=n;return true;}
static uint64_t murmurHash64(void* p,int n,VkFormat f){hashes++;return 42;}
static bool readCachedImage(TextureDecoder_Image* i,uint64_t h,void* p){reads++;assert(p);if(cacheHit)memset(p,0x5a,(size_t)i->width*i->height*4);return cacheHit;}
static void writeImageToCache(TextureDecoder* t,TextureDecoder_Image* i,uint64_t h){writes++;}
static void BCDecoder_decode(void* in,void* out,int w,int h,int bc,bool alpha,void* pool){assert(out);decodes++;}
static VkResult begin(VkCommandBuffer c,const VkCommandBufferBeginInfo* i){return VK_SUCCESS;}
static void copy(VkCommandBuffer c,VkBuffer b,VkImage i,VkImageLayout l,uint32_t n,const VkBufferImageCopy* r){}
static VkResult end(VkCommandBuffer c){return VK_SUCCESS;}
static VkResult submit(VkQueue q,uint32_t n,const VkSubmitInfo* i,VkFence f){submits++;return VK_SUCCESS;}
static VkResult wait(VkDevice d,uint32_t n,const VkFence* f,VkBool32 all,uint64_t timeout){return VK_SUCCESS;}
static VkResult reset(VkDevice d,uint32_t n,const VkFence* f){return VK_SUCCESS;}
static struct {
 PFN_vkGetPhysicalDeviceExternalBufferProperties vkGetPhysicalDeviceExternalBufferProperties;
 PFN_vkBeginCommandBuffer vkBeginCommandBuffer;
 PFN_vkCmdCopyBufferToImage vkCmdCopyBufferToImage;
 PFN_vkEndCommandBuffer vkEndCommandBuffer;
 PFN_vkQueueSubmit vkQueueSubmit;
 PFN_vkWaitForFences vkWaitForFences;
 PFN_vkResetFences vkResetFences;
} vulkanWrapper={external,begin,copy,end,submit,wait,reset};
/* PRODUCTION */
static void textureCase(bool hit,int cache,bool fail,bool smallBuffer,bool partial){
 creates=hashes=reads=decodes=writes=submits=unmaps=0;cacheHit=hit;failCreate=fail;
 ResourceMemory mem={sizeof(input),1};Bound bound={&mem,0};TextureDecoder_Image image={1024,1024,VK_FORMAT_BC1_RGB_UNORM_BLOCK,VK_NULL_HANDLE};
 TextureDecoder t={0};t.imageCacheSize=cache;
 if(smallBuffer){t.bufferSize=16;t.decompressedData=malloc(16);}
 TextureDecoder_BufferImageCopy* c=calloc(1,sizeof(*c));c->srcBuffer=&bound;c->dstImage=&image;c->region.imageExtent=(VkExtent3D){1024,1024,1};c->region.imageSubresource.layerCount=1;
 if(partial){image.width=2048;c->region.imageSubresource.mipLevel=1;}
 t.bufferImageCopies.next=c;TextureDecoder_decodeAll(&t);
 assert(creates==1 && unmaps==1);
 if(fail){assert(!reads&&!hashes&&!decodes&&!submits);}
 else {
  assert(submits==1);
  if(cache>0&&!partial){assert(hashes==1&&reads==1);assert(decodes==!hit);assert(writes==!hit);}
  else {assert(!hashes&&!reads&&decodes==1&&!writes);}
 }
 free(t.decompressedData);
}
int main(void){
 types[0].propertyFlags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
 types[1].propertyFlags=VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
 types[2].propertyFlags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
 VkFlags both=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
 assert(getMemoryTypeIndex(15,both)==2);
 assert(getMemoryTypeIndex(3,both)==UINT32_MAX);
 assert(getMemoryTypeIndex(0,0)==UINT32_MAX);
 assert(getMemoryTypeIndex(8,0)==3);
 features=VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT;
 assert(!isExternalMemoryHandleTypeSupported(NULL,VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT));
 assert(isExternalMemoryHandleTypeSupported(NULL,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT));
 features=VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
 assert(isExternalMemoryHandleTypeSupported(NULL,VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT));
 assert(!isExternalMemoryHandleTypeSupported(NULL,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT));
 compatible=false;assert(!isExternalMemoryHandleTypeSupported(NULL,VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT));
 textureCase(true,512,false,false,false); // warm disk cache on first upload
 textureCase(true,512,false,true,false); // cache hit larger than old buffer
 textureCase(false,512,false,false,false);
 textureCase(true,0,false,false,false); // disabled cache performs no hash or read
 textureCase(true,512,true,true,false); // allocation failure must not decode or submit
 textureCase(true,512,false,false,true); // mip must not read a full-size cache entry
 puts("PASS: 9 memory/capability assertions and 6 texture upload scenarios");
}
