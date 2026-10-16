#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdio.h>
#include "vulkan/vulkan_core.h"
#include "vulkan/vulkan_android.h"
typedef struct AHardwareBuffer AHardwareBuffer;
typedef struct {VkDevice device; VkBuffer buffer; VkDeviceMemory memory; void* decompressedData; uint32_t bufferSize;} TextureDecoder;
static int failure,created,freed,destroyed,mapped,unmapped;
static uint32_t selected=2; static VkDeviceSize mappedSize;
static uint32_t getMemoryTypeIndex(uint32_t bits,VkFlags props){assert(props==VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT || props==(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));return selected;}
static VkResult create(VkDevice d,const VkBufferCreateInfo* i,const VkAllocationCallbacks* a,VkBuffer* b){created++;assert(i->size==100);if(failure==1)return VK_ERROR_OUT_OF_DEVICE_MEMORY;*b=(VkBuffer)(uintptr_t)1;return VK_SUCCESS;}
static void req(VkDevice d,VkBuffer b,VkMemoryRequirements* r){r->size=4096;r->memoryTypeBits=7;}
static VkResult alloc(VkDevice d,const VkMemoryAllocateInfo* i,const VkAllocationCallbacks* a,VkDeviceMemory* m){assert(i->memoryTypeIndex==2);if(failure==2)return VK_ERROR_OUT_OF_DEVICE_MEMORY;*m=(VkDeviceMemory)(uintptr_t)2;return VK_SUCCESS;}
static VkResult bind(VkDevice d,VkBuffer b,VkDeviceMemory m,VkDeviceSize o){return failure==3?VK_ERROR_OUT_OF_DEVICE_MEMORY:VK_SUCCESS;}
static VkResult map(VkDevice d,VkDeviceMemory m,VkDeviceSize o,VkDeviceSize n,VkMemoryMapFlags f,void** p){if(failure==4)return VK_ERROR_MEMORY_MAP_FAILED;mapped++;mappedSize=n;*p=(void*)(uintptr_t)3;return VK_SUCCESS;}
static void unmap(VkDevice d,VkDeviceMemory m){unmapped++;}
static void destroy(VkDevice d,VkBuffer b,const VkAllocationCallbacks* a){destroyed++;}
static void release(VkDevice d,VkDeviceMemory m,const VkAllocationCallbacks* a){freed++;}
static VkResult props(VkDevice d,const AHardwareBuffer* b,VkAndroidHardwareBufferPropertiesANDROID* p){if(failure==5)return VK_ERROR_INVALID_EXTERNAL_HANDLE;p->allocationSize=4096;p->memoryTypeBits=7;return VK_SUCCESS;}
static VkResult bindImage(VkDevice d,VkImage i,VkDeviceMemory m,VkDeviceSize o){return failure==6?VK_ERROR_OUT_OF_DEVICE_MEMORY:VK_SUCCESS;}
static struct {
 PFN_vkCreateBuffer vkCreateBuffer;PFN_vkGetBufferMemoryRequirements vkGetBufferMemoryRequirements;
 PFN_vkAllocateMemory vkAllocateMemory;PFN_vkBindBufferMemory vkBindBufferMemory;PFN_vkMapMemory vkMapMemory;
 PFN_vkUnmapMemory vkUnmapMemory;PFN_vkDestroyBuffer vkDestroyBuffer;PFN_vkFreeMemory vkFreeMemory;
 PFN_vkGetAndroidHardwareBufferPropertiesANDROID vkGetAndroidHardwareBufferPropertiesANDROID;PFN_vkBindImageMemory vkBindImageMemory;
} vulkanWrapper={create,req,alloc,bind,map,unmap,destroy,release,props,bindImage};
/* PRODUCTION */
int main(void){
 TextureDecoder t={0};
 assert(createDecodeBuffer(&t,100));assert(t.bufferSize==100&&mappedSize==4096);
 destroyDecodeBuffer(&t);assert(!t.bufferSize&&!t.decompressedData&&!t.memory&&!t.buffer);
 assert(created==1&&freed==1&&destroyed==1&&unmapped==1);
 for(failure=1;failure<=4;failure++){
  assert(!createDecodeBuffer(&t,100));assert(!t.bufferSize&&!t.decompressedData&&!t.memory&&!t.buffer);
 }
 failure=0;selected=UINT32_MAX;assert(!createDecodeBuffer(&t,100));assert(!t.bufferSize&&!t.buffer);
 selected=2;assert(createDecodeBuffer(&t,100));destroyDecodeBuffer(&t);
 VkDeviceMemory m=VK_NULL_HANDLE;failure=5;int before=freed;
 assert(createImageMemory(NULL,VK_NULL_HANDLE,NULL,&m)==VK_ERROR_INVALID_EXTERNAL_HANDLE);assert(!m&&freed==before);
 failure=6;assert(createImageMemory(NULL,VK_NULL_HANDLE,NULL,&m)==VK_ERROR_OUT_OF_DEVICE_MEMORY);assert(!m&&freed==before+1);
 failure=0;selected=UINT32_MAX;assert(createImageMemory(NULL,VK_NULL_HANDLE,NULL,&m)==VK_ERROR_FEATURE_NOT_PRESENT);assert(!m);
 selected=2;assert(createImageMemory(NULL,VK_NULL_HANDLE,NULL,&m)==VK_SUCCESS);assert(m);release(NULL,m,NULL);
 puts("PASS: buffer capacity, 5 allocation failures, retry, and 4 swapchain import scenarios");
}
