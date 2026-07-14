package com.example.usbkeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var display: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.display)

        // Example: Setup button listeners for the keyboard keys defined in XML
        // You would bind your buttons here using findViewById
        val btnA: Button = findViewById(R.id.btnA)
        btnA.setOnClickListener { handleKeyPress("a") }
    }

    private fun handleKeyPress(key: String) {
        // Update local display
        display.append(key)
        
        // Send to host
        ioExecutor.execute {
            try {
                if (writer == null) {
                    // Logic to ensure socket is connected
                }
                writer?.println(key)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.execute { socket?.close() }
        ioExecutor.shutdown()
    }
}
