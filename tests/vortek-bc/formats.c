#include <assert.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include "bc_layout.h"
#define ARRAY_SIZE(a) (sizeof(a)/sizeof((a)[0]))
#define VK_FORMAT_BC_FLAG(f) (1u << ((f) - VK_FORMAT_BC1_RGB_UNORM_BLOCK))
typedef struct { uint32_t deviceSupportedFormats; } TextureDecoder;
static VkFormat lastQuery;
static void query(VkPhysicalDevice device,VkFormat f,VkFormatProperties* props) {
    (void)device;lastQuery=f;
    *props=(VkFormatProperties){0};
    if(f==VK_FORMAT_BC1_RGB_UNORM_BLOCK)props->optimalTilingFeatures=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
    else if(f==VK_FORMAT_BC4_UNORM_BLOCK)props->optimalTilingFeatures=VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
    else if(f==VK_FORMAT_B8G8R8A8_SRGB||f==VK_FORMAT_R8G8B8A8_SNORM)
        props->optimalTilingFeatures=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
}
static struct { PFN_vkGetPhysicalDeviceFormatProperties vkGetPhysicalDeviceFormatProperties; } vulkanWrapper={query};
static bool isFormatScaled(VkFormat f){(void)f;return false;}
/* PRODUCTION */
int main(void) {
    TextureDecoder decoder={getDeviceSupportedFormats(VK_NULL_HANDLE)};
    assert(decoder.deviceSupportedFormats==VK_FORMAT_BC_FLAG(VK_FORMAT_BC1_RGB_UNORM_BLOCK));
    assert(!TextureDecoder_isFormatSupported(&decoder,VK_FORMAT_BC1_RGB_UNORM_BLOCK));
    assert(TextureDecoder_isFormatSupported(&decoder,VK_FORMAT_BC4_UNORM_BLOCK));
    assert(!TextureDecoder_isFormatSupported(&decoder,VK_FORMAT_ASTC_4x4_UNORM_BLOCK));
    assert(!TextureDecoder_isFormatSupported(&decoder,VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK));
    assert(!TextureDecoder_isFormatSupported(&decoder,VK_FORMAT_BC7_UNORM_BLOCK));
    VkFormatProperties props={0};lastQuery=VK_FORMAT_UNDEFINED;
    checkFormatProperties(VK_NULL_HANDLE,VK_FORMAT_BC1_RGB_SRGB_BLOCK,&props);
    assert(lastQuery==VK_FORMAT_B8G8R8A8_SRGB&&props.optimalTilingFeatures);
    props=(VkFormatProperties){0};
    checkFormatProperties(VK_NULL_HANDLE,VK_FORMAT_BC5_SNORM_BLOCK,&props);
    assert(lastQuery==VK_FORMAT_R8G8B8A8_SNORM&&props.optimalTilingFeatures);
    props=(VkFormatProperties){0};lastQuery=VK_FORMAT_UNDEFINED;
    checkFormatProperties(VK_NULL_HANDLE,VK_FORMAT_BC7_UNORM_BLOCK,&props);
    assert(lastQuery==VK_FORMAT_UNDEFINED&&!props.optimalTilingFeatures);
    props.optimalTilingFeatures=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
    checkFormatProperties(VK_NULL_HANDLE,VK_FORMAT_BC1_RGB_UNORM_BLOCK,&props);
    assert(lastQuery==VK_FORMAT_UNDEFINED&&props.optimalTilingFeatures);
    puts("PASS: native partial BC support, unsupported formats and sRGB/SNORM queries");
}
