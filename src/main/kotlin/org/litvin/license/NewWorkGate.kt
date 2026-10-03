package org.litvin.license

/**
 * The expiry check of the core functions (E7-S2, "Expiry check" in `build-expiry-spec.md`). The function that opens
 * a project and the function that adds a new export ask it. Thus, a new way to open a project or to start an export
 * gets the check with no extra work. `ExpiryController` is the production gate.
 */
fun interface NewWorkGate {
    /** False in expired mode. */
    fun allowsNewWork(): Boolean
}

/** A core function refused because this version has expired. The user interface of expired mode (E8) shows why. */
class ExpiredVersionException : IllegalStateException("This version has expired. Update to the new version to continue.")
