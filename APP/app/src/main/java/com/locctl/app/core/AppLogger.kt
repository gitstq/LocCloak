package com.locctl.app.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long,
    val time: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object AppLogger {
    private const val MAX_LOGS = 300
    private var sequence = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var logFile: java.io.File? = null

    fun init(context: android.content.Context) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = java.io.File(dir, "locctl_debug.log")
        }
    }

    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    @Synchronized
    private fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$message\n${Log.getStackTraceString(tr).trim()}" else message
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, fullMsg)
            LogLevel.INFO -> Log.i(tag, fullMsg)
            LogLevel.WARN -> Log.w(tag, fullMsg)
            LogLevel.ERROR -> Log.e(tag, fullMsg)
        }
        val entry = LogEntry(
            id = ++sequence,
            time = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = fullMsg
        )
        val current = _logs.value
        val updated = if (current.size >= MAX_LOGS) {
            current.drop(current.size - MAX_LOGS + 1) + entry
        } else {
            current + entry
        }
        _logs.value = updated

        logFile?.let { file ->
            ioScope.launch {
                runCatching {
                    file.appendText("[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.message}\n")
                }
            }
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, message, tr)

    fun clear() {
        _logs.value = emptyList()
        runCatching { logFile?.delete() }
    }

    fun getFormattedLogs(maxCount: Int = 100): String {
        val list = _logs.value.takeLast(maxCount)
        val text = list.joinToString("\n") { "[${it.time}] [${it.level.name}] [${it.tag}] ${it.message}" }
        return if (text.length > 500_000) text.takeLast(500_000) else text
    }
}

