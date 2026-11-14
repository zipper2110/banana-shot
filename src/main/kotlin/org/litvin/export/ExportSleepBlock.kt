package org.litvin.export

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Stops the idle sleep of the computer. The display can still turn off. */
interface SleepBlocker {
    /** Stops the idle sleep. Returns false when the system did not accept the request. */
    fun block(): Boolean

    /** Lets the computer go to sleep again. */
    fun unblock()

    companion object {
        /** Does nothing. Tests and unknown systems use it. */
        val NONE: SleepBlocker = object : SleepBlocker {
            override fun block() = false
            override fun unblock() = Unit
        }

        /** The blocker of the current system. */
        fun forPlatform(): SleepBlocker = when {
            Platform.isMac() -> MacSleepBlocker()
            Platform.isWindows() -> WindowsSleepBlocker()
            else -> NONE
        }
    }
}

/**
 * Keeps the computer awake while an export runs, when the setting is on.
 *
 * The render queue tells it when an export starts and ends. More → Settings turns the setting on and off. The change
 * applies also to the export that runs now.
 */
class ExportSleepBlock(
    private val blocker: SleepBlocker,
    enabled: Boolean,
) {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    private val exports = mutableSetOf<String>()
    private var enabled = enabled
    private var blocked = false

    /** True while the computer cannot go to idle sleep because of an export. */
    val isBlocking: Boolean get() = synchronized(this) { blocked }

    fun exportStarted(jobId: String) = synchronized(this) {
        exports += jobId
        update()
    }

    fun exportEnded(jobId: String) = synchronized(this) {
        exports -= jobId
        update()
    }

    fun setEnabled(on: Boolean) = synchronized(this) {
        enabled = on
        update()
    }

    /** Lets the computer sleep again. The app calls it when it closes. */
    fun close() = synchronized(this) {
        exports.clear()
        update()
    }

    private fun update() {
        val wanted = enabled && exports.isNotEmpty()
        if (wanted == blocked) return
        if (wanted) {
            blocked = runCatching { blocker.block() }
                .onFailure { logger.warn(it) { "Cannot stop the sleep of the computer during the export." } }
                .getOrDefault(false)
            if (blocked) logger.info { "The computer stays awake during the export." }
        } else {
            runCatching { blocker.unblock() }
                .onFailure { logger.warn(it) { "Cannot let the computer sleep again after the export." } }
            blocked = false
            logger.info { "The computer can sleep again." }
        }
    }
}

/**
 * Uses a power assertion of IOKit. The type "PreventUserIdleSystemSleep" stops the idle sleep, but lets the display
 * turn off. macOS removes the assertion when the process ends, also after a crash.
 */
internal class MacSleepBlocker : SleepBlocker {
    private interface IOKitPower : Library {
        fun IOPMAssertionCreateWithName(type: CoreFoundation.CFStringRef, level: Int, name: CoreFoundation.CFStringRef, id: IntByReference): Int

        fun IOPMAssertionRelease(id: Int): Int
    }

    private val iokit: IOKitPower by lazy { Native.load("IOKit", IOKitPower::class.java) }
    private var assertionId: Int? = null

    override fun block(): Boolean {
        if (assertionId != null) return true
        val type = CoreFoundation.CFStringRef.createCFString(ASSERTION_TYPE)
        val name = CoreFoundation.CFStringRef.createCFString(ASSERTION_NAME)
        try {
            val id = IntByReference()
            val result = iokit.IOPMAssertionCreateWithName(type, LEVEL_ON, name, id)
            if (result != 0) return false
            assertionId = id.value
            return true
        } finally {
            type.release()
            name.release()
        }
    }

    override fun unblock() {
        val id = assertionId ?: return
        assertionId = null
        iokit.IOPMAssertionRelease(id)
    }

    private companion object {
        const val ASSERTION_TYPE = "PreventUserIdleSystemSleep"
        const val ASSERTION_NAME = "BananaShot video export"
        const val LEVEL_ON = 255
    }
}

/**
 * Uses `SetThreadExecutionState`. The state belongs to the thread that sets it, so one thread sets and clears it.
 * Windows removes the state when the thread or the process ends.
 */
internal class WindowsSleepBlocker : SleepBlocker {
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "export-sleep-block").apply { isDaemon = true }
    }

    override fun block(): Boolean = call(ES_CONTINUOUS or ES_SYSTEM_REQUIRED)

    override fun unblock() {
        call(ES_CONTINUOUS)
    }

    private fun call(flags: Int): Boolean =
        thread.submit<Boolean> { Kernel32.INSTANCE.SetThreadExecutionState(flags) != 0 }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_SYSTEM_REQUIRED = 0x00000001
        const val TIMEOUT_SECONDS = 5L
    }
}
