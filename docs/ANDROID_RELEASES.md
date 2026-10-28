# Android release and update signing

GitHub Releases must use one persistent Android signing key. Android will reject an APK as an update if its application ID differs or its signing certificate does not match the installed app.

## Configure repository secrets

In **Settings → Secrets and variables → Actions**, add these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: the existing release keystore encoded as one Base64 string
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias inside the keystore
- `ANDROID_KEY_PASSWORD`: private-key password

Generate the Base64 value locally from the keystore you intend to keep using (do not commit the keystore or passwords). For example:

```sh
base64 -w 0 release-key.jks
```

**Important:** To update an already-installed app, use the exact same signing key that signed that installed app. If the original key is lost or the current APK was signed with a different key, a new key cannot repair in-place updates; the installed app must be replaced once, which may erase app-private vault data. Back up/export vault contents before any uninstall.

## Publish a release

After the secrets are configured, push a version tag such as `v1.0.1`. The workflow runs tests, builds a signed release APK, and attaches it to the GitHub Release. Each run gets an incrementing version code, while the version name comes from the tag.

Do not distribute debug APKs as releases. The existing debug variant has a different application ID (`com.ninjavault.app.debug`) and is for development only.
