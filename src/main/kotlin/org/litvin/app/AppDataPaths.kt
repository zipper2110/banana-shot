package org.litvin.app

import org.litvin.ApplicationLayout
import java.io.File

data class AppDataPaths(val root: File) {
    val projects: File = root.resolve("projects")
    val completedRenders: File = root.resolve("completed-renders.json")
    val renderQueue: File = root.resolve("render-queue.json")
    val renderQueueLock: File = root.resolve("render-queue.lock")
    val logs: File = root.resolve("logs")
    val temporary: File = root.resolve("tmp")
    val instanceLock: File = root.resolve("instance.lock")

    companion object {
        fun production(): AppDataPaths = AppDataPaths(ApplicationLayout.current().appDataDirectory)
    }
}
