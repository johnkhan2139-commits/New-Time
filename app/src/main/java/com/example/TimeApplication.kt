package com.example

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.example.data.remote.RuntimeFirebaseConfig
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class TimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return

        RuntimeFirebaseConfig.ensureInitialized(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString().take(4000)
                Log.e("TimeCrash", "Uncaught exception on ${thread.name}", throwable)

                val intent = Intent(this, CrashFallbackActivity::class.java).apply {
                    putExtra(CrashFallbackActivity.EXTRA_ERROR_TITLE, throwable::class.java.simpleName)
                    putExtra(CrashFallbackActivity.EXTRA_ERROR_MESSAGE, throwable.localizedMessage ?: throwable.toString())
                    putExtra(CrashFallbackActivity.EXTRA_STACK_TRACE, stackTrace)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
                startActivity(intent)
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isCrashProcess(): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                getProcessName().endsWith(":crash")
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
