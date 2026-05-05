package jarvis.win32

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import java.io.File

data class WindowInfo(
    val title: String,
    val processName: String,
    val pid: Long,
)

object Win32 {
    private const val TITLE_BUF_SIZE = 512

    fun activeWindow(): WindowInfo {
        val hwnd: HWND = User32.INSTANCE.GetForegroundWindow()
            ?: return WindowInfo("", "unknown", 0)
        val titleBuf = CharArray(TITLE_BUF_SIZE)
        val titleLen = User32.INSTANCE.GetWindowText(hwnd, titleBuf, titleBuf.size)
        val title = if (titleLen > 0) String(titleBuf, 0, titleLen) else ""

        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val pid = pidRef.value.toLong().coerceAtLeast(0L)

        return WindowInfo(title, processName(pid), pid)
    }

    private fun processName(pid: Long): String {
        if (pid <= 0) return "unknown"
        return try {
            ProcessHandle.of(pid)
                .flatMap { it.info().command() }
                .map { File(it).name }
                .orElse("unknown")
        } catch (_: Exception) {
            "unknown"
        }
    }
}
