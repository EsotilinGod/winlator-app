#!/usr/bin/env python3
"""JVM logic checks with minimal Android stubs; NOT an APK/Android build."""
import pathlib, subprocess, tempfile, sys
root = pathlib.Path(sys.argv[1]).resolve()
here = pathlib.Path(__file__).resolve().parent
stubs = {
 'android/content/Context.java': 'package android.content; public class Context {}',
 'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
 'com/winlator/core/GPUHelper.java': 'package com.winlator.core; import android.content.Context; public class GPUHelper { public static short getAdrenoModelId(Context c) { throw new UnsupportedOperationException("GPU detection is not tested on JVM"); } }',
 'com/winlator/core/StringUtils.java': 'package com.winlator.core; public class StringUtils { public static String replace(String s, int a, int b, String v) { throw new UnsupportedOperationException("StringUtils is outside these tests"); } }',
}
with tempfile.TemporaryDirectory() as tmp:
    tmp = pathlib.Path(tmp)
    stub_files = []
    for name, code in stubs.items():
        p = tmp / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(code)
        stub_files.append(str(p))
    out = tmp / 'classes'
    out.mkdir()
    sources = [root / 'core/GraphicsCompatibility.java', root / 'core/KeyValueSet.java', root / 'container/GraphicsDrivers.java']
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '8', '-target', '8', '-d', str(out), *stub_files, *map(str, sources), str(here / 'CompatibilityTest.java')], check=True)
    subprocess.run(['java', '-cp', str(out), 'CompatibilityTest'], check=True)
    subprocess.run(['java', str(here / 'ParseSources.java'), *map(str, sorted(root.rglob('*.java')))], check=True)
