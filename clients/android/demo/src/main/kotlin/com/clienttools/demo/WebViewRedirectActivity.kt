package com.clienttools.demo

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clienttools.sdk.ClientToolsSDK

class WebViewRedirectActivity : AppCompatActivity() {

    private val remoteOriginalUrl = "https://example.com"
    private val localOriginalUrl = "file:///android_asset/test_local.html"

    private lateinit var remoteWebView: WebView
    private lateinit var localWebView: WebView
    private lateinit var remoteUrlLabel: TextView
    private lateinit var localUrlLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_redirect)

        remoteWebView = findViewById(R.id.webview_redirect_remote)
        localWebView = findViewById(R.id.webview_redirect_local)
        remoteUrlLabel = findViewById(R.id.webview_redirect_url_remote)
        localUrlLabel = findViewById(R.id.webview_redirect_url_local)

        remoteWebView.webViewClient = WebViewClient()
        localWebView.webViewClient = WebViewClient()

        findViewById<TextView>(R.id.webview_redirect_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.webview_redirect_btn_reload).setOnClickListener { loadAll() }

        loadAll()
    }

    private fun loadAll() {
        val resolvedRemote = ClientToolsSDK.resolveRedirect(remoteOriginalUrl)
        val resolvedLocal = ClientToolsSDK.resolveRedirect(localOriginalUrl)

        remoteUrlLabel.text = resolvedRemote
        localUrlLabel.text = resolvedLocal

        remoteWebView.loadUrl(resolvedRemote)
        localWebView.loadUrl(resolvedLocal)
    }
}
