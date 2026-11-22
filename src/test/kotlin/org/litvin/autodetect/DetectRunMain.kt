package org.litvin.autodetect

import org.litvin.app.AppDataPaths
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/**
 * A developer tool: runs the detection for a video as the Points tab does, with no window. It uses the lab of
 * `autodetect.json` in the app data folder, and prints each state.
 *
 * Arguments: the project folder (the result copy goes into its `autodetect` folder), the work folder name, the
 * video file, and optionally "fast" for a run with no ball stage.
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        println("usage: <project folder> <work name> <video> [fast]")
        exitProcess(2)
    }
    val root = AppDataPaths.production().root
    val lab = DetectLab.load(root) ?: run {
        println("no usable ${DetectLab.FILE_NAME} in $root")
        exitProcess(1)
    }
    val runner = DetectRunner(lab, root)
    val done = CountDownLatch(1)
    var last = ""
    runner.listen { state ->
        val line = when (state) {
            is DetectState.Running -> "running ${state.stage} ${state.progress ?: ""}"
            is DetectState.Finished -> "finished ${state.resultFile}"
            is DetectState.Failed -> "failed ${state.message} (log ${state.logFile})"
            DetectState.Idle -> "idle"
        }
        if (line != last) println(line)
        last = line
        if (state is DetectState.Finished || state is DetectState.Failed) done.countDown()
    }
    val settings = DetectSettings(ballPass = args.getOrNull(3) != "fast")
    check(runner.start(File(args[0]).apply { mkdirs() }, args[1], File(args[2]), settings))
    done.await()
    exitProcess(if (runner.state is DetectState.Finished) 0 else 1)
}
