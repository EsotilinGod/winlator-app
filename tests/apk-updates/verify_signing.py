"""Fail before publishing if the APK does not use the restored permanent key."""
import hashlib, os, re, subprocess, sys
certificate = subprocess.check_output([
    'keytool', '-exportcert', '-alias', 'androiddebugkey',
    '-keystore', os.environ['WINLATOR_KEYSTORE_PATH'],
    '-storepass', 'android',
])
expected = hashlib.sha256(certificate).hexdigest()
report = subprocess.check_output([sys.argv[2], 'verify', '--verbose', '--print-certs', sys.argv[1]], text=True)
digests = re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', report)
if not digests or any(d.lower() != expected for d in digests):
    raise SystemExit('APK certificate does not match the permanent signing key')
print('PASS: APK uses permanent certificate SHA-256 ' + expected)
