package app.bicybert.patches.deepseek

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AvailabilityResolver
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.bicybert.patches.shared.Constants.COMPATIBILITY_DEEPSEEK
import app.bicybert.patches.shared.MicrogRe
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

/*
 * Signing in through microG. The approach follows the GmsCore support patches of
 * ReVanced / Morphe (YouTube) and bearinmindcat/morphe-patches (Gmail, Maps; GPLv3):
 * microG installs under its own package, so every Play services name the app uses is
 * pointed at microG's, and the manifest names the app microG should vouch for -- stock
 * DeepSeek's package and signing certificate -- so the account tokens it hands out are
 * the ones Google issues to DeepSeek.
 *
 * DeepSeek is an easy case: no push (no FCM receivers or c2dm permissions at all -- the
 * app keeps no live connection), no Play services location or Cronet, no Play-services
 * permissions microG renames. What it uses Google for is the account: Google Sign-In
 * (auth.api.signin), through the Play services client library bundled in the app.
 */

private const val STOCK_PACKAGE = "com.deepseek.chat"
/** DeepSeek's signing certificate (SHA-1), which microG reports to Google for this app. */
private const val STOCK_CERT_SHA1 = "ca2378d73740d0010d2c4bce7eef948b32fef14b"
private const val MICROG_PACKAGE = MicrogRe.PACKAGE
private const val VENDOR = MicrogRe.VENDOR

/** microG-RE's name for a Play services name in DeepSeek's code; null if it is none. */
private fun route(value: String): String? = MicrogRe.route(value)

private val microgManifestPatch = resourcePatch(
    description = "Points the manifest at microG: package visibility for it, and whom microG should vouch for.",
) {
    execute {
        // Runs first as the microG patch's dependency. The choice is recorded so
        // Change package name picks the .microg package.
        MicrogSelection.select(this)

        document("AndroidManifest.xml").use { manifest ->
            fun all(tag: String) = manifest.getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

            // 1. Android 11+ package visibility: the app must be allowed to see microG. Its
            //    <queries> already names Play services' own package (left alone: some code
            //    still asks for it, and seeing it harms nothing).
            val root = manifest.documentElement
            val queries = all("queries").firstOrNull { it.parentNode == root }
                ?: manifest.createElement("queries").also { root.insertBefore(it, root.firstChild) }
            queries.appendChild(manifest.createElement("package").apply { setAttribute("android:name", MICROG_PACKAGE) })

            // 2. Whom microG vouches for: stock DeepSeek's package and certificate.
            val application = all("application").single()
            fun meta(name: String, value: String) = application.appendChild(manifest.createElement("meta-data").apply {
                setAttribute("android:name", name)
                setAttribute("android:value", value)
            })
            meta("$MICROG_PACKAGE.SPOOFED_PACKAGE_NAME", STOCK_PACKAGE)
            meta("$MICROG_PACKAGE.SPOOFED_PACKAGE_SIGNATURE", STOCK_CERT_SHA1)
            meta("$VENDOR.MICROG_PACKAGE_NAME", MICROG_PACKAGE)
            meta("$MICROG_PACKAGE.MICROG_PACKAGE_NAME", MICROG_PACKAGE)
        }
    }
}

/** Play services' own availability log line, in the client library's GooglePlayServicesUtil. */
private const val SERVICES_MISSING = " requires Google Play services, but they are missing."

/**
 * Builds microG DeepSeek instead of Ungoogled DeepSeek. Its name sorts it ahead of "Change
 * package name", which sees the choice ([MicrogSelection]) and defaults the package to microG
 * DeepSeek's own.
 */
@Suppress("unused")
val microgSupportPatch = bytecodePatch(
    name = "Add microG-RE support",
    description = "Builds microG DeepSeek, a separate app that signs in to your Google account through microG-RE " +
        "(MicroG-RE or ReVanced GmsCore). Not for root installs.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_DEEPSEEK)
    category("microG-RE")
    // A mount install keeps stock DeepSeek's manifest, so microG never learns whom to vouch for.
    availability(AvailabilityResolver { installer, _ ->
        if (installer == InstallerType.MOUNT) PatchAvailability.UNAVAILABLE else PatchAvailability.DISABLED
    })
    dependsOn(microgManifestPatch)

    execute {
        MicrogSelection.select(this)

        // 1. The Play services client library checks, before it binds anything, that Play
        //    services is installed, signed by Google and new enough (GooglePlayServicesUtil /
        //    GoogleApiAvailability, obfuscated). microG-RE is none of those, so the check is
        //    made to always pass: it now reports CONNECTION_SUCCESS whatever it finds. Every
        //    caller of the availability check funnels through this one method, whose log line
        //    anchors it against R8's renaming.
        val holders = mutableListOf<String>()
        classDefForEach { classDef ->
            if (classDef.methods.any { method ->
                    method.implementation?.instructions?.any { insn ->
                        insn.opcode == Opcode.CONST_STRING &&
                            ((insn as ReferenceInstruction).reference as StringReference).string == SERVICES_MISSING
                    } == true
                }) holders += classDef.type
        }
        val availabilityClass = holders.singleOrNull()
            ?: throw PatchException("expected one Play services availability class, found ${holders.size}")
        val availability = mutableClassDefBy(availabilityClass).methods.singleOrNull { method ->
            // The parameter order flipped between versions: (int, Context) in 2.8.0, (Context, int) in 2.1.6.
            val params = method.parameterTypes.map(CharSequence::toString)
            method.returnType == "I" &&
                (params == listOf("I", "Landroid/content/Context;") || params == listOf("Landroid/content/Context;", "I")) &&
                method.implementation?.instructions?.any { insn ->
                    insn.opcode == Opcode.CONST_STRING &&
                        ((insn as ReferenceInstruction).reference as StringReference).string == SERVICES_MISSING
                } == true
        } ?: throw PatchException("Play services availability check changed shape")
        availability.apply {
            // An instance method taking (int, Context): its three parameters sit at the top of
            // its registers, so v0 is a local as long as the method keeps its own workspace --
            // this one builds log lines, so it always has one.
            if (implementation!!.registerCount < 4) throw PatchException("availability check has no free register")
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }

        // 2. The Play services names in DeepSeek's code: in finalize, below.
    }

    // Every Play services name in the app's code: the package (which the client library asks for
    // by name before it binds -- microG-RE answers Google's own bind actions, but only under its
    // own package), the account type, and the providers and content URIs. In finalize, after every
    // other patch has run, so code they add is covered too.
    finalize {
        val holders = mutableListOf<String>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/bicybert/")) return@classDefForEach
            if (classDef.methods.any { method ->
                    method.implementation?.instructions?.any { insn ->
                        (insn.opcode == Opcode.CONST_STRING || insn.opcode == Opcode.CONST_STRING_JUMBO) &&
                            route(((insn as ReferenceInstruction).reference as StringReference).string) != null
                    } == true
                }) holders += classDef.type
        }
        var rewritten = 0
        for (type in holders) {
            for (method in mutableClassDefBy(type).methods) {
                val instructions = method.implementation?.instructions?.toList() ?: continue
                instructions.forEachIndexed { index, insn ->
                    if (insn.opcode != Opcode.CONST_STRING && insn.opcode != Opcode.CONST_STRING_JUMBO) return@forEachIndexed
                    val value = ((insn as ReferenceInstruction).reference as StringReference).string
                    val to = route(value) ?: return@forEachIndexed
                    val register = (insn as OneRegisterInstruction).registerA
                    val opcode = if (insn.opcode == Opcode.CONST_STRING) "const-string" else "const-string/jumbo"
                    method.replaceInstruction(index, "$opcode v$register, \"$to\"")
                    rewritten++
                }
            }
        }
        // Measured on this DeepSeek version: 20 names -- the client library asks for Play
        // services by name in its bind, availability, auth and dynamite paths. Fewer means
        // it changed shape.
        if (rewritten < 15) throw PatchException("rewrote only $rewritten Play services names; DeepSeek changed shape")
    }
}
