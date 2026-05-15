package com.clienttools.demo

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clienttools.demo.model.UserInfo

class UserInfoActivity : AppCompatActivity() {

    companion object {
        const val KEY_USER = "user_info"
        const val KEY_TOKEN = "token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        @Suppress("DEPRECATION")
        val user = intent.getParcelableExtra<UserInfo>(KEY_USER)
            ?: run { finish(); return }
        val token = intent.getStringExtra(KEY_TOKEN) ?: ""

        findViewById<TextView>(R.id.user_info_avatar).text =
            user.name.firstOrNull()?.toString() ?: "?"
        findViewById<TextView>(R.id.user_info_name).text = user.name
        findViewById<TextView>(R.id.user_info_phone).text = user.phone.ifEmpty { "未绑定" }
        findViewById<TextView>(R.id.user_info_email).text = user.email.ifEmpty { "未绑定" }
        findViewById<TextView>(R.id.user_info_token).text =
            if (token.length > 20) "${token.take(20)}..." else token

        findViewById<FrameLayout>(R.id.user_info_btn_logout).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }
}
