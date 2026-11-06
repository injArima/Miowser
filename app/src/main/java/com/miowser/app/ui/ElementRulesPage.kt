package com.miowser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.ElementRuleAction
import com.miowser.app.adblock.SiteElementRule
import com.miowser.app.ui.theme.UiSpacing

@Composable
fun ElementRulesPage(
    rules: List<SiteElementRule>,
    onActionChanged: (SiteElementRule, ElementRuleAction) -> Unit,
    onRevert: (SiteElementRule) -> Unit,
    onRevertSite: (String) -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Blocked page elements", onBack = onBack) { modifier ->
        if (rules.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No page elements changed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use Block element from the browser menu's Quick Access section to hide or delete part of a website.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = UiSpacing.s)
                )
            }
        } else {
            val groups = rules.sortedWith(compareBy<SiteElementRule> { it.host }.thenBy { it.createdAt })
                .groupBy(SiteElementRule::host).toList()
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.comfortable)
            ) {
                item {
                    Text(
                        "Review changes created with Block element. Reverting a deleted element reloads matching open pages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(groups, key = { it.first }) { (host, siteRules) ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(vertical = UiSpacing.s)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(host, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { onRevertSite(host) }) {
                                    Text("Revert all", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            siteRules.forEachIndexed { index, rule ->
                                if (index > 0) HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = UiSpacing.l),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                ElementRuleManagementRow(rule, onActionChanged, onRevert)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(UiSpacing.s)) }
            }
        }
    }
}

@Composable
private fun ElementRuleManagementRow(
    rule: SiteElementRule,
    onActionChanged: (SiteElementRule, ElementRuleAction) -> Unit,
    onRevert: (SiteElementRule) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l, vertical = UiSpacing.m),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.s)
    ) {
        Text(
            rule.selector,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            ElementRuleAction.entries.forEach { action ->
                FilterChip(
                    selected = rule.action == action,
                    onClick = { onActionChanged(rule, action) },
                    label = { Text(if (action == ElementRuleAction.HIDE) "Hide" else "Delete") }
                )
            }
            TextButton(onClick = { onRevert(rule) }) {
                Text("Revert", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
