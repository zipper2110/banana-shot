package org.litvin

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Counts the hardware GPUs that DXGI enumerates (B-35).
 *
 * Windows applies the GPU preference to the DXGI adapter list. Thus the DXGI count is the number
 * of GPUs that the preference can choose from. The count does not include software adapters
 * such as "Microsoft Basic Render Driver".
 */
object WindowsGpuCount {
    private val logger = KotlinLogging.logger {}

    private const val IID_IDXGI_FACTORY1 = "{770aae78-f26f-4dba-a829-253c83d1b387}"
    private const val DXGI_ERROR_NOT_FOUND = 0x887A0002.toInt()
    private const val DXGI_ADAPTER_FLAG_SOFTWARE = 2

    // The vtable indexes come from dxgi.h. IUnknown has 3 methods and IDXGIObject has 4 methods.
    private const val RELEASE = 2
    private const val FACTORY1_ENUM_ADAPTERS1 = 12
    private const val ADAPTER1_GET_DESC1 = 10

    // DXGI_ADAPTER_DESC1: WCHAR Description[128], 4 UINT values, 3 SIZE_T values, LUID, UINT Flags.
    private val DESC1_FLAGS_OFFSET = 128L * 2 + 4 * 4 + 3 * Native.SIZE_T_SIZE + 8
    private const val DESC1_BUFFER_SIZE = 512L

    /** Returns the number of hardware GPUs, or null if the count is not available. */
    fun count(): Int? {
        if (!Platform.isWindows()) return null
        return try {
            countHardwareAdapters().also { logger.info { "DXGI found $it hardware GPU(s)." } }
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to count the GPUs." }
            null
        }
    }

    private fun countHardwareAdapters(): Int? {
        val create = NativeLibrary.getInstance("dxgi").getFunction("CreateDXGIFactory1", Function.ALT_CONVENTION)
        val factoryRef = PointerByReference()
        val created = create.invokeInt(arrayOf(Guid.IID(IID_IDXGI_FACTORY1), factoryRef))
        if (created < 0) {
            logger.warn { "CreateDXGIFactory1 failed: 0x${Integer.toHexString(created)}." }
            return null
        }
        val factory = factoryRef.value
        try {
            var count = 0
            var index = 0
            while (true) {
                val adapterRef = PointerByReference()
                val enumerated = invoke(factory, FACTORY1_ENUM_ADAPTERS1, index, adapterRef)
                if (enumerated == DXGI_ERROR_NOT_FOUND) return count
                if (enumerated < 0) {
                    logger.warn { "EnumAdapters1 failed: 0x${Integer.toHexString(enumerated)}." }
                    return null
                }
                val adapter = adapterRef.value
                try {
                    val desc = Memory(DESC1_BUFFER_SIZE)
                    desc.clear()
                    if (invoke(adapter, ADAPTER1_GET_DESC1, desc) < 0) return null
                    if (desc.getInt(DESC1_FLAGS_OFFSET) and DXGI_ADAPTER_FLAG_SOFTWARE == 0) count++
                } finally {
                    invoke(adapter, RELEASE)
                }
                index++
            }
        } finally {
            invoke(factory, RELEASE)
        }
    }

    /** Calls the COM method at [index] in the vtable of [target]. */
    private fun invoke(target: Pointer, index: Int, vararg args: Any): Int {
        val method = target.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(target, *args))
    }
}
