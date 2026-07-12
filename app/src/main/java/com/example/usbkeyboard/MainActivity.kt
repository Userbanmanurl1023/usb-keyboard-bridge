package com.example.usbkeyboard

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val PORT = 8901
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView

    private val activeModifiers = linkedSetOf<String>()
    private val modifierButtons = mutableMapOf<String, Button>()

    private val rows: List<List<Pair<String, String>>> = listOf(
        listOf("Esc" to "esc", "1" to "1", "2" to "2", "3" to "3", "4" to "4",
            "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9", "0" to "0",
            "-" to "-", "=" to "=", "⌫" to "backspace"),
        listOf("Tab" to "tab", "q" to "q", "w" to "w", "e" to "e", "r" to "r",
            "t" to "t", "y" to "y", "u" to "u", "i" to "i", "o" to "o", "p" to "p",
            "[" to "[", "]" to "]"),
        listOf("a" to "a", "s" to "s", "d" to "d", "f" to "f", "g" to "g",
            "h" to "h", "j" to "j", "k" to "k", "l" to "l", ";" to ";",
            "'" to "'", "Enter" to "enter"),
        listOf("z" to "z", "x" to "x", "c" to "c", "v" to "v", "b" to "b",
            "n" to "n", "m" to "m", "," to ",", "." to ".", "/" to "/"),
        listOf("←" to "left", "↑" to "up", "↓" to "down", "→" to "right")
    )

    private val modifierKeys = listOf("ctrl", "alt", "shift")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val connectButton: Button = findViewById(R.id.connectButton)
        val keyboardContainer: LinearLayout = findViewById(R.id.keyboardContainer)

        connectButton.setOnClickListener { connect() }

        val scroll = ScrollView(this)
        val keyboardColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(keyboardColumn)
        keyboardContainer.addView(scroll)

        rows.forEach { row -> keyboardColumn.addView(buildRow(row)) }
        keyboardColumn.addView(buildModifierAndSpaceRow())
    }

    private fun buildRow(keys: List<Pair<String, String>>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }
        keys.forEach { (label, value) ->
            row.addView(makeKeyButton(label) { onKeyPressed(value) })
        }
        return row
    }

    private fun buildModifierAndSpaceRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }
        modifierKeys.forEach { mod ->
            val btn = makeKeyButton(mod.replaceFirstChar { it.uppercase() }) { toggleModifier(mod) }
            modifierButtons[mod] = btn
            row.addView(btn)
        }
        val space = makeKeyButton("Space") { onKeyPressed("space") }
        space.layoutParams = (space.layoutParams as LinearLayout.LayoutParams).apply { weight = 4f }
        row.addView(space)
        return row
    }

    private fun makeKeyButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setPadding(2, 8, 2, 8)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(1, 1, 1, 1) }
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun toggleModifier(mod: String) {
        if (activeModifiers.contains(mod)) {
            activeModifiers.remove(mod)
            modifierButtons[mod]?.setBackgroundColor(Color.parseColor("#333333"))
        } else {
            activeModifiers.add(mod)
            modifierButtons[mod]?.setBackgroundColor(Color.parseColor("#3B82F6"))
        }
    }

    private fun onKeyPressed(value: String) {
        val combo = if (activeModifiers.isEmpty()) {
            value
        } else {
            (activeModifiers.toList() + value).joinToString("+")
        }
        send("""{"type":"key","value":"$combo"}""")

        if (activeModifiers.isNotEmpty()) {
            activeModifiers.toList().forEach { mod ->
                modifierButtons[mod]?.setBackgroundColor(Color.parseColor("#333333"))
            }
            activeModifiers.clear()
        }
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
