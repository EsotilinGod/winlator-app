# Update compatibility: confirmed signing mismatch
The shipped APKs use com.winlator and increasing versionCode 38, 39, 40,
but their signer certificate SHA-256 fingerprints differ:
- experimental 9: 943b216740cc918bb5006e702901e6ff952e3ebe4ce58c70019e62d9c40ddf9a
- experimental 10: fe73743e9fdae38406f05a9f5e77e7efab318851385b421021b0aac37c7b23df
- experimental 11: bf9bed79a7c3795ca94bc394441015e04bf3d9d0f94c4e176df19b75ddd33566

Audit: https://github.com/EsotilinGod/winlator-app/actions/runs/38984180346

Restoring a keystore in the default home location did not ensure Gradle used it.
The workflow now restores the permanent secret to an explicit RUNNER_TEMP path.
Gradle selects that exact path and fails in CI if it is missing. Before upload,
compare the APK signer certificate against the public certificate exported from
that key. Private signing material is never uploaded.

Previously shipped APKs cannot accept a differently signed update unless the
old signing key is available for signing or authorized signing-key rotation.
Do not recommend uninstalling without backing up container data first.
