package com.clienttools.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.clienttools.sdk.webview.ui.FloatingControlPanel

class MainActivity : AppCompatActivity() {

    private lateinit var floatingPanel: FloatingControlPanel
    private lateinit var webViewViewModel: WebViewViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        webViewViewModel = ViewModelProvider(this).get(WebViewViewModel::class.java)

        // Add FloatingControlPanel to root layout
        val rootLayout = findViewById<FrameLayout>(R.id.root_container)
        if (rootLayout != null) {
            floatingPanel = FloatingControlPanel(this)
            rootLayout.addView(floatingPanel)
        }

        findViewById<Button>(R.id.login_button).setOnClickListener {
            startTestPage(R.layout.login_screen, "Login Screen")
        }

        findViewById<Button>(R.id.form_button).setOnClickListener {
            startTestPage(R.layout.form_screen, "Form Screen")
        }

        // WebView overlay test button
        val webviewButton = Button(this).apply {
            text = "WebView Overlay Test"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
        }
        findViewById<LinearLayout>(R.id.button_container).addView(webviewButton)

        webviewButton.setOnClickListener {
            val intent = Intent(this, WebViewTestActivity::class.java)
            startActivity(intent)
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
