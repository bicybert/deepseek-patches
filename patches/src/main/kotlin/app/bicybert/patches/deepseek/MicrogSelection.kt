package app.bicybert.patches.deepseek

import app.morphe.patcher.patch.PatchException
import java.util.WeakHashMap

/**
 * Whether "Add microG-RE support" is part of the patching run a patch is in.
 *
 * The patches it changes -- the package name -- ask with their own context object: Morphe
 * hands every patch of one run the same bytecode and resource contexts, so a run never sees
 * another run's choice, even in a Manager that patches several apps in one process.
 *
 * Morphe runs patches in name order and "Add microG-RE support" sorts ahead of "Change
 * package name", which asks. Should that ever change, "Change package name" would run
 * before the choice is known; it notes itself here, and the microG patch fails the build
 * when it finds one, instead of shipping microG DeepSeek under the wrong package.
 *
 * Modeled on MicrogSelection in bearinmindcat/morphe-patches (GPLv3).
 */
internal object MicrogSelection {
    private val selected = WeakHashMap<Any, Boolean>()
    private val stock = WeakHashMap<Any, MutableList<String>>()

    /** From the microG patch: this run builds microG DeepSeek. */
    fun select(context: Any) {
        val earlier = stock[context]
        if (!earlier.isNullOrEmpty()) throw PatchException(
            "Add microG-RE support has to run before ${earlier.distinct().joinToString()}, which ran first. " +
                "Turn those off and patch again."
        )
        selected[context] = true
    }

    /** For a patch a microG build does its own way: true when this run builds microG DeepSeek. */
    fun builds(context: Any, patch: String): Boolean {
        if (selected.containsKey(context)) return true
        stock.getOrPut(context) { mutableListOf() } += patch
        return false
    }
}
