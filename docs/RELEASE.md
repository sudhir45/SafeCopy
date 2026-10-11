# Releasing SafeCopy

Releases are signed APKs attached to a GitHub release.

## Signing

The build reads the key from four Gradle properties, set in `~/.gradle/gradle.properties` or as environment variables:

```properties
SAFECOPY_KEYSTORE=/path/to/keystore.jks
SAFECOPY_KEYSTORE_PASSWORD=...
SAFECOPY_KEY_ALIAS=...
SAFECOPY_KEY_PASSWORD=...
```

Without them, release builds come out unsigned. Keep the keystore out of the repo and backed up. Android only installs an update over an existing copy if both are signed with the same key.

Published APKs use the certificate with SHA-256 `63:F9:2C:DE:62:E2:20:83:19:25:B7:FC:0E:AA:39:27:4B:3D:81:32:80:C5:FB:A8:93:D3:83:53:FE:4A:B6:5A`.

## Steps

1. Raise `versionCode` by one and set `versionName` in `app/build.gradle.kts`.
2. Build and check:
   ```bash
   ./gradlew clean testDebugUnitTest lint assembleRelease
   scripts/check-permissions.sh app/build/outputs/apk/release/app-release.apk
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
   Run `./gradlew connectedDebugAndroidTest` on an emulator or phone, and install the release APK once by hand. R8 shrinking has broken ML Kit in release builds before, and only a release install shows it.
3. Publish:
   ```bash
   cp app/build/outputs/apk/release/app-release.apk SafeCopy-<version>.apk
   sha256sum SafeCopy-<version>.apk > SafeCopy-<version>.apk.sha256
   gh release create v<version> SafeCopy-<version>.apk SafeCopy-<version>.apk.sha256 --title "SafeCopy <version>"
   ```
