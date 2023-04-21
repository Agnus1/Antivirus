package ru.heatrk.antivirus.data.models

import com.sun.jna.Native

enum class MessageMethod(val id: ByteArray) {
    IS_PROTECTED(Native.toByteArray("is_protected")),
    ENABLE_PROTECTION(Native.toByteArray("enable_protection")),
    DISABLE_PROTECTION(Native.toByteArray("disable_protection")),
    SCAN_START(Native.toByteArray("scan_start")),
    SCAN_PAUSE(Native.toByteArray("scan_pause")),
    SCAN_RESUME(Native.toByteArray("scan_resume")),
    SCAN_STOP(Native.toByteArray("scan_stop")),
}