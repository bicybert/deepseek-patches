package app.bicybert.patches.deepseek

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.bicybert.patches.shared.Constants.COMPATIBILITY_DEEPSEEK
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * DeepSeek ships with PairIP (Google Play's app protection): `com.pairip.application.Application`
 * calls `LicenseClient.checkLicense(Context)` in `attachBaseContext`, before anything else in the
 * app runs. The check contacts Google Play (ILicensingService, the same interface old paid apps
 * used) and verifies the app was installed by it -- a re-signed copy fails, and PairIP's answer is
 * `System.exit(0)`: the patched app would shut itself down on every start.
 *
 * `checkLicense` is the only entry point everything else hangs off (the background licensing
 * connection, the retries, the repeated checks, the eventual shutdown, the LicenseActivity
 * paywall), so turning it into a no-op takes all of them out at once. The one other caller,
 * LicenseContentProvider, is not in the manifest and never runs. DeepSeek's build carries no
 * PairIP VM classes, so there is nothing else to neutralize.
 */
@Suppress("unused")
val removePairIPLicenseCheckPatch = bytecodePatch(
    name = "Remove PairIP license checks",
    description = "Neuters PairIP's license check, which contacts Google Play on every start and shuts the app " +
        "down when it was not installed by Play. Needed for a re-signed copy to run at all.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_DEEPSEEK)

    execute {
        val licenseClient = "Lcom/pairip/licensecheck/LicenseClient;"
        mutableClassDefByOrNull(licenseClient)
            ?: throw PatchException("PairIP LicenseClient not found; this DeepSeek build does not use PairIP")
        val check = mutableClassDefBy(licenseClient).methods.singleOrNull { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) && method.name == "checkLicense" &&
                method.returnType == "V" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;")
        } ?: throw PatchException("LicenseClient.checkLicense(Context) changed shape")
        check.addInstructions(0, "return-void")
    }
}
