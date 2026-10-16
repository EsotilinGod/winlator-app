"""Verify the shipped DEX retains Commons Compress's reflective ZIP constructors."""
import pathlib, re, subprocess, sys, tempfile, zipfile
apk, dexdump = sys.argv[1:3]
registered = [
    'AsiExtraField', 'X5455_ExtendedTimestamp', 'X7875_NewUnix', 'JarMarker',
    'UnicodePathExtraField', 'UnicodeCommentExtraField',
    'Zip64ExtendedInformationExtraField', 'X000A_NTFS',
    'X0014_X509Certificates', 'X0015_CertificateIdForFile',
    'X0016_CertificateIdForCentralDirectory', 'X0017_StrongEncryptionHeader',
    'X0019_EncryptionRecipientCertificateList', 'ResourceAlignmentExtraField',
]
with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as archive:
    dumps = []
    for name in archive.namelist():
        if re.fullmatch(r'classes\d*\.dex', name):
            path = pathlib.Path(tmp) / name
            path.write_bytes(archive.read(name))
            dumps.append(subprocess.check_output([dexdump, str(path)], text=True))
    blocks = re.split(r"(?m)^Class #\d+.*$", '\n'.join(dumps))
    for name in registered:
        descriptor = 'Lorg/apache/commons/compress/archivers/zip/' + name + ';'
        matching = [b for b in blocks if re.search(
            r"Class descriptor\s*:\s*'" + re.escape(descriptor) + "'", b)]
        assert len(matching) == 1, f'Missing or duplicate class: {name}'
        block = matching[0]
        flags = re.search(r'Access flags\s*:\s*(.*)', block).group(1)
        assert 'ABSTRACT' not in flags and 'INTERFACE' not in flags, f'Not concrete: {name}'
        constructor = re.search(
            r"name\s*:\s*'<init>'\s+type\s*:\s*'\(\)V'\s+access\s*:\s*([^\n]+)",
            block)
        assert constructor and 'PUBLIC' in constructor.group(1), f'Missing public no-arg constructor: {name}'
print(f'PASS: {len(registered)} reflective ZIP classes and constructors retained in APK DEX')
