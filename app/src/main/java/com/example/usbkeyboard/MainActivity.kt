package com.example.usbkeyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private val colorKeyNormal = Color.parseColor("#E8E8E8")
    private val colorKeyAccent = Color.parseColor("#FF6B35")
    private val colorTextNormal = Color.parseColor("#1A1A1A")
    private val colorTextAccent = Color.parseColor("#FFFFFF")
    private val colorAccentActive = Color.parseColor("#FF9166")

    private val accentKeys = setOf(
        "esc", "backspace", "enter", "tab", "up", "down", "left", "right",
        "ctrl", "alt", "shift"
    )

    private val fRow: List<Pair<String, String>> = (1..12).map { "F$it" to "f$it" }

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
            "n" to "n", "m" to "m", "," to ",", "." to ".", "/" to "/")
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
        val keyboardColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }
        scroll.addView(keyboardColumn)
        keyboardContainer.addView(scroll)

        keyboardColumn.addView(buildRow(fRow, compact = true))
        rows.forEach { row -> keyboardColumn.addView(buildRow(row)) }

        val bottomSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 0) }
        }
        val modifierAndSpace = buildModifierAndSpaceRow().apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f
            )
        }
        val arrowCluster = buildArrowCluster().apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(8, 0, 0, 0) }
        }
        bottomSection.addView(modifierAndSpace)
        bottomSection.addView(arrowCluster)
        keyboardColumn.addView(bottomSection)
    }

    private fun buildRow(keys: List<Pair<String, String>>, compact: Boolean = false): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }
        keys.forEach { (label, value) ->
            row.addView(makeKeyButton(label, compact) { onKeyPressed(value) }.also {
                styleKey(it, value)
            })
        }
        return row
    }

    private fun buildModifierAndSpaceRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        modifierKeys.forEach { mod ->
            val btn = makeKeyButton(mod.replaceFirstChar { it.uppercase() }, false) { toggleModifier(mod) }
            styleKey(btn, mod)
            modifierButtons[mod] = btn
            row.addView(btn)
        }
        val space = makeKeyButton("Space", false) { onKeyPressed("space") }
        styleKey(space, "space")
        space.layoutParams = (space.layoutParams as LinearLayout.LayoutParams).apply { weight = 4f }
        row.addView(space)
        return row
    }

    private fun buildArrowCluster(): LinearLayout {
        val cluster = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(spacerKey())
        topRow.addView(makeKeyButton("↑", false) { onKeyPressed("up") }.also { styleKey(it, "up") })
        topRow.addView(spacerKey())

        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(makeKeyButton("←", false) { onKeyPressed("left") }.also { styleKey(it, "left") })
        bottomRow.addView(makeKeyButton("↓", false) { onKeyPressed("down") }.also { styleKey(it, "down") })
        bottomRow.addView(makeKeyButton("→", false) { onKeyPressed("right") }.also { styleKey(it, "right") })

        cluster.addView(topRow)
        cluster.addView(bottomRow)
        return cluster
    }

    private fun spacerKey(): Button {
        return makeKeyButton("", false) {}.apply {
            isEnabled = false
            background = null
        }
    }

    private fun makeKeyButton(label: String, compact: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = if (compact) 10f else 13f
            setPadding(2, if (compact) 4 else 8, 2, if (compact) 4 else 8)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(2, 2, 2, 2) }
            gravity = Gravity.CENTER
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun styleKey(button: Button, keyValue: String) {
        val isAccent = accentKeys.contains(keyValue)
        val drawable = GradientDrawable().apply {
            cornerRadius = 10f
            setColor(if (isAccent) colorKeyAccent else colorKeyNormal)
        }
        button.background = drawable
        button.setTextColor(if (isAccent) colorTextAccent else colorTextNormal)
    }

    private fun toggleModifier(mod: String) {
        val btn = modifierButtons[mod] ?: return
        val drawable = GradientDrawable().apply { cornerRadius = 10f }
        if (activeModifiers.contains(mod)) {
            activeModifiers.remove(mod)
            drawable.setColor(colorKeyAccent)
        } else {
            activeModifiers.add(mod)
            drawable.setColor(colorAccentActive)
        }
        btn.background = drawable
    }

    private fun onKeyPressed(value: String) {
        val combo = if (activeModifiers.isEmpty()) {
            value
        } else {
            (activeModifiers.toList() + value).joinToString("+")
        }
        send("""{"type":"key","value":"$combo"}""")

        if (activeModifiers.isNotEmpty()) {
            activeModifiers.toList().forEach { mod -> resetModifierColor(mod) }
            activeModifiers.clear()
        }
    }

    private fun resetModifierColor(mod: String) {
        modifierButtons[mod]?.background = GradientDrawable().apply {
            cornerRadius = 10f
            setColor(colorKeyAccent)
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
