#!/usr/bin/env python3
"""Exercise exact functions extracted from production C with mocked dependencies."""
from pathlib import Path
import subprocess,sys,tempfile
root=Path(sys.argv[1]).resolve() # app/src/main/cpp/vortekrenderer
here=Path(__file__).resolve().parent

def function(file, signature):
    text=file.read_text()
    start=text.index(signature); brace=text.index('{',start); level=1; end=brace+1
    while level:
        if text[end]=='{': level+=1
        elif text[end]=='}': level-=1
        end+=1
    return text[start:end]

production=function(root/'src/vulkan_helper.c', 'VkExtensionProperties* getExposedDeviceExtensionProperties(')+'\n'+function(root/'src/request_handler.c', 'void vt_handle_vkEnumerateDeviceExtensionProperties(')
with tempfile.TemporaryDirectory() as temp:
    temp=Path(temp); test=temp/'test.c'; binary=temp/'test'
    test.write_text((here/'harness.c').read_text().replace('/* PRODUCTION_FUNCTIONS */',production))
    subprocess.run(['cc','-std=c11','-Wall','-Wextra','-Werror','-Wno-sign-compare','-fsanitize=undefined','-fno-sanitize-recover=all','-g','-I'+str(root/'include'),str(test),'-o',str(binary)],check=True)
    subprocess.run([str(binary)],check=True)
