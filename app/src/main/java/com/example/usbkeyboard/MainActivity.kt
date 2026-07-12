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
