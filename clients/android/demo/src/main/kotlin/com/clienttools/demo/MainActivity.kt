package com.clienttools.demo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val pages by lazy {
        listOf(
            Page("Login Screen") { startActivity(Intent(this, LoginActivity::class.java)) },
            Page("WebView 重定向测试") { startActivity(Intent(this, WebViewRedirectActivity::class.java)) },
            Page("Verify Code") { startActivity(Intent(this, VerifyCodeActivity::class.java)) },
            Page("Agent Test") { startActivity(Intent(this, AgentTestActivity::class.java)) },
            Page("User Info (Demo)") {
                val demoUser = com.clienttools.demo.model.UserInfo(
                    id = "demo",
                    name = "Demo User",
                    phone = "138****8000",
                    email = "demo@pulse.app",
                    avatar_url = ""
                )
                startActivity(Intent(this, UserInfoActivity::class.java).apply {
                    putExtra(UserInfoActivity.KEY_USER, demoUser)
                    putExtra(UserInfoActivity.KEY_TOKEN, "demo_token_12345")
                })
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<RecyclerView>(R.id.main_page_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = PageAdapter(pages)
    }

    private inner class PageAdapter(private val items: List<Page>) :
        RecyclerView.Adapter<PageAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.item_page_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page_entry, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = items[position]
            holder.name.text = page.name
            holder.itemView.setOnClickListener { page.action() }
        }

        override fun getItemCount() = items.size
    }

    private data class Page(val name: String, val action: () -> Unit)
}
