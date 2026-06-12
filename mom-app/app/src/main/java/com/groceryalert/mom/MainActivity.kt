package com.groceryalert.mom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var itemInput: EditText
    private lateinit var targetGroup: RadioGroup
    private lateinit var alertButton: Button
    private var isSending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startService(Intent(this, MomWebSocketService::class.java))

        itemInput = findViewById(R.id.item_input)
        targetGroup = findViewById(R.id.target_group)
        alertButton = findViewById(R.id.alert_button)

        alertButton.setOnClickListener { sendAlert() }
    }

    private fun getTarget(): String {
        return when (targetGroup.checkedRadioButtonId) {
            R.id.target_son -> "SON"
            R.id.target_both -> "BOTH"
            else -> "DAD"
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
        alertButton.isEnabled = false
        alertButton.text = "Sending..."

        val target = getTarget()

        MomWebSocketClient.sendAlert(item, target) { success, message ->
            runOnUiThread {
                isSending = false
                alertButton.isEnabled = true
                alertButton.text = "Send Alert"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                if (success) {
                    itemInput.text.clear()
                }
            }
        }
    }
}
