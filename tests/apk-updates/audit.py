"""Read-only audit of released CI artifacts; never reads private signing keys."""
import json, os, pathlib, re, subprocess, tempfile, urllib.request, zipfile

class ArtifactRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None:
            redirected.remove_header('Authorization')
        return redirected

def api(path):
    req = urllib.request.Request('https://api.github.com/repos/EsotilinGod/winlator-app/' + path,
        headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json'})
    return urllib.request.build_opener(ArtifactRedirect()).open(req, timeout=120).read()

sdk = pathlib.Path(os.environ['ANDROID_HOME'])
signer = sorted(sdk.glob('build-tools/*/apksigner'))[-1]
aapt = signer.with_name('aapt')
rows = []
for artifact_id, label in [(12074315540, 'experimental-9'), (12081521853, 'experimental-10'), (12082380350, 'experimental-11')]:
    with tempfile.TemporaryDirectory() as temp:
        temp = pathlib.Path(temp)
        archive = temp / 'artifact.zip'
        archive.write_bytes(api('actions/artifacts/' + str(artifact_id) + '/zip'))
        with zipfile.ZipFile(archive) as z:
            name = next(n for n in z.namelist() if n.endswith('.apk'))
            apk = temp / 'app.apk'
            apk.write_bytes(z.read(name))
        cert = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
        badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
        package = next(line for line in badging.splitlines() if line.startswith('package:'))
        print(cert, flush=True)
        digest = re.search(r'certificate SHA-256 digest: (\S+)', cert).group(1)
        row = dict(build=label, certificate_sha256=digest, package=package)
        rows.append(row)
        print(json.dumps(row), flush=True)
print('SAME_CERTIFICATE=' + str(len({r['certificate_sha256'] for r in rows}) == 1), flush=True)
