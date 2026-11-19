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
> **[v1.0.0](https://github.com/bicybert/deepseek-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 DeepSeek&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**Package name:** `com.deepseek.chat`

**🎯 Supported versions:**

| 2.8.0 | 2.1.6 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add microG-RE support](#add-microg-re-support) | Builds microG DeepSeek, a separate app that signs in to your Google account through microG-RE (MicroG-RE or ReVanced GmsCore). Not for root installs. |  |
| [Change package name](#change-package-name) | Installs alongside stock DeepSeek under its own package name. On by default: a re-signed copy cannot replace the stock app. | • Package name |
| [Remove PairIP license checks](#remove-pairip-license-checks) | Neuters PairIP's license check, which contacts Google Play on every start and shuts the app down when it was not installed by Play. Needed for a re-signed copy to run at all. |  |

</details>

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
## why i made this?
my phone does not support google services nor gmscore apps so i decided to patch the apps i use mostly
## License

bicybert patches are licensed under the [GNU General Public License v3.0](LICENSE).
The microG-RE name-routing helper is adapted from
[bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) (GPLv3).
