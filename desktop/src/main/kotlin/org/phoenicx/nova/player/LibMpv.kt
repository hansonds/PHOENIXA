package org.phoenicx.nova.player

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.phoenicx.nova.App
import java.io.File

/** The parts of libmpv (mpv's library, libmpv-2.dll) that PHOENIXA uses. */
@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_command_string(ctx: Pointer, args: String): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_error_string(error: Int): String?

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_free(ctx: Pointer)

    interface UpdateCallback : Callback {
        fun invoke(data: Pointer?)
    }

    companion object {
        // Event ids (client.h)
        const val EVENT_SHUTDOWN = 1
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8

        // End-file reasons
        const val END_EOF = 0
        const val END_ERROR = 4

        // Render params (render.h)
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20
        const val RENDER_UPDATE_FRAME = 1L

        @Volatile private var cached: LibMpv? = null
        @Volatile var loadError: String? = null; private set

        /** libmpv-2.dll is shipped in the "mpv" folder next to PHOENIXA.exe */
        fun dllPath(): File? {
            val names = listOf("libmpv-2.dll", "mpv-2.dll", "mpv-1.dll")
            val dirs = mutableListOf<File>()
            System.getProperty("jpackage.app-path")?.let { dirs += File(it).parentFile.resolve("mpv") }
            System.getProperty("compose.application.resources.dir")?.let { dirs += File(it).resolve("mpv") }
            dirs += File(System.getProperty("user.dir"), "mpv")
            dirs += File(App.prefs.folder, "mpv")
            for (d in dirs) for (n in names) {
                val f = File(d, n)
                if (f.isFile) return f
            }
            return null
        }

        fun get(): LibMpv? {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                System.setProperty("jna.encoding", "UTF-8")
                val dll = dllPath()
                if (dll == null) {
                    loadError = "Video engine (libmpv-2.dll) not found. Please re-download PHOENIXA for Windows."
                    return null
                }
                return try {
                    Native.load(dll.absolutePath, LibMpv::class.java).also { cached = it }
                } catch (e: Throwable) {
                    loadError = "Couldn't start the video engine: ${e.message}"
                    null
                }
            }
        }
    }
}
