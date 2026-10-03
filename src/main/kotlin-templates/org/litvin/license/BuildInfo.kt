package org.litvin.license

// The Maven phase generate-sources makes BuildInfo.kt from this template
// (templating-maven-plugin). Do not put these values in a resource file, a
// system property, or the jpackage .cfg file (see build-expiry-spec.md).
object BuildInfo {
    /** The build date (UTC), yyyy-MM-dd. */
    const val BUILD_DATE: String = "${build.date}"

    /** The project version from pom.xml. */
    const val VERSION: String = "${revision}"
}
