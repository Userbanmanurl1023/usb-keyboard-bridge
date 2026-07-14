package com.example.usbkeyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // Network & Socket communication structures
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Target host parameters (typically 10.0.2.2 for Android emulator to host machine)
    private val hostAddress = "10.0.2.2"
    private val hostPort = 8901
    private var isConnected = false

    // UI elements
    private lateinit var display: TextView
    private lateinit var rootContainer: ViewGroup

    // Keyboard modifier states
    private var isShiftActive = false
    private var isCtrlActive = false
    private var isAltActive = false
    private var isCapsLockActive = false

    // References to mod buttons for live visual highlighting
    private var btnShiftL: Button? = null
    private var btnShiftR: Button? = null
    private var btnCtrl: Button? = null
    private var btnAlt: Button? = null
    private var btnCaps: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind main container and text output display
        rootContainer = findViewById(android.R.id.content) as ViewGroup
        display = findViewById(R.id.display)
        display.text = "Status: Initializing connection...\n"

        // Locate layout-level modifiers to style and interact with them
        btnShiftL = findViewById(R.id.btnShiftL)
        btnShiftR = findViewById(R.id.btnShiftR)
        btnCtrl = findViewById(R.id.btnCtrl)
        btnAlt = findViewById(R.id.btnAlt)
        btnCaps = findViewById(R.id.btnCaps)

        // Eval 1: Handle soft keyboard visibility changes gracefully to keep layout dimensions pristine
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Apply bottom padding dynamically to lift the keyboard container without distorting layout weights
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                if (imeVisible) imeHeight else 0
            )
            insets
        }

        // Discover and attach unified click logic to all key elements in the view hierarchy
        bindKeyboardButtons(rootContainer)

        // Initiate our asynchronous heartbeat and connection loops
        startNetworkConnectionLoop()
        startHeartbeatMonitor()
    }

    private fun bindKeyboardButtons(view: View) {
        if (view is Button) {
            val keyLabel = view.text.toString()
            
            // Do not override navigation or custom handler buttons, but set click triggers
            view.setOnClickListener {
                handleKeyInteraction(keyLabel)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                bindKeyboardButtons(view.getChildAt(i))
            }
        }
    }

    private fun handleKeyInteraction(key: String) {
        var processedKey = key
        var transmitKey = true

        when (key.lowercase()) {
            "shift" -> {
                isShiftActive = !isShiftActive
                updateModifierVisuals()
                transmitKey = false
            }
            "ctrl" -> {
                isCtrlActive = !isCtrlActive
                updateModifierVisuals()
                transmitKey = false
            }
            "alt" -> {
                isAltActive = !isAltActive
                updateModifierVisuals()
                transmitKey = false
            }
            "caps" -> {
                isCapsLockActive = !isCapsLockActive
                updateModifierVisuals()
                transmitKey = false
            }
            "space" -> {
                processedKey = " "
                display.append(" ")
            }
            "enter" -> {
                processedKey = "\n"
                display.append("\n")
            }
            "back" -> {
                processedKey = "Backspace"
                val currentText = display.text.toString()
                if (currentText.isNotEmpty()) {
                    display.text = currentText.substring(0, currentText.length - 1)
                }
            }
            "tab" -> {
                processedKey = "\t"
                display.append("\t")
            }
            "esc" -> {
                processedKey = "Escape"
            }
            "↑" -> processedKey = "ArrowUp"
            "↓" -> processedKey = "ArrowDown"
            "←" -> processedKey = "ArrowLeft"
            "→" -> processedKey = "ArrowRight"
            else -> {
                // Character styling adjustment according to shift & caps states
                val isUpper = isShiftActive xor isCapsLockActive
                processedKey = if (isUpper) key.uppercase() else key.lowercase()
                display.append(processedKey)
                
                // Reset standard Shift key toggle after next typing character input
                if (isShiftActive) {
                    isShiftActive = false
                    updateModifierVisuals()
                }
            }
        }

        if (transmitKey) {
            sendKeystrokeToHost(processedKey)
        }
    }

    private fun updateModifierVisuals() {
        val activeColor = ColorStateList.valueOf(Color.parseColor("#FF6B35")) // Vibrant Orange Highlight
        val inactiveColor = ColorStateList.valueOf(Color.parseColor("#444444")) // Semi-translucent Glass

        btnShiftL?.backgroundTintList = if (isShiftActive) activeColor else inactiveColor
        btnShiftR?.backgroundTintList = if (isShiftActive) activeColor else inactiveColor
        btnCtrl?.backgroundTintList = if (isCtrlActive) activeColor else inactiveColor
        btnAlt?.backgroundTintList = if (isAltActive) activeColor else inactiveColor
        btnCaps?.backgroundTintList = if (isCapsLockActive) activeColor else inactiveColor
    }

    private fun startNetworkConnectionLoop() {
        ioExecutor.execute {
            attemptSocketConnection()
        }
    }

    @Synchronized
    private fun attemptSocketConnection() {
        if (isConnected && socket?.isConnected == true) return

        try {
            // Close existing pipelines prior to re-establishing connection
            socket?.close()
            
            socket = Socket()
            // 2-second timeout threshold to prevent app suspension
            socket?.connect(InetSocketAddress(hostAddress, hostPort), 2000)
            writer = PrintWriter(socket!!.getOutputStream(), true)
            
            setConnectionState(true)
        } catch (e: Exception) {
            setConnectionState(false)
        }
    }

    // Eval 2: Real-time network heartbeats to verify live host pipe integrity
    private fun startHeartbeatMonitor() {
        scheduler.scheduleAtFixedRate({
            if (isConnected) {
                ioExecutor.execute {
                    try {
                        // Check structural socket details and send a small ping frame
                        val isDead = socket == null || socket!!.isClosed || !socket!!.isConnected
                        if (isDead) {
                            setConnectionState(false)
                        } else {
                            // Construct standard JSON heartbeat frame
                            val heartbeat = JSONObject().apply {
                                put("type", "ping")
                                put("timestamp", System.currentTimeMillis())
                            }
                            writer?.println(heartbeat.toString())
                            if (writer?.checkError() == true) {
                                // Connection pipe has severed implicitly
                                setConnectionState(false)
                            }
                        }
                    } catch (e: Exception) {
                        setConnectionState(false)
                    }
                }
            } else {
                // Instantly try to re-establish the broken socket bridge
                ioExecutor.execute {
                    attemptSocketConnection()
                }
            }
        }, 0, 2000, TimeUnit.MILLISECONDS)
    }

    private fun sendKeystrokeToHost(keyValue: String) {
        ioExecutor.execute {
            if (!isConnected) return@execute

            try {
                // Build robust, clean payload container 
                val payload = JSONObject().apply {
                    put("type", "keypress")
                    put("key", keyValue)
                    put("shift", isShiftActive)
                    put("ctrl", isCtrlActive)
                    put("alt", isAltActive)
                    put("caps", isCapsLockActive)
                    put("timestamp", System.currentTimeMillis())
                }
                writer?.println(payload.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setConnectionState(state: Boolean) {
        isConnected = state
        mainHandler.post {
            if (state) {
                display.hint = "Host Active: Direct typing session connected."
            } else {
                display.hint = "Host Offline: Reconnecting on port $hostPort..."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.execute {
            try {
                writer?.close()
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ioExecutor.shutdown()
        scheduler.shutdown()
    }
}
