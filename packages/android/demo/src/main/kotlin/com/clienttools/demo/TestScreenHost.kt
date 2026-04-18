package com.clienttools.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TestScreenHost : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutResId = intent.getIntExtra("layoutResId", 0)
        if (layoutResId != 0) {
            setContentView(layoutResId)
        }
    }
}
