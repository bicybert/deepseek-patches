package app.bicybert.patches.deepseek

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.bicybert.patches.shared.Constants.COMPATIBILITY_DEEPSEEK
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val STOCK_PACKAGE = "com.deepseek.chat"
private const val UNGOOGLED_PACKAGE = "org.ungoogled.deepseek"
/** microG DeepSeek's own, so it installs beside Ungoogled DeepSeek. */
private const val MICROG_PACKAGE = "org.ungoogled.deepseek.microg"
/** WeChat's callback activity, which the WeChat app starts under `<package>.wxapi.WXEntryActivity`. */
private const val WX_ENTRY_ACTIVITY = "com.deepseek.chat.wxapi.WXEntryActivity"

/** `old` itself, or `old` followed by one of [separators], moved under `new`; anything else is not ours. */
private fun moved(value: String, old: String, new: String, separators: String = "."): String? = when {
    value == old -> new
    separators.any { value.startsWith(old + it) } -> new + value.substring(old.length)
    else -> null
}

private fun Document.elements(tag: String): List<Element> =
    getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

/**
 * Installs DeepSeek under a package name of your choosing, alongside stock DeepSeek.
 *
 * Everything that makes up the app's OWN identity moves with the package, so the renamed app
 * stays consistent with itself:
 *
 * - the package;
 * - its five provider authorities (AppFileProvider's, the cn.fly ID SDK's, the Bytedance APM
 *   monitor's, androidx-startup's and jlatexmath's -- authorities are unique on a phone, so a
 *   second copy under the stock names would refuse to install next to the first);
 * - its one custom permission (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, which AndroidX uses to
 *   keep the app's own broadcast receivers private -- left behind, the renamed app would no
 *   longer hold the permission guarding its own receivers);
 * - the two task affinities.
 *
 * Deliberately left alone: component class names (real classes, which do not move), the
 * <queries> entries (they name OTHER apps), the intent-filter actions and the deep-link scheme
 * (`com.deepseek.chat://`, which the code matches by literal), the extra keys and action strings
 * in the code (both sides agree on them), and WeChat's SDK callbacks, which reach the app by
 * class path. The one exception is WeChat's entry activity: the WeChat app starts
 * `<package>.wxapi.WXEntryActivity` BY NAME, so an activity-alias is added under the new
 * package's name pointing at the real class, and WeChat login keeps working on the renamed app.
 *
 * On by default: stock DeepSeek is signed by DeepSeek, so a patched copy under the stock package
 * name cannot update it and can only sit beside it. The default name mirrors stock DeepSeek's
 * com.deepseek.chat, and is the same one Add microG-RE support builds under, with `.microg` added.
 */
@Suppress("unused")
val changePackageNamePatch = resourcePatch(
    name = "Change package name",
    description = "Installs alongside stock DeepSeek under its own package name. On by default: a re-signed copy cannot replace the stock app.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_DEEPSEEK)

    val packageName = stringOption(
        key = "packageName",
        default = UNGOOGLED_PACKAGE,
        title = "Package name",
        description = "The package name to install under. Must be a valid Android package name. Left at " +
            "$UNGOOGLED_PACKAGE, Add microG-RE support installs under $MICROG_PACKAGE instead.",
        required = true,
    ) {
        it != null && it.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))
    }

    execute {
        val old = STOCK_PACKAGE
        val microg = MicrogSelection.builds(this, "Change package name")
        val new = packageName.value!!.let { if (microg && it == UNGOOGLED_PACKAGE) MICROG_PACKAGE else it }
        val counts = linkedMapOf<String, Int>()
        fun count(what: String) { counts[what] = (counts[what] ?: 0) + 1 }

        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            if (root.getAttribute("package") != old) throw PatchException("not DeepSeek's manifest")
            root.setAttribute("package", new)
            count("package")

            // The app's own permission: its declaration, the uses-permission, and any components it guards.
            val ownPermission = "$old.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            val declared = manifest.elements("permission").map { it.getAttribute("android:name") }.toSet()
            if (declared != setOf(ownPermission)) throw PatchException("DeepSeek's own permissions changed: $declared")
            for (element in manifest.elements("*")) {
                val attributes = when (element.tagName) {
                    "permission", "uses-permission" -> listOf("android:name")
                    else -> listOf("android:permission", "android:readPermission", "android:writePermission")
                }
                for (attribute in attributes) {
                    if (moved(element.getAttribute(attribute), old, new) != null) {
                        element.setAttribute(attribute, new + element.getAttribute(attribute).substring(old.length))
                        count("permission")
                    }
                }
                // A task affinity naming DeepSeek's package would put the renamed app's screens into
                // stock DeepSeek's tasks.
                if (element.getAttribute("android:taskAffinity").let { moved(it, old, new) != null }) {
                    element.setAttribute("android:taskAffinity", new + element.getAttribute("android:taskAffinity").substring(old.length))
                    count("task affinity")
                }
            }

            // Every provider of the app's own: authorities are unique on a phone.
            val application = manifest.elements("application").single()
            for (provider in manifest.elements("provider").filter { it.parentNode == application }) {
                val list = provider.getAttribute("android:authorities").split(';')
                provider.setAttribute("android:authorities", list.joinToString(";") {
                    val to = moved(it, old, new)
                    if (to != null) { count("authority"); to } else it
                })
            }

            // WeChat starts its callback activity by name under the app's own package; the real class
            // does not move, so an alias under the new name points at it.
            val wxEntry = manifest.elements("activity").singleOrNull { it.getAttribute("android:name") == WX_ENTRY_ACTIVITY }
                ?: throw PatchException("WeChat's WXEntryActivity not found in the manifest")
            application.appendChild(manifest.createElement("activity-alias").apply {
                setAttribute("android:name", "$new.wxapi.WXEntryActivity")
                setAttribute("android:targetActivity", WX_ENTRY_ACTIVITY)
                setAttribute("android:exported", wxEntry.getAttribute("android:exported").ifEmpty { "true" })
            })
            count("WeChat alias")
        }

        // Measured against DeepSeek 2.8.0 and 2.1.6; anything less means the manifest changed
        // shape. (2.1.6 has one task affinity, 2.8.0 two.)
        val expected = mapOf(
            "package" to 1, "permission" to 2, "task affinity" to 1, "authority" to 5, "WeChat alias" to 1,
        )
        val short = expected.filter { (k, v) -> (counts[k] ?: 0) < v }
        if (short.isNotEmpty()) {
            throw PatchException("package rename found less than expected: " + short.keys.joinToString { "$it ${counts[it] ?: 0}/${expected[it]}" })
        }
    }
}
