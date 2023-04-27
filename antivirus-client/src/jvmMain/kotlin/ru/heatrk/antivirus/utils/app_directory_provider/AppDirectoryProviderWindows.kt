package ru.heatrk.antivirus.utils.app_directory_provider

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.PointerByReference

class AppDirectoryProviderWindows : AppDirectoryProvider {
    private val shell32 = Shell32.INSTANCE
    private val ole32 = Ole32.INSTANCE

    override fun providePath(): String {
        val pathPointer = PointerByReference()

        val handle = shell32.SHGetKnownFolderPath(
            KnownFolders.FOLDERID_ProgramData,
            0,
            WinNT.HANDLE(Pointer.NULL),
            pathPointer
        )

        if (!W32Errors.SUCCEEDED(handle.toInt())) {
            println("App directory path provider error (code = ${handle.toInt()})")
        }

        val path = "${pathPointer.value.getWideString(0)}\\$APP_DIRECTORY_NAME\\"

        ole32.CoTaskMemFree(pathPointer.value)

        return path
    }

    companion object {
        private const val APP_DIRECTORY_NAME = "exchange_data"
    }
}