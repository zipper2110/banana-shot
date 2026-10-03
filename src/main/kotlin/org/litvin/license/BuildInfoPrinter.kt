package org.litvin.license

/**
 * Prints the build information for distribution/windows/Validate-AppImage.ps1.
 * The script reads the lines "buildDate=", "expiryDate=", and "version=".
 * This class has no options.
 */
object BuildInfoPrinter {
    fun lines(): List<String> = listOf(
        "buildDate=${BuildInfo.BUILD_DATE}",
        "expiryDate=${BuildExpiry.expiryDate()}",
        "version=${BuildInfo.VERSION}",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        lines().forEach(::println)
    }
}
