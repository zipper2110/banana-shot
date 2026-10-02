package org.litvin

object AppInfo {
    const val NAME = "BananaShot"
    const val TAGLINE = "Tennis Video Editor"

    val version: String by lazy {
        System.getProperty("bananashot.version")?.takeIf { it.isNotBlank() }
            ?: AppInfo::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
            ?: "development"
    }

    val displayName: String
        get() = if (version == "development") NAME else "$NAME $version"
}
