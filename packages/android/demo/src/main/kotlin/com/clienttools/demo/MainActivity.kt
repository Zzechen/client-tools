package com.clienttools.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class TestPage(
    val name: String,
    val description: String,
    val layoutResId: Int
)

class MainActivity : AppCompatActivity() {
    private val testPages = listOf(
        TestPage("Login Screen", "登录页面示例", R.layout.login_screen),
        TestPage("Form Screen", "表单页面示例", R.layout.form_screen)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    TestPageList(testPages) { page -> startTestPage(page) }
                }
            }
        }
    }
    
    private fun startTestPage(page: TestPage) {
        val intent = Intent(this, TestScreenHost::class.java).apply {
            putExtra("layoutResId", page.layoutResId)
            putExtra("pageName", page.name)
        }
        startActivity(intent)
    }
}

@Composable
fun TestPageList(
    pages: List<TestPage>,
    onPageClick: (TestPage) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(pages) { page ->
            TestPageListItem(page) { onPageClick(page) }
        }
    }
}

@Composable
fun TestPageListItem(page: TestPage, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(text = page.name, style = MaterialTheme.typography.headlineSmall)
        Text(text = page.description, style = MaterialTheme.typography.bodyMedium)
    }
}
