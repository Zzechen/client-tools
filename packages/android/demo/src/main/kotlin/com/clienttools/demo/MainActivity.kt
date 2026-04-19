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

    private val pages = listOf(
        Page("Login Screen") { startActivity(Intent(this, LoginActivity::class.java)) }
    )

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

    private data class Page(val name: String, val action: MainActivity.() -> Unit)
}
