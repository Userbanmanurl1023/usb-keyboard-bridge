package com.example.usbkeyboard

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Minimal keyboard bridge UI.
 *
 * Design goals (low resource usage):
 * - No RecyclerView, no animations, no images.
 * - Single background thread for the socket; no polling.
 * - Plain java.net.Socket, no OkHttp/Retrofit — keeps APK small and avoids
 *   pulling in unused HTTP stack for a simple line-based TCP protocol.
 *
 * Connects to 127.0.0.1:8901, which `adb reverse tcp:8901 tcp:8901`
 * tunnels directly to the PC-side Python server over the USB cable.
 */
class MainActivity : AppCompatActivity() {

    private val PORT = 8901
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView
    private lateinit var input: EditText

    private var lastSentLength = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        input = findViewById(R.id.inputField)
        val connectButton: Button = findViewById(R.id.connectButton)
        val enterButton: Button = findViewById(R.id.enterButton)
        val clearButton: Button = findViewById(R.id.clearButton)

        connectButton.setOnClickListener { connect() }

        enterButton.setOnClickListener {
            send("""{"type":"key","value":"enter"}""")
        }

        clearButton.setOnClickListener {
            input.setText("")
            lastSentLength = 0
        }

        // Send only the newly typed characters as they're typed,
        // so the PC field always mirrors the phone field.
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.length > lastSentLength) {
                    val added = text.substring(lastSentLength)
                    sendText(added)
                } else if (text.length < lastSentLength) {
                    val deletedCount = lastSentLength - text.length
                    repeat(deletedCount) {
                        send("""{"type":"key","value":"backspace"}""")
                    }
                }
                lastSentLength = text.length
            }
        })
    }

    private fun connect() {
        ioExecutor.execute {
            try {
                socket?.close()
                val s = Socket("127.0.0.1", PORT)
                writer = PrintWriter(s.getOutputStream(), true)
                socket = s
                runOnUiThread { statusText.text = "Connected to PC" }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Connection failed: ${e.message}" }
            }
        }
    }

    private fun sendText(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        send("""{"type":"text","value":"$escaped"}""")
    }

    private fun send(jsonLine: String) {
        ioExecutor.execute {
            try {
                writer?.println(jsonLine)
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Send failed: ${e.message}" }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.execute { socket?.close() }
        ioExecutor.shutdown()
    }
}
