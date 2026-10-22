#!/usr/bin/env python3
"""Exercise Vortek's real wait/submit functions with pthreads."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/cpp/vortekrenderer/src/vk_context.c').read_text()

def extract(signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        if source[end] == '{': depth += 1
        elif source[end] == '}': depth -= 1
        end += 1
    return source[start:end]

functions = '\n'.join(extract(name) for name in (
    'static ExtraDataRequest* waitForExtraDataRequest(',
    'bool handleExtraDataRequest(',
))
test = (Path(__file__).parent / 'test.c').read_text().replace('/* PRODUCTION_FUNCTIONS */', functions)
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory)
    file = path / 'test.c'
    file.write_text(test)
    binary = path / 'test'
    subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror', '-pthread',
                    '-fsanitize=undefined', '-fno-sanitize-recover=all',
                    str(file), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
