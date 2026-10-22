package com.divan.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Generic single-select dropdown for entities. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EntityDropdown(
    label: String,
    items: List<T>,
    selectedId: String?,
    getText: (T) -> String,
    getId: (T) -> String = { "" },
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = items.firstOrNull { getId(it).isNotEmpty() && getId(it) == selectedId }?.let(getText)
        ?: items.firstOrNull { getId(it) == selectedId }?.let(getText)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(getText(item), maxLines = 1) },
                    onClick = { onSelect(item); expanded = false },
                )
            }
        }
    }
}

/** Category tree picker (roots then children). */
@Composable
fun CategoryPickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val cats by ServiceLocator.categories.allActive().collectAsState(initial = emptyList())
    var openRoot by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب دسته‌بندی") },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                val roots = cats.filter { it.parentId == null }
                if (openRoot == null) {
                    items(roots, key = { it.id }) { root ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openRoot = root.id }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(root.icon, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(root.name)
                            Spacer(Modifier.weight(1f))
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                } else {
                    item {
                        TextButton(onClick = { openRoot = null }) { Text("‹ بازگشت") }
                    }
                    val root = roots.firstOrNull { it.id == openRoot }
                    item(key = "root") {
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(root?.id ?: "") }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✔ ${root?.name ?: ""}", style = MaterialTheme.typography.titleMedium)
                        }
                        HorizontalDivider()
                    }
                    items(cats.filter { it.parentId == openRoot }, key = { it.id }) { sub ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(sub.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sub.name)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

/** Counterparty picker with quick-add. */
@Composable
fun CounterpartyPickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val cps by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب طرف‌حساب") },
        text = {
            Column {
                if (cps.isEmpty()) Text("هنوز طرف‌حسابی ندارید", fontSize = 13.sp)
                LazyColumn(Modifier.height(240.dp)) {
                    items(cps, key = { it.id }) { cp ->
                        Text(
                            cp.name,
                            Modifier.fillMaxWidth().clickable { onSelect(cp.id) }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("افزودن سریع") }, singleLine = true,
                )
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val cp = Counterparty(
                                id = java.util.UUID.randomUUID().toString(),
                                name = newName.trim(),
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                            ServiceLocator.counterparties.save(cp, isNew = true)
                            onSelect(cp.id)
                        }
                    }
                }) { Text("+ افزودن و انتخاب") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
