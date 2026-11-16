package app.bicybert.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * DeepSeek ships as an App Bundle -- `base.apk` plus `config.armeabi_v7a` (its native
     * libraries, Tencent's audio stack) and `config.mdpi` -- so the file users download is
     * an XAPK, not a bare APK. The splits are not optional: the native code lives in the
     * ABI split, not in `base.apk`.
     *
     * Every patch here is pinned to one exact version. The app's own classes are not
     * obfuscated (com.deepseek.chat.*), but the Play services client library inside it is
     * (h75 and friends), and R8 renames those on every build, so a patch derived against
     * one build cannot be assumed to work on anything else. Fingerprints are written to
     * survive that where possible, but the version is still declared explicitly rather
     * than as `null`.
     */
    val COMPATIBILITY_DEEPSEEK = Compatibility(
        name = "DeepSeek",
        packageName = "com.deepseek.chat",
        apkFileType = ApkFileType.XAPK,
        // The launcher icon's blue.
        appIconColor = 0x4D6BFE,
        targets = listOf(
            AppTarget(version = "2.8.0", minSdk = 32),
            // The newest version that still runs on Android 10 and older: 2.1.8 raised the
            // minimum to Android 12L, which many phones -- Huawei's without GMS among them --
            // cannot install.
            AppTarget(version = "2.1.6", minSdk = 29),
        ),
    )
}
