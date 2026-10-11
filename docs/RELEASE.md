# Releasing SafeCopy

## 1. Create your upload key (once)

Google Play signs the app for users; you sign uploads with your own **upload key**. Create it outside the repo and back it up somewhere safe (password manager, encrypted drive). If you lose it you'll need to ask Google to reset it.

```bash
mkdir -p ~/.android-keys
keytool -genkeypair -v \
  -keystore ~/.android-keys/safecopy-upload.jks \
  -alias safecopy-upload -keyalg RSA -keysize 4096 -validity 10000
```

Then add to `~/.gradle/gradle.properties` (not the project's `gradle.properties`, which is committed):

```properties
SAFECOPY_KEYSTORE=~/.android-keys/safecopy-upload.jks
SAFECOPY_KEYSTORE_PASSWORD=...
SAFECOPY_KEY_ALIAS=safecopy-upload
SAFECOPY_KEY_PASSWORD=...
```

The same four names also work as environment variables (for CI). Without them, release builds come out unsigned.

## 2. Build

```bash
mise exec -- ./gradlew clean testDebugUnitTest lint bundleRelease assembleRelease
scripts/check-permissions.sh app/build/outputs/apk/release/app-release.apk
```

- `bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, the file to upload to Play. Play splits it per CPU type, so users download about 20 MB, not the 66 MB universal APK.
- `check<Variant>Permissions` runs automatically before every `assemble*` and `bundle*` and fails the build if a network, camera or storage permission appears.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` for each upload.

## 3. Before each release

1. All JVM tests green: `./gradlew testDebugUnitTest`.
2. All instrumented tests green on an emulator or phone: `./gradlew connectedDebugAndroidTest` (includes `FixtureDetectionTest`).
3. Manual test matrix (spec 15.5), on API 26, the latest API, and a real mid/low-end phone: gallery, camera, share-in from WhatsApp, rotate, export JPEG, save to device, dark mode, font scale 1.3, TalkBack labels.
4. Release APK permission check passes (above).

## 4. Play Console

- **New personal developer accounts** must run a closed test before production access (it was 12 testers for 14 continuous days at the time of the spec; check the current rule in Play Console).
- **Data safety form**: "No data collected", "No data shared". This is true only while the privacy invariants in spec section 2 hold.
- **Privacy policy**: host `docs/PRIVACY_POLICY.md` somewhere public (e.g. GitHub Pages) and link it.
- **Store listing**: text in `docs/STORE_LISTING.md`. Screenshots in `docs/store-screenshots/` are made from the synthetic fixtures only. No government logos and no wording that implies UIDAI affiliation.
- **Target API**: `targetSdk` must meet Play's current requirement (it's 37 now).
