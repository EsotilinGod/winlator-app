from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def function(filename, signature):
    source = (root / 'src' / filename).read_text()
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

with tempfile.TemporaryDirectory() as tmp:
    binary = str(Path(tmp) / 'bc-test')
    subprocess.run(['cc', '-std=gnu11', '-O1', '-g', '-Wall', '-Wextra',
                    '-Werror', '-Wno-sign-compare', '-fsanitize=address,undefined',
                    '-fno-sanitize-recover=all', '-fno-omit-frame-pointer',
                    '-I' + str(root / 'include'), str(here / 'test.c'),
                    '-o', binary], check=True)
    subprocess.run([binary], check=True)
    functions = [('texture_decoder.c', 'static bool isCanDecompressFormat('),
                 ('texture_decoder.c', 'static uint32_t getDeviceSupportedFormats('),
                 ('texture_decoder.c', 'bool TextureDecoder_isFormatSupported('),
                 ('vulkan_helper.c', 'void checkFormatProperties(')]
    code = '\n'.join(function(*entry) for entry in functions)
    source = Path(tmp) / 'formats.c'
    source.write_text((here / 'formats.c').read_text().replace('/* PRODUCTION */', code))
    subprocess.run(['cc', '-std=gnu11', '-Wall', '-Wextra', '-Werror',
                    '-Wno-sign-compare', '-fsanitize=undefined',
                    '-fno-sanitize-recover=all', '-I' + str(root / 'include'),
                    str(source), '-o', binary], check=True)
    subprocess.run([binary], check=True)
