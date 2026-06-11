package com.clienttools.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

class AgentTestActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var delayedView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_test)
        title = "Agent Test"

        statusView = findViewById(R.id.test_status)
        delayedView = findViewById(R.id.test_delayed_view)

        // 清空输入框
        findViewById<Button>(R.id.test_btn_clear).setOnClickListener {
            findViewById<EditText>(R.id.test_input).setText("")
            updateStatus("clear_input")
        }

        // 长按
        findViewById<Button>(R.id.test_btn_long_press).setOnLongClickListener {
            updateStatus("long_press")
            true
        }

        // 双击
        val doubleTapBtn = findViewById<Button>(R.id.test_btn_double_tap)
        var lastTapTime = 0L
        doubleTapBtn.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 300) {
                updateStatus("double_tap")
            }
            lastTapTime = now
        }

        // 填充滑动列表（20项）
        val scrollContent = findViewById<LinearLayout>(R.id.test_scroll_content)
        val itemIds = listOf(
            R.id.test_item_0, R.id.test_item_1, R.id.test_item_2, R.id.test_item_3,
            R.id.test_item_4, R.id.test_item_5, R.id.test_item_6, R.id.test_item_7,
            R.id.test_item_8, R.id.test_item_9, R.id.test_item_10, R.id.test_item_11,
            R.id.test_item_12, R.id.test_item_13, R.id.test_item_14, R.id.test_item_15,
            R.id.test_item_16, R.id.test_item_17, R.id.test_item_18, R.id.test_item_19
        )
        for (i in 0 until 20) {
            val item = TextView(this).apply {
                id = itemIds[i]
                text = "Item $i"
                textSize = 14f
                setPadding(16, 24, 16, 24)
                setTextColor(0xFFCCCCCC.toInt())
            }
            scrollContent.addView(item)
        }

        // 延迟出现
        findViewById<Button>(R.id.test_btn_trigger_delay).setOnClickListener {
            updateStatus("trigger_delay")
            delayedView.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                delayedView.visibility = View.VISIBLE
                updateStatus("delayed_view_visible")
            }, 2000)
        }
    }

    private fun updateStatus(action: String) {
        statusView.text = "status: $action"
    }
}
