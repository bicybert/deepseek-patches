# bicybert patches

Patches for DeepSeek (com.deepseek.chat), built for [Morphe](https://morphe.software).

## What these patches do

| Patch | Description | Options |
|----------|----------------|-----------|
| Remove PairIP license checks | Neuters PairIP's license check, which contacts Google Play on every start and shuts the app down when it was not installed by Play. Needed for a re-signed copy to run at all. On by default. | |
| Change package name | Installs alongside stock DeepSeek under its own package name (`org.ungoogled.deepseek`). Moves the app's own provider authorities, permission and task affinities with it, and adds an activity-alias so WeChat login keeps working on the renamed app. On by default. | • Package name |
| Add microG-RE support | Builds microG DeepSeek, a separate app that signs in to your Google account through microG-RE (MicroG-RE or ReVanced GmsCore). Pointed at stock DeepSeek's package and signing certificate, so Google hands out the tokens it issues to DeepSeek. Not for root installs. Off by default. | |

## How to use these patches

Add this repo to Morphe Manager, select DeepSeek and follow the instructions shown.
You will need the app as an XAPK (an App Bundle from APKPure or the like -- `base.apk`
plus its ABI and density splits).

## Patches

<!-- PATCHES_START EXPANDED -->
<!-- PATCHES_END -->

## Notes

- DeepSeek does not use push notifications (no Firebase/FCM), so there is nothing to
  route to microG-RE there; Google Sign-In is the app's one Play services dependency.
- In-app purchases (Play Billing) bind to the Play Store directly and are not patched;
  they work only where the Play Store is installed and refuse a re-signed app's license.
- Patched against DeepSeek 2.8.0 (versionCode 298). The Play services client library
  inside the app is obfuscated and R8 renames it on every build, so patches pin the version.

## Building locally

No GitHub token needed: the Morphe patcher and this repo's Gradle plugin resolve from
`mavenLocal()` first. To rebuild them locally:

```
git clone https://github.com/MorpheApp/morphe-patcher.git   # tag v2.0.0
git clone https://github.com/MorpheApp/morphe-patches-gradle-plugin.git  # tag v2.0.0
# in each: ./gradlew publishToMavenLocal   (signing is disabled in the local build files)
./gradlew buildAndroid
# bundle: patches/build/libs/patches-*.mpp
```

## License

bicybert patches are licensed under the [GNU General Public License v3.0](LICENSE).
The microG-RE name-routing helper is adapted from
[bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) (GPLv3).
