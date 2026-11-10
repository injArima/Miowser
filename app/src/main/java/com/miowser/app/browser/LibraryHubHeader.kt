package com.miowser.app.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.miowser.app.R
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryHubTopBar(
    title: @Composable () -> Unit,
    branded: Boolean = true,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            if (navigation != null) {
                navigation()
            } else if (branded) {
                Surface(
                    modifier = Modifier.padding(start = UiSpacing.l, end = UiSpacing.m),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(Modifier.size(UiControlSize.compact), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_miowser_mark), contentDescription = null,
                            modifier = Modifier.size(UiControlSize.compact))
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryHubTabs(deviceSelected: Boolean, onSelectDevice: (Boolean) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = if (deviceSelected) 0 else 1,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Tab(selected = deviceSelected, onClick = { onSelectDevice(true) },
            modifier = Modifier.testTag("hub_destination_device"), text = { Text("Device Videos") })
        Tab(selected = !deviceSelected, onClick = { onSelectDevice(false) },
            modifier = Modifier.testTag("hub_destination_saved"), text = { Text("Miowser Hub") })
    }
}
