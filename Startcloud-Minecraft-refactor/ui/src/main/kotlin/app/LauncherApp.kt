package cn.starcloud.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LauncherApp(
    state: LauncherState,
    actions: LauncherActions,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                ) {
                    Text("Starcloud Launcher", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.padding(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { actions.refresh() }) { Text("刷新版本列表") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { actions.launch() },
                            enabled = state.selectedVersion != null && !state.isLaunching,
                        ) {
                            Text(if (state.isLaunching) "启动中…" else "启动游戏")
                        }
                    }

                    Spacer(Modifier.padding(12.dp))

                    if (state.downloadProgress in 0f..1f && state.downloadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.padding(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.versions) { v ->
                            val selected = v == state.selectedVersion
                            Text(
                                text = if (selected) "▶ $v" else "   $v",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }

                    state.message?.let {
                        Spacer(Modifier.padding(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}