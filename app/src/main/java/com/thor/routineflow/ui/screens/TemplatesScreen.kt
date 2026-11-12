package com.thor.routineflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.catalog.RoutineTemplates
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.Routine
import com.thor.routineflow.ui.components.RoutineIcons
import com.thor.routineflow.ui.components.actionSummaryText
import com.thor.routineflow.ui.components.triggerSummaryText
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    container: AppContainer,
    onOpenEditor: (String) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory(container))
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Templates") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Ready-made routines. Tapping “Use this template” copies it " +
                        "into your routines so you can fill in your own details — " +
                        "the template itself never changes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(RoutineTemplates.all, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onUse = {
                        scope.launch {
                            val newId = viewModel.useTemplate(template)
                            onOpenEditor(newId)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(template: Routine, onUse: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(template.color).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    RoutineIcons.IconFor(
                        iconKey = template.icon,
                        modifier = Modifier.size(24.dp),
                        tint = Color(template.color)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Template",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "When: " + template.triggers.joinToString(" + ") {
                    triggerSummaryText(it, context)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (template.conditions.isNotEmpty()) {
                Text(
                    text = "Only if: " + template.conditions.joinToString(" + ") {
                        com.thor.routineflow.ui.components.conditionSummaryText(it, context)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Do: " + template.actions.joinToString(" · ") {
                    actionSummaryText(it, context)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
                Text("Use this template")
            }
        }
    }
}
