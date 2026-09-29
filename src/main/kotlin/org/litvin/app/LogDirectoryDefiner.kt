package org.litvin.app

import ch.qos.logback.core.PropertyDefinerBase

/** Gives `logback.xml` the `logs` folder in the app data folder. */
class LogDirectoryDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = AppDataPaths.production().logs.absolutePath
}
