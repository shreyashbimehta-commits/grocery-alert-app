package com.groceryalert.son

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SonMainActivity : AppCompatActivity() {

    private lateinit var itemInput: EditText
    private lateinit var targetGroup: RadioGroup
    private lateinit var sendButton: Button
    private var isSending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_son_main)

        startService(Intent(this, SonWebSocketService::class.java))

        itemInput = findViewById(R.id.item_input)
        targetGroup = findViewById(R.id.target_group)
        sendButton = findViewById(R.id.send_button)

        sendButton.setOnClickListener { sendAlert() }
    }

    private fun getTarget(): String {
        return when (targetGroup.checkedRadioButtonId) {
            R.id.target_dad -> "DAD"
            R.id.target_both -> "BOTH"
            else -> "MOM"
        }
    }

    private fun sendAlert() {
        val item = itemInput.text.toString().trim()
        if (item.isEmpty()) {
            itemInput.error = "Enter an item"
            return
        }

        if (isSending) return
        isSending = true
        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        val target = getTarget()

        SonWebSocketClient.sendAlert(item, target) { success, message ->
            runOnUiThread {
                isSending = false
                sendButton.isEnabled = true
                sendButton.text = "Send Alert"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (success) {
                    itemInput.text.clear()
                }
            }
        }
    }
}
