from pathlib import Path
import subprocess,sys,tempfile
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def function(name,signature):
 s=(root/'src'/name).read_text();a=s.index(signature);b=s.index('{',a);depth=1;e=b+1
 while depth:
  depth+=(s[e]=='{')-(s[e]=='}');e+=1
 return s[a:e]
parts=[('vulkan_helper.c','uint32_t getMemoryTypeIndex('),('vulkan_helper.c','static bool isExternalMemoryHandleTypeSupported('),('texture_decoder.c','void TextureDecoder_decodeAll(')]
code='\n'.join(function(*p) for p in parts)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'test.c').write_text((here/'harness.c').read_text().replace('/* PRODUCTION */',code))
 subprocess.run(['cc','-std=gnu11','-Wall','-Wextra','-Werror','-Wno-sign-compare','-Wno-unused-parameter','-fsanitize=undefined','-fno-sanitize-recover=all','-I'+str(root/'include'),str(p/'test.c'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)

parts=[('texture_decoder.c','static void destroyDecodeBuffer('),('texture_decoder.c','static bool createDecodeBuffer('),('xwindow_swapchain.c','static VkResult createImageMemory(')]
code='\n'.join(function(*p) for p in parts)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'test.c').write_text((here/'allocation.c').read_text().replace('/* PRODUCTION */',code))
 subprocess.run(['cc','-std=gnu11','-Wall','-Wextra','-Werror','-Wno-sign-compare','-Wno-unused-parameter','-fsanitize=undefined','-fno-sanitize-recover=all','-I'+str(root/'include'),str(p/'test.c'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)
