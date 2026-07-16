package com.example.usbkeyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    private var capsActive = false
    private lateinit var capsButton: Button

    // Fake-glass palette: translucent fills over the dark root background
    // (no live blur -- GradientDrawable can't do backdrop-filter).
    private val colorKeyNormal = Color.argb(13, 255, 255, 255)   // rgba(255,255,255,0.05)
    private val colorKeyBorder = Color.argb(26, 255, 255, 255)   // rgba(255,255,255,0.10)
    private val colorKeyAccent = Color.argb(64, 255, 107, 53)    // rgba(255,107,53,0.25)
    private val colorAccentBorder = Color.argb(102, 255, 107, 53) // rgba(255,107,53,0.40)
    private val colorAccentActive = Color.argb(153, 255, 107, 53) // brighter active state
    private val colorTextNormal = Color.argb(204, 255, 255, 255)  // rgba(255,255,255,0.8)
    private val colorTextAccent = Color.WHITE

    // Connection status dot colors
    private val statusDisconnected = Color.parseColor("#E74C3C") // red
    private val statusConnected = Color.parseColor("#2ECC71")    // green
    private val statusConnecting = Color.parseColor("#F1C40F")   // yellow
    private val statusError = Color.parseColor("#E67E22")        // orange

    private lateinit var statusDot: View

    private val accentKeys = setOf(
        "esc", "backspace", "enter", "tab", "caps", "up", "down", "left", "right",
        "ctrl", "alt", "shift", "delete", "home", "end", "pageup", "pagedown",
        "meta", "printscreen", "insert"
    )

    // key value -> layout weight. Esc/Backspace/Enter stay slightly widened
    // for reachability; Tab/Caps/Shift are normal-sized. Anything not
    // listed defaults to 1f.
    private val keyWeights = mapOf(
        "esc" to 1.5f, "backspace" to 1.5f,
        "enter" to 1.5f
    )

    private val fRow: List<Pair<String, String>> = (1..12).map { "F$it" to "f$it" }

    private val rows: List<List<Pair<String, String>>> = listOf(
        listOf("Esc" to "esc", "1" to "1", "2" to "2", "3" to "3", "4" to "4",
            "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9", "0" to "0",
            "-" to "-", "=" to "=", "⌫" to "backspace"),
        listOf("Tab" to "tab", "q" to "q", "w" to "w", "e" to "e", "r" to "r",
            "t" to "t", "y" to "y", "u" to "u", "i" to "i", "o" to "o", "p" to "p",
            "[" to "[", "]" to "]"),
        listOf("Caps" to "caps", "a" to "a", "s" to "s", "d" to "d", "f" to "f", "g" to "g",
            "h" to "h", "j" to "j", "k" to "k", "l" to "l",
            "Enter" to "enter"),
        listOf("Shift" to "shift", "z" to "z", "x" to "x", "c" to "c", "v" to "v", "b" to "b",
            "n" to "n", "m" to "m")
    )

    private val modifierKeys = listOf("ctrl", "alt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        val keyboardContainer: LinearLayout = findViewById(R.id.keyboardContainer)

        setStatusColor(statusDisconnected)
        statusDot.setOnClickListener { connect() }

        val scroll = ScrollView(this)
        val keyboardColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }
        scroll.addView(keyboardColumn)
        keyboardContainer.addView(scroll)

        keyboardColumn.addView(buildRow(fRow, compact = true))
        val maxRowWeight = rows.maxOf { rowWeightSum(it) }
        rows.forEach { row -> keyboardColumn.addView(buildRow(row, targetWeight = maxRowWeight)) }

        val bottomSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 0) }
        }
        val modifierAndSpace = buildModifierAndSpaceRow().apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f
            )
        }
        val navCluster = buildNavCluster().apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f
            ).apply { setMargins(8, 0, 0, 0) }
        }
        val arrowCluster = buildArrowCluster().apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f
            ).apply { setMargins(8, 0, 0, 0) }
        }
        bottomSection.addView(modifierAndSpace)
        bottomSection.addView(navCluster)
        bottomSection.addView(arrowCluster)
        keyboardColumn.addView(bottomSection)
    }

    private fun buildNavCluster(): LinearLayout {
        val cluster = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(makeKeyButton("Home", false, 1f) { onKeyPressed("home") }.also { styleKey(it, "home") })
        topRow.addView(makeKeyButton("PgUp", false, 1f) { onKeyPressed("pageup") }.also { styleKey(it, "pageup") })

        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(makeKeyButton("End", false, 1f) { onKeyPressed("end") }.also { styleKey(it, "end") })
        bottomRow.addView(makeKeyButton("PgDn", false, 1f) { onKeyPressed("pagedown") }.also { styleKey(it, "pagedown") })

        cluster.addView(topRow)
        cluster.addView(bottomRow)
        return cluster
    }

    private fun rowWeightSum(keys: List<Pair<String, String>>): Float =
        keys.sumOf { (_, value) -> (keyWeights[value] ?: 1f).toDouble() }.toFloat()

    private fun buildRow(keys: List<Pair<String, String>>, compact: Boolean = false, targetWeight: Float? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }
        keys.forEach { (label, value) ->
            val weight = keyWeights[value] ?: 1f
            val button = makeKeyButton(label, compact, weight) {
                if (value == "caps") toggleCaps() else onKeyPressed(value)
            }
            styleKey(button, value)
            if (value == "caps") capsButton = button
            row.addView(button)
        }
        // Pad shorter rows with an invisible spacer so every row's letter
        // keys divide the same total weight -- keeps key width consistent
        // and columns aligned across rows instead of drifting.
        if (targetWeight != null) {
            val remainder = targetWeight - rowWeightSum(keys)
            if (remainder > 0.01f) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, remainder)
                }
                row.addView(spacer)
            }
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
            val btn = makeKeyButton(mod.replaceFirstChar { it.uppercase() }, false, 1f) { toggleModifier(mod) }
            styleKey(btn, mod)
            modifierButtons[mod] = btn
            row.addView(btn)
        }
        val delButton = makeKeyButton("Del", false, 1f) { onKeyPressed("delete") }
        styleKey(delButton, "delete")
        row.addView(delButton)
        val winButton = makeKeyButton("Win", false, 1f) { onKeyPressed("meta") }
        styleKey(winButton, "meta")
        row.addView(winButton)
        val prtScButton = makeKeyButton("PrtSc", false, 1f) { onKeyPressed("printscreen") }
        styleKey(prtScButton, "printscreen")
        row.addView(prtScButton)
        val insButton = makeKeyButton("Ins", false, 1f) { onKeyPressed("insert") }
        styleKey(insButton, "insert")
        row.addView(insButton)
        val space = makeKeyButton("Space", false, 2f) { onKeyPressed("space") }
        styleKey(space, "space")
        row.addView(space)
        return row
    }

    private fun buildArrowCluster(): LinearLayout {
        val cluster = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(makeKeyButton("↑", false, 1f) { onKeyPressed("up") }.also { styleKey(it, "up") })

        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(makeKeyButton("←", false, 1f) { onKeyPressed("left") }.also { styleKey(it, "left") })
        bottomRow.addView(makeKeyButton("↓", false, 1f) { onKeyPressed("down") }.also { styleKey(it, "down") })
        bottomRow.addView(makeKeyButton("→", false, 1f) { onKeyPressed("right") }.also { styleKey(it, "right") })

        cluster.addView(topRow)
        cluster.addView(bottomRow)
        return cluster
    }

    private fun makeKeyButton(label: String, compact: Boolean, weight: Float, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = if (compact) 10f else 13f
            setPadding(2, if (compact) 4 else 8, 2, if (compact) 4 else 8)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight
            ).apply { setMargins(2, 2, 2, 2) }
            gravity = Gravity.CENTER
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun styleKey(button: Button, keyValue: String) {
        val isAccent = accentKeys.contains(keyValue)
        val drawable = GradientDrawable().apply {
            cornerRadius = 2f
            setColor(if (isAccent) colorKeyAccent else colorKeyNormal)
            setStroke(2, if (isAccent) colorAccentBorder else colorKeyBorder)
        }
        button.background = drawable
        button.setTextColor(if (isAccent) colorTextAccent else colorTextNormal)
    }

    private fun toggleModifier(mod: String) {
        val btn = modifierButtons[mod] ?: return
        val drawable = GradientDrawable().apply {
            cornerRadius = 2f
            setStroke(2, colorAccentBorder)
        }
        if (activeModifiers.contains(mod)) {
            activeModifiers.remove(mod)
            drawable.setColor(colorKeyAccent)
        } else {
            activeModifiers.add(mod)
            drawable.setColor(colorAccentActive)
        }
        btn.background = drawable
    }

    private fun toggleCaps() {
        capsActive = !capsActive
        val drawable = GradientDrawable().apply {
            cornerRadius = 2f
            setStroke(2, colorAccentBorder)
            setColor(if (capsActive) colorAccentActive else colorKeyAccent)
        }
        capsButton.background = drawable
        // Placeholder only: caps state is tracked and shown, but letter
        // keys still send their literal (lowercase) value for now.
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
            cornerRadius = 2f
            setColor(colorKeyAccent)
            setStroke(2, colorAccentBorder)
        }
    }

    private fun setStatusColor(color: Int) {
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun connect() {
        runOnUiThread {
            setStatusColor(statusConnecting)
            statusText.text = "Connecting..."
        }
        ioExecutor.execute {
            try {
                socket?.close()
                val s = Socket("127.0.0.1", PORT)
                writer = PrintWriter(s.getOutputStream(), true)
                socket = s
                runOnUiThread {
                    setStatusColor(statusConnected)
                    statusText.text = "Connected to PC"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setStatusColor(statusError)
                    statusText.text = "Connection failed: ${e.message}"
                }
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
