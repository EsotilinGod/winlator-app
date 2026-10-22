#!/usr/bin/env python3
"""Compile the real SYNC extension against minimal Android/X server doubles."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
test = Path(__file__).parent
stubs = {
    'android/util/SparseBooleanArray.java': '''package android.util;
import java.util.*;
public class SparseBooleanArray {
    private final Map<Integer, Boolean> values = new HashMap<>();
    public int indexOfKey(int id) { return values.containsKey(id) ? 0 : -1; }
    public boolean get(int id) { return values.getOrDefault(id, false); }
    public void put(int id, boolean value) { values.put(id, value); }
    public void delete(int id) { values.remove(id); }
}''',
    'com/winlator/xserver/XServer.java': 'package com.winlator.xserver; public class XServer {}',
    'com/winlator/xserver/XClient.java': '''package com.winlator.xserver;
public class XClient { public int getRemainingRequestLength() { return 4; }
public int getRequestData() { return 0; } }''',
    'com/winlator/xconnector/XInputStream.java': '''package com.winlator.xconnector;
public class XInputStream {
    private final int id;
    public XInputStream(int id) { this.id=id; }
    public void skip(int amount) {}
    public int readInt() { return id; }
    public byte readByte() { return 0; }
}''',
    'com/winlator/xconnector/XOutputStream.java': 'package com.winlator.xconnector; public class XOutputStream {}',
    'com/winlator/xserver/extensions/Extension.java': '''package com.winlator.xserver.extensions;
import com.winlator.xserver.*; import com.winlator.xconnector.*;
import com.winlator.xserver.errors.*; import java.io.IOException;
public abstract class Extension {
    protected Extension(XServer s, byte opcode) {}
    public abstract String getName();
    public byte getErrorCount() { return 0; }
    public abstract void handleRequest(XClient c, XInputStream i, XOutputStream o) throws IOException, XRequestError;
}''',
    'com/winlator/xserver/errors/XRequestError.java': 'package com.winlator.xserver.errors; public class XRequestError extends Exception {}',
}
for name in ('BadFence', 'BadIdChoice', 'BadImplementation', 'BadMatch'):
    argument = 'int id' if name in ('BadFence', 'BadIdChoice') else ''
    stubs[f'com/winlator/xserver/errors/{name}.java'] = (
        f'package com.winlator.xserver.errors; public class {name} extends XRequestError '
        f'{{ public {name}({argument}) {{}} }}')

with tempfile.TemporaryDirectory() as directory:
    path = Path(directory)
    files = []
    for name, contents in stubs.items():
        file = path / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(contents)
        files.append(str(file))
    classes = path / 'classes'
    classes.mkdir()
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(classes),
                    *files,
                    str(root / 'app/src/main/java/com/winlator/xserver/extensions/SyncExtension.java'),
                    str(test / 'SyncFenceTest.java')], check=True)
    subprocess.run(['java', '-cp', str(classes), 'SyncFenceTest'], check=True)
