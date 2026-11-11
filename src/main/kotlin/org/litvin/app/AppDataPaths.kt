package org.litvin.app

import org.litvin.ApplicationLayout
import java.io.File

data class AppDataPaths(val root: File) {
    val projects: File = root.resolve("projects")
    val completedRenders: File = root.resolve("completed-renders.json")
    val renderQueue: File = root.resolve("render-queue.json")
    val renderQueueLock: File = root.resolve("render-queue.lock")
    val logs: File = root.resolve("logs")
    /** The safe parts of the JVM crash files (B-67). */
    val crashes: File = logs.resolve("crashes")
    /** The marker of the app session (B-67). A crash leaves it open. */
    val sessionMarker: File = root.resolve("last-session.properties")
    val temporary: File = root.resolve("tmp")
    val instanceLock: File = root.resolve("instance.lock")

    companion object {
        fun production(): AppDataPaths = AppDataPaths(ApplicationLayout.current().appDataDirectory)
    }
}
