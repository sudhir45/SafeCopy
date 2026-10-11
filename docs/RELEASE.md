# Releasing SafeCopy

SafeCopy is released as a signed APK on GitHub Releases. Google Play isn't used (it needs a paid developer account and a closed test); the Play notes at the end are kept in case that changes.

## Signing key

The release key lives outside the repo:

- Keystore: `~/.android-keys/safecopy-upload.jks` (alias `safecopy`)
- Passwords and paths: `~/.gradle/gradle.properties` (`SAFECOPY_KEYSTORE`, `SAFECOPY_KEYSTORE_PASSWORD`, `SAFECOPY_KEY_ALIAS`, `SAFECOPY_KEY_PASSWORD`)
- Certificate SHA-256: `63:F9:2C:DE:62:E2:20:83:19:25:B7:FC:0E:AA:39:27:4B:3D:81:32:80:C5:FB:A8:93:D3:83:53:FE:4A:B6:5A`

**Back up the `.jks` file and the four `SAFECOPY_*` lines together**, in a password manager or encrypted drive. Every future update must be signed with this exact key, or Android will refuse to install it over the old version. If it's lost, users have to uninstall and reinstall.

The same four names also work as environment variables (for CI). Without them, release builds come out unsigned.

## Making a release

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Check everything:
   ```bash
   mise exec -- ./gradlew clean testDebugUnitTest lint assembleRelease
   scripts/check-permissions.sh app/build/outputs/apk/release/app-release.apk
   ~/Android/Sdk/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
   With an emulator or phone attached, also run `./gradlew connectedDebugAndroidTest`, and install the release APK once to smoke-test it. R8 once broke ML Kit only in release builds.
3. Copy the APK to `SafeCopy-<version>.apk`, then publish:
   ```bash
   sha256sum SafeCopy-<version>.apk > SafeCopy-<version>.apk.sha256
   gh release create v<version> SafeCopy-<version>.apk SafeCopy-<version>.apk.sha256 --title "SafeCopy <version>" --notes "..."
   ```

## Google Play (not used, kept for reference)

- Upload `bundleRelease` output (`app-release.aab`); Play splits it per CPU type, so users download about 20 MB instead of the 66 MB universal APK.
- New personal developer accounts must run a closed test (it was 12 testers for 14 days) before production.
- Data safety form: "No data collected", "No data shared". Text for the listing is in `docs/STORE_LISTING.md`.
