package org.litvin.autodetect

import java.io.File
import kotlin.system.exitProcess

/**
 * A developer tool: adds the clips of a detection result to a project folder, as "Add points" of the Points tab
 * does. Use it on a COPY of a project, to look at an import without the app.
 *
 * Arguments: the project folder, the result file, and optionally "advanced" to make a new score file with advanced
 * scoring mode (so that the import adds the fault clips). More optional arguments, in any order after the result file:
 * "settings=<app data folder>" (the saved detection settings of that folder), "range=<start ms>-<end ms>" (the trim
 * of the video), and "swapped" (player 1 of the detection is player 2 of the project).
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: <project folder> <result.json> [advanced]")
        exitProcess(2)
    }
    val project = File(args[0]).apply { mkdirs() }
    val result = File(args[1])
    if ("advanced" in args && !org.litvin.scoring.ScoreIO.existsForProjectDir(project.absolutePath)) {
        System.setProperty(org.litvin.shared.util.Edition.PROPERTY, "true")
        org.litvin.scoring.ScoreIO.writeForProjectDir(
            project.absolutePath,
            org.litvin.scoring.ScoreV1(scoringMode = org.litvin.scoring.ScoringMode.ADVANCED),
        )
    }
    // The result file must be in the autodetect folder of the project, as after a run in the app.
    val folder = File(project, DetectImport.FOLDER).apply { mkdirs() }
    val copy = if (result.parentFile.absoluteFile == folder.absoluteFile) result else result.copyTo(File(folder, "result-manual.json"), overwrite = true)
    val settings = args.firstOrNull { it.startsWith("settings=") }?.let { DetectSettings.load(File(it.substringAfter('='))) } ?: DetectSettings()
    val range = args.firstOrNull { it.startsWith("range=") }?.substringAfter('=')?.split('-')?.let { it[0].toLong()..it[1].toLong() }
    val plan = DetectImport.apply(project, copy, settings, swapped = "swapped" in args, rangeMs = range)
    println("added ${plan.rallyCount} points, ${plan.faultCount} fault clips; skipped ${plan.skippedCount}")
}
