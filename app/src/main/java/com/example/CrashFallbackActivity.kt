package com.example

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashFallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleText = intent.getStringExtra(EXTRA_ERROR_TITLE) ?: "Startup Error"
        val messageText = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Unexpected error occurred."
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0E17"))
            setPadding(48, 84, 48, 48)
        }

        val header = TextView(this).apply {
            text = "TIME • Startup Diagnostics"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 22f
        }

        val subtitle = TextView(this).apply {
            text = "$titleText: $messageText"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, 24, 0, 24)
        }

        val retryButton = Button(this).apply {
            text = "Restart TIME"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (launch != null) {
                    startActivity(launch)
                }
                finish()
            }
        }

        val traceView = TextView(this).apply {
            text = stackTrace
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 24)
        }

        val scroll = ScrollView(this).apply {
            addView(traceView)
        }

        root.addView(header)
        root.addView(subtitle)
        root.addView(retryButton)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
    }

    companion object {
        const val EXTRA_ERROR_TITLE = "extra_error_title"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }
}
