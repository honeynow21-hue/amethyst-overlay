package org.amethystoverlay.app

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

const val CRASH_FILE_NAME = "last_crash.txt"

fun installCrashLogger(dir: File) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            File(dir, CRASH_FILE_NAME).writeText("Thread: ${thread.name}\n\n$sw")
        } catch (e: Exception) {
            // ignore: logging must never block the real crash
        }
        previous?.uncaughtException(thread, throwable)
    }
}
