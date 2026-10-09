package org.litvin

import org.litvin.license.BuildInfo

object AppInfo {
    const val NAME = "BananaShot"
    const val TAGLINE = "Tennis & Padel Video Editor"

    /** Only from BuildInfo. A system property, the .cfg file, or the JAR manifest must not change it. */
    const val version: String = BuildInfo.VERSION

    const val displayName: String = "$NAME $version"
}
