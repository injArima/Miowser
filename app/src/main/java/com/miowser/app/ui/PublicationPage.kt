package com.miowser.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal enum class PublicationPage(val title: String, val asset: String) {
    PRIVACY("Privacy policy", "privacy_policy.txt"),
    NOTICES("Third-party notices", "licenses/THIRD_PARTY_NOTICES.txt")
}

@Composable
internal fun PublicationPageContent(page: PublicationPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(page) {
        context.assets.open(page.asset).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText().trim().split(Regex("\\n\\s*\\n"))
        }
    }
    BackHandler(onBack = onBack)
    SimplePageScaffold(title = page.title, onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)) {
            paragraphs.forEach { paragraph ->
                val heading = paragraph.startsWith("# ")
                Text(
                    text = if (heading) paragraph.removePrefix("# ") else paragraph,
                    style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (heading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (heading) 6.dp else 16.dp)
                )
            }
        }
    }
}
