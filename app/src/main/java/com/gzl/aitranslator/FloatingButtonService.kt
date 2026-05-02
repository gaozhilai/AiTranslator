package com.gzl.aitranslator

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var buttonParams: WindowManager.LayoutParams? = null
    private var resultPopup: View? = null

    private var startX = 0
    private var startY = 0
    private var startRawX = 0f
    private var startRawY = 0f
    private var dragging = false

    private var screenW = 0
    private var screenH = 0
    private var btnSize = 0
    private var resultContent: TextView? = null
    @Volatile private var translating = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "floating_button_channel"
        const val NOTIFY_ID = 1
        const val TAP_SLOP = 10

        var isRunning by mutableStateOf(false)
            private set
    }

    override fun onCreate() {
        super.onCreate()
        AppConfig.init(this)
        isRunning = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID, "悬浮翻译按钮", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "用于保持悬浮按钮在后台运行" }
                .let { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            NOTIFY_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI 翻译")
                .setContentText("悬浮翻译按钮已启用")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        resources.displayMetrics.let { screenW = it.widthPixels; screenH = it.heightPixels }
        btnSize = dp(56)

        createButton()
    }

    // ── button ─────────────────────────────────────────────────────

    private fun createButton() {
        floatingView = TextView(this).apply {
            text = "译"
            textSize = 18f
            setTextColor(-0x1)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.floating_button_bg)
            elevation = dp(4).toFloat()
        }

        buttonParams = WindowManager.LayoutParams(
            btnSize, btnSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - btnSize - dp(16)
            y = screenH / 2
        }

        floatingView.setOnTouchListener { _, e -> onButtonTouch(e) }
        windowManager.addView(floatingView, buttonParams)
    }

    private fun onButtonTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = buttonParams!!.x; startY = buttonParams!!.y
                startRawX = e.rawX; startRawY = e.rawY
                dragging = false
                setButtonFocusable(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - startRawX
                val dy = e.rawY - startRawY
                if (abs(dx) > TAP_SLOP || abs(dy) > TAP_SLOP) dragging = true
                if (dragging) {
                    buttonParams!!.x = (startX + dx).toInt()
                    buttonParams!!.y = (startY + dy).toInt()
                    windowManager.updateViewLayout(floatingView, buttonParams)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) snapToEdge() else onTranslate()
                setButtonFocusable(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                setButtonFocusable(false)
            }
        }
        return true
    }

    private fun setButtonFocusable(focusable: Boolean) {
        val params = buttonParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (::floatingView.isInitialized) {
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun snapToEdge() {
        val cur = buttonParams!!.x
        val target = when {
            cur < dp(30) -> 0
            cur > screenW - btnSize - dp(30) -> screenW - btnSize
            else -> return
        }
        ValueAnimator.ofInt(cur, target).apply {
            duration = 200
            addUpdateListener { buttonParams!!.x = it.animatedValue as Int; windowManager.updateViewLayout(floatingView, buttonParams) }
            start()
        }
    }

    private fun onTranslate() {
        if (translating) return
        val text = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()

        if (text.isBlank()) {
            showPopup(null, getString(R.string.no_content_hint))
        } else {
            translating = true
            showPopup(text, "翻译中...")
            scope.launch(Dispatchers.IO) {
                try {
                    val prompt = AppConfig.buildPrompt(text)
                    val sb = StringBuilder()
                    val sendResult = TranslationManager.sendMessageStream(prompt) { token ->
                        sb.append(token)
                        val current = sb.toString()
                        withContext(Dispatchers.Main) {
                            resultContent?.text = current
                        }
                    }
                    when (sendResult) {
                        SendResult.NotInitialized -> withContext(Dispatchers.Main) {
                            resultContent?.text = "模型未就绪"
                        }
                        SendResult.Busy -> withContext(Dispatchers.Main) {
                            resultContent?.text = "模型资源等待中..."
                        }
                        SendResult.Success -> { /* tokens already streamed */ }
                    }
                } finally {
                    translating = false
                }
            }
        }
    }

    // ── popup ──────────────────────────────────────────────────────

    private fun showPopup(source: String?, result: String) {
        dismissPopup()

        val card = makeCard(source, result)
        resultPopup = FrameLayout(this).apply {
            setBackgroundColor(0x40000000)
            isClickable = true
            setOnClickListener { dismissPopup() }
            addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
        }

        windowManager.addView(resultPopup, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { dimAmount = 0.3f; gravity = Gravity.TOP or Gravity.START })
    }

    private fun makeCard(source: String?, result: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply {
            setColor(-0x1); cornerRadius = dp(12).toFloat()
        }

        if (source != null) {
            addView(label("原文"))
            addView(content(source))
        }
        addView(label(if (source != null) "翻译" else "提示"))
        val resultView = content(result)
        addView(resultView)
        resultContent = resultView

        // drag
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f; var moved = false
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    cx = v.translationX; cy = v.translationY
                    sx = e.rawX; sy = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > TAP_SLOP || abs(dy) > TAP_SLOP) moved = true
                    if (moved) { v.translationX = cx + dx; v.translationY = cy + dy }
                }
            }
            true
        }
        isClickable = true
        setOnClickListener { /* consume */ }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(0xFF999999.toInt())
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun content(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setTextColor(0xFF333333.toInt())
        lineHeight = dp(24)
    }

    private fun dismissPopup() {
        resultPopup?.let { try { windowManager.removeView(it) } catch (_: IllegalArgumentException) {} }
        resultPopup = null
        resultContent = null
    }

    // ── lifecycle ──────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        dismissPopup()
        scope.cancel()
        if (buttonParams != null && ::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: IllegalArgumentException) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
