package org.litvin.media.mpv

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Component
import java.awt.Dialog
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs

/**
 * The mpv video window on macOS (B-13).
 *
 * mpv 0.41 has no `wid` option on macOS: the macvk context always makes its own `NSWindow`. Thus the preview
 * does not embed mpv in the canvas as on Windows. mpv makes a borderless window that ignores the mouse
 * (`input-cursor-passthrough`). This class attaches that window as a child window of the Java window and keeps
 * its frame equal to the screen bounds of [canvas]. A child window moves with its parent. The mouse input goes
 * through the mpv window to the canvas below it, so the Swing listeners get it.
 *
 * Call [setMpvWindow], [update], and [close] on the event dispatch thread. AppKit calls run on the main thread
 * of macOS with `dispatch_async_f`. The calls do not wait for the main thread, because the main thread can wait
 * for the event dispatch thread.
 */
internal class MacVideoWindow(private val canvas: Component, private val name: String) {
    private companion object {
        val logger = KotlinLogging.logger {}

        /** `NSWindowAbove` of `NSWindowOrderingMode`. */
        const val WINDOW_ABOVE = 1L

        /** The time between two checks of the frame of the mpv window. */
        const val RECHECK_MS = 1_000
    }

    /** The state that the event dispatch thread wants. The main thread applies the newest one. */
    private data class Target(
        val visible: Boolean,
        val canvas: Rectangle = Rectangle(),
        val window: Rectangle = Rectangle(),
        val title: String = "",
        val windowKey: Int = 0,
    )

    @Volatile private var latest = Target(visible = false)
    private val applyPending = AtomicBoolean(false)

    // Only the main thread reads and writes these fields. The window pointers are retained.
    private var mpvWindow: Pointer? = null
    private var parent: Pointer? = null
    private var parentKey = 0
    private var attached = false

    // The frame that the app set last on the attached window, or null before the first frame.
    private var lastFrame: Objc.NSRect.ByValue? = null
    private var parentMissingLogged = false

    /**
     * macOS can move the mpv window after the app sets its frame, for example when a new mpv window opens after the
     * computer wakes. No Swing event then tells the app. Thus a check each second sets the frame again when it is
     * different from the canvas. The check uses only a few calls, and it sets no frame when the frame is correct.
     */
    private val recheck = Timer(RECHECK_MS) { update() }.apply { isRepeats = true }

    /** Gives the `window-id` of mpv: the `NSWindow` pointer. 0 removes the window. */
    fun setMpvWindow(windowId: Long) {
        MainQueue.async {
            val next = windowId.takeIf { it != 0L }?.let(::Pointer)
            if (next == mpvWindow) return@async
            releaseMpvWindow()
            if (next != null) {
                Objc.send(next, "retain")
                // The window is a part of the Java window. It must not show in the Window menu of the app.
                Objc.sendByte(next, "setExcludedFromWindowsMenu:", 1)
                Objc.sendByte(next, "setIgnoresMouseEvents:", 1)
                mpvWindow = next
                logger.info { "mpv preview $name: macOS video window $next" }
            }
            apply(latest)
        }
        if (windowId != 0L) recheck.start() else recheck.stop()
        update()
    }

    /** Reads the canvas and moves, shows, or hides the mpv window. */
    fun update() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(::update)
            return
        }
        val window = SwingUtilities.getWindowAncestor(canvas)
        latest = if (window != null && window.isShowing && canvas.isShowing && canvas.width > 0 && canvas.height > 0) {
            Target(
                visible = true,
                canvas = Rectangle(canvas.locationOnScreen, canvas.size),
                window = window.bounds,
                title = titleOf(window),
                windowKey = System.identityHashCode(window),
            )
        } else {
            Target(visible = false)
        }
        if (!applyPending.getAndSet(true)) {
            MainQueue.async {
                applyPending.set(false)
                apply(latest)
            }
        }
    }

    /** Removes the mpv window from the Java window. Call it before mpv closes its window. */
    fun close() {
        recheck.stop()
        latest = Target(visible = false)
        MainQueue.async {
            releaseMpvWindow()
            releaseParent()
        }
    }

    // ---- Main thread ----------------------------------------------------------------------------------------

    private fun apply(target: Target) {
        val video = mpvWindow ?: return
        // Without a screen, hide the window. The next update after a screen comes back shows it again.
        val screenHeight = if (target.visible) Objc.mainScreenHeight() else null
        val parentWindow = if (screenHeight != null) parentFor(target, screenHeight) else null
        if (screenHeight == null || parentWindow == null) {
            detach(video)
            Objc.send(video, "orderOut:", null as Pointer?)
            return
        }
        if (!attached) {
            Objc.send(parentWindow, "addChildWindow:ordered:", video, WINDOW_ABOVE)
            val parentNumber = Objc.sendLong(parentWindow, "windowNumber")
            Objc.send(video, "orderWindow:relativeTo:", WINDOW_ABOVE, parentNumber)
            attached = true
        }
        resetMousePassthrough(video)
        val frame = Objc.NSRect.ByValue().apply {
            x = target.canvas.x.toDouble()
            y = screenHeight - (target.canvas.y + target.canvas.height)
            width = target.canvas.width.toDouble()
            height = target.canvas.height.toDouble()
        }
        val current = Objc.frame(video)
        val same = abs(current.x - frame.x) < 0.5 && abs(current.y - frame.y) < 0.5 &&
            abs(current.width - frame.width) < 0.5 && abs(current.height - frame.height) < 0.5
        if (!same) {
            // The frame that the app set last is the same as the new frame: thus macOS moved the window.
            val last = lastFrame
            if (last != null && abs(last.y - frame.y) < 0.5 && abs(last.x - frame.x) < 0.5 && abs(last.height - frame.height) < 0.5) {
                logger.info { "mpv preview $name: macOS moved the video window to y ${current.y}. It goes back to y ${frame.y}." }
            }
            Objc.setFrame(video, frame)
        }
        lastFrame = frame
        // mpv makes its window the key and the main window when it starts. The keyboard focus must stay in Java.
        if (Objc.sendByte(video, "isKeyWindow") != 0.toByte()) Objc.send(parentWindow, "makeKeyWindow")
        if (Objc.sendByte(video, "isMainWindow") != 0.toByte()) Objc.send(parentWindow, "makeMainWindow")
    }

    /**
     * Sends the mouse passthrough of the mpv window to the window server again.
     *
     * The window server can lose the passthrough, for example after a change of the window order. AppKit then keeps
     * `ignoresMouseEvents` true, so a second set to true sends nothing. Without the passthrough, the window server
     * uses the alpha of the window: the mpv window gets the mouse on the comments and the scoreboard, and the canvas
     * gets the mouse only on the video. Then the user cannot drag a comment by its text.
     * A set to false and then to true makes AppKit send the value again.
     */
    private fun resetMousePassthrough(video: Pointer) {
        Objc.sendByte(video, "setIgnoresMouseEvents:", 0)
        Objc.sendByte(video, "setIgnoresMouseEvents:", 1)
    }

    private fun detach(video: Pointer) {
        lastFrame = null
        if (!attached) return
        parent?.let { Objc.send(it, "removeChildWindow:", video) }
        attached = false
    }

    /**
     * Finds the `NSWindow` of the Java window. The JDK has no public API that gives it, and JNA gives 0 on macOS.
     * Thus the search compares the frame and the title of each window of the app with the Java window.
     */
    private fun parentFor(target: Target, screenHeight: Double): Pointer? {
        if (parent != null && parentKey == target.windowKey) return parent
        val video = mpvWindow
        if (video != null) detach(video)
        releaseParent()
        val app = Objc.send(Objc.cls("NSApplication"), "sharedApplication") ?: return null
        val windows = Objc.send(app, "windows") ?: return null
        val count = Objc.sendLong(windows, "count")
        for (index in 0 until count) {
            val candidate = Objc.send(windows, "objectAtIndex:", index) ?: continue
            if (candidate == video) continue
            val frame = Objc.frame(candidate)
            val javaY = screenHeight - (frame.y + frame.height)
            val sameFrame = abs(frame.x - target.window.x) < 1.0 && abs(javaY - target.window.y) < 1.0 &&
                abs(frame.width - target.window.width) < 1.0 && abs(frame.height - target.window.height) < 1.0
            if (sameFrame && Objc.string(Objc.send(candidate, "title")).orEmpty() == target.title) {
                Objc.send(candidate, "retain")
                parent = candidate
                parentKey = target.windowKey
                parentMissingLogged = false
                return candidate
            }
        }
        if (!parentMissingLogged) {
            parentMissingLogged = true
            logger.warn { "mpv preview $name: no macOS window for the Java window '${target.title}' ${target.window}" }
        }
        return null
    }

    private fun releaseMpvWindow() {
        val video = mpvWindow ?: return
        detach(video)
        Objc.send(video, "orderOut:", null as Pointer?)
        Objc.send(video, "release")
        mpvWindow = null
    }

    private fun releaseParent() {
        parent?.let { Objc.send(it, "release") }
        parent = null
        parentKey = 0
    }

    private fun titleOf(window: Window): String = when (window) {
        is Frame -> window.title.orEmpty()
        is Dialog -> window.title.orEmpty()
        else -> ""
    }
}

/**
 * Keeps the mpv window out of the full screen of macOS.
 *
 * The mpv window class (`swift.Window`) sets the collection behavior `fullScreenPrimary`, and mpv shows the window
 * when it starts. When the Java window is in full screen at that time, macOS puts the mpv window in full screen on
 * a new Space and goes to that Space. Then the Java window and its open dialogs are on a Space that the user cannot
 * get to. A change of the behavior after mpv shows the window is too late. Thus this object adds a
 * `setCollectionBehavior:` method to the mpv window class before mpv starts. The method replaces
 * `fullScreenPrimary` with `fullScreenAuxiliary`, so the mpv window stays on the Space of the Java window. As a
 * child window, it goes into full screen with the Java window.
 *
 * The mpv window class also has its own `constrainFrameRect:toScreen:`. It keeps the top of the window under the menu
 * bar. In full screen, the canvas starts at the top of the screen, so macOS moved the video down by the height of the
 * menu bar, over the controls under the video. The mpv window is a child window that must cover the canvas exactly.
 * Thus this object also replaces that method with one that keeps the frame.
 */
internal object MacMpvWindowClass {
    private val logger = KotlinLogging.logger {}

    /** The Objective-C name of `swift.Window` in libmpv. */
    private const val MPV_WINDOW_CLASS = "_TtC5swift6Window"
    private const val SET_BEHAVIOR = "setCollectionBehavior:"
    private const val CONSTRAIN_FRAME = "constrainFrameRect:toScreen:"
    private const val FULL_SCREEN_PRIMARY = 1L shl 7
    private const val FULL_SCREEN_AUXILIARY = 1L shl 8

    fun interface SetBehavior : Callback {
        fun invoke(self: Pointer, selector: Pointer, behavior: Long)
    }

    fun interface ConstrainFrame : Callback {
        fun invoke(self: Pointer, selector: Pointer, frame: MacRect.ByValue, screen: Pointer?): MacRect.ByValue
    }

    private var original: Function? = null

    // The frame of the mpv window comes from the canvas. It must not move under the menu bar.
    private val keepFrame = ConstrainFrame { _, _, frame, _ -> frame }

    // The Objective-C runtime keeps only the function pointer, so this object must stay reachable.
    private val setBehavior = SetBehavior { self, selector, behavior ->
        val changed = (behavior and FULL_SCREEN_PRIMARY.inv()) or FULL_SCREEN_AUXILIARY
        original?.invoke(Void::class.java, arrayOf(self, selector, changed))
    }

    private val installed by lazy {
        try {
            install()
        } catch (t: Throwable) {
            logger.warn(t) { "Cannot change the mpv window class. Full screen can move the video to another Space." }
        }
    }

    /** Adds the method one time. Call it after libmpv loads and before mpv starts. */
    fun ensureInstalled() {
        installed
    }

    private fun install() {
        val mpvWindow = Objc.clsOrNull(MPV_WINDOW_CLASS)
        if (mpvWindow == null) {
            logger.warn { "No mpv window class $MPV_WINDOW_CLASS. Full screen can move the video to another Space." }
            return
        }
        val method = Objc.instanceMethod(Objc.cls("NSWindow"), SET_BEHAVIOR) ?: error("No NSWindow $SET_BEHAVIOR")
        original = Function.getFunction(Objc.implementation(method))
        val added = Objc.addMethod(mpvWindow, SET_BEHAVIOR, setBehavior, Objc.typeEncoding(method))
        logger.info { "mpv window class: $SET_BEHAVIOR ${if (added) "added" else "not added, the class has one"}" }
        val constrain = Objc.instanceMethod(Objc.cls("NSWindow"), CONSTRAIN_FRAME) ?: error("No NSWindow $CONSTRAIN_FRAME")
        Objc.replaceMethod(mpvWindow, CONSTRAIN_FRAME, keepFrame, Objc.typeEncoding(constrain))
        logger.info { "mpv window class: $CONSTRAIN_FRAME replaced" }
    }
}

/** An `NSRect` for the callbacks of [MacMpvWindowClass]. */
@Structure.FieldOrder("x", "y", "width", "height")
internal open class MacRect : Structure() {
    @JvmField var x = 0.0
    @JvmField var y = 0.0
    @JvmField var width = 0.0
    @JvmField var height = 0.0

    class ByValue : MacRect(), Structure.ByValue
}

/** Runs blocks on the main thread of macOS (the AppKit thread) with `dispatch_async_f`. */
private object MainQueue {
    private val logger = KotlinLogging.logger {}

    private interface Dispatch : Library {
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }

    fun interface Work : Callback {
        fun invoke(context: Pointer?)
    }

    private val tasks = ConcurrentHashMap<Long, () -> Unit>()
    private val ids = AtomicLong()
    private val dispatch: Dispatch by lazy { Native.load("System", Dispatch::class.java) }
    private val queue: Pointer by lazy { NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q") }

    // One callback object for all blocks. JNA keeps no reference to a callback, so the object must stay reachable.
    private val work = Work { context ->
        val block = tasks.remove(Pointer.nativeValue(context)) ?: return@Work
        try {
            block()
        } catch (t: Throwable) {
            logger.warn(t) { "A macOS window call failed." }
        }
    }

    fun async(block: () -> Unit) {
        val id = ids.incrementAndGet()
        tasks[id] = block
        dispatch.dispatch_async_f(queue, Pointer(id), work)
    }
}

/** The Objective-C calls of [MacVideoWindow]. Each `objc_msgSend` signature has its own name (see the mapper). */
@Suppress("FunctionName")
private object Objc {
    @Structure.FieldOrder("x", "y", "width", "height")
    open class NSRect : Structure() {
        @JvmField var x = 0.0
        @JvmField var y = 0.0
        @JvmField var width = 0.0
        @JvmField var height = 0.0

        class ByValue : NSRect(), Structure.ByValue
    }

    private interface Runtime : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun class_getInstanceMethod(cls: Pointer, selector: Pointer): Pointer?
        fun class_addMethod(cls: Pointer, selector: Pointer, imp: Callback, types: String): Byte
        fun class_replaceMethod(cls: Pointer, selector: Pointer, imp: Callback, types: String): Pointer?
        fun method_getImplementation(method: Pointer): Pointer
        fun method_getTypeEncoding(method: Pointer): String
        fun msgSend(receiver: Pointer, selector: Pointer): Pointer?
        fun msgSend(receiver: Pointer, selector: Pointer, argument: Pointer?): Pointer?
        fun msgSend(receiver: Pointer, selector: Pointer, argument: Long): Pointer?
        fun msgSend(receiver: Pointer, selector: Pointer, argument: Pointer?, argument2: Long): Pointer?
        fun msgSend(receiver: Pointer, selector: Pointer, argument: Long, argument2: Long): Pointer?
        fun msgSendByte(receiver: Pointer, selector: Pointer): Byte
        fun msgSendByte(receiver: Pointer, selector: Pointer, argument: Byte): Byte
        fun msgSendLong(receiver: Pointer, selector: Pointer): Long
        fun msgSendRect(receiver: Pointer, selector: Pointer): NSRect.ByValue
        fun msgSendRectByte(receiver: Pointer, selector: Pointer, rect: NSRect.ByValue, argument: Byte)
    }

    private val runtime: Runtime by lazy {
        val mapper = FunctionMapper { _, method -> if (method.name.startsWith("msgSend")) "objc_msgSend" else method.name }
        Native.load("objc", Runtime::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to mapper))
    }
    private val selectors = ConcurrentHashMap<String, Pointer>()

    private fun sel(name: String): Pointer = selectors.getOrPut(name) { runtime.sel_registerName(name) }

    fun cls(name: String): Pointer = runtime.objc_getClass(name) ?: error("No Objective-C class $name")
    fun clsOrNull(name: String): Pointer? = runtime.objc_getClass(name)

    fun instanceMethod(cls: Pointer, selector: String): Pointer? = runtime.class_getInstanceMethod(cls, sel(selector))
    fun implementation(method: Pointer): Pointer = runtime.method_getImplementation(method)
    fun typeEncoding(method: Pointer): String = runtime.method_getTypeEncoding(method)
    fun addMethod(cls: Pointer, selector: String, imp: Callback, types: String): Boolean =
        runtime.class_addMethod(cls, sel(selector), imp, types) != 0.toByte()
    fun replaceMethod(cls: Pointer, selector: String, imp: Callback, types: String) {
        runtime.class_replaceMethod(cls, sel(selector), imp, types)
    }

    fun send(receiver: Pointer, selector: String): Pointer? = runtime.msgSend(receiver, sel(selector))
    fun send(receiver: Pointer, selector: String, argument: Pointer?): Pointer? = runtime.msgSend(receiver, sel(selector), argument)
    fun send(receiver: Pointer, selector: String, argument: Long): Pointer? = runtime.msgSend(receiver, sel(selector), argument)
    fun send(receiver: Pointer, selector: String, argument: Pointer?, argument2: Long): Pointer? =
        runtime.msgSend(receiver, sel(selector), argument, argument2)
    fun send(receiver: Pointer, selector: String, argument: Long, argument2: Long): Pointer? =
        runtime.msgSend(receiver, sel(selector), argument, argument2)
    fun sendByte(receiver: Pointer, selector: String): Byte = runtime.msgSendByte(receiver, sel(selector))
    fun sendByte(receiver: Pointer, selector: String, argument: Byte): Byte = runtime.msgSendByte(receiver, sel(selector), argument)
    fun sendLong(receiver: Pointer, selector: String): Long = runtime.msgSendLong(receiver, sel(selector))
    fun frame(window: Pointer): NSRect = runtime.msgSendRect(window, sel("frame"))
    fun setFrame(window: Pointer, frame: NSRect.ByValue) = runtime.msgSendRectByte(window, sel("setFrame:display:"), frame, 1)

    fun string(nsString: Pointer?): String? =
        nsString?.let { send(it, "UTF8String") }?.getString(0, "UTF-8")

    /**
     * The height of the first screen, or null when macOS has no screen. Java places the origin at its top left
     * corner, AppKit at its bottom left corner.
     *
     * The screen list can be empty, for example when the lid closes and no other display is connected. Then
     * `objectAtIndex:` throws an Objective-C exception, and that exception stops the process at once.
     */
    fun mainScreenHeight(): Double? {
        val screens = send(cls("NSScreen"), "screens") ?: return null
        if (sendLong(screens, "count") <= 0L) return null
        val first = send(screens, "objectAtIndex:", 0L) ?: return null
        return frame(first).height
    }
}
