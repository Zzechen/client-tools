package com.clienttools.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.login_button).setOnClickListener {
            startTestPage(R.layout.login_screen, "Login Screen")
        }

        findViewById<Button>(R.id.form_button).setOnClickListener {
            startTestPage(R.layout.form_screen, "Form Screen")
        }
    }

    private fun startTestPage(layoutResId: Int, pageName: String) {
        val intent = Intent(this, TestScreenHost::class.java).apply {
            putExtra("layoutResId", layoutResId)
            putExtra("pageName", pageName)
        }
        startActivity(intent)
    }
}
