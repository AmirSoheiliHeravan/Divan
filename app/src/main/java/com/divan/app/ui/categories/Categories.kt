package com.divan.app.ui.categories

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.CategoryKind
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CategoriesScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val cats by ServiceLocator.categories.all().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    var selectedRoot by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "دسته‌بندی‌ها") { nav.popBackStack() }
        Spacer(Modifier.height(6.dp))
        Text(
            "دسته‌های سیستمی را می‌توانید مخفی کنید؛ دسته سفارشی بی‌نهایت اضافه کنید.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SectionCard(title = "افزودن دسته جدید") {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text("نام دسته") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text("دسته والد (اختیاری)", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipRow(
                    options = cats.filter { it.parentId == null }.map { it.id to it.name },
                    selected = selectedRoot, onSelect = { selectedRoot = it },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = newName.isNotBlank(),
                onClick = {
                    scope.launch {
                        val ts = System.currentTimeMillis()
                        ServiceLocator.categories.save(
                            Category(
                                id = UUID.randomUUID().toString(),
                                parentId = selectedRoot,
                                name = newName.trim(),
                                nameEn = newName.trim(),
                                kind = CategoryKind.EXPENSE,
                                icon = "🏷", isSystem = false,
                                createdAt = ts, updatedAt = ts,
                            ), isNew = true,
                        )
                        newName = ""
                    }
                },
            ) { Text("افزودن") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cats.filter { it.parentId == null }, key = { it.id }) { root ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(root.icon, fontSize = 16.sp)
                        Spacer(Modifier.padding(2.dp))
                        Text(root.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = root.active,
                            onCheckedChange = { on -> scope.launch { ServiceLocator.categories.setActive(root.id, on) } },
                        )
                        if (!root.isSystem) {
                            TextButton(onClick = { scope.launch { ServiceLocator.categories.delete(root.id) } }) { Text("حذف") }
                        }
                    }
                    cats.filter { it.parentId == root.id }.forEach { sub ->
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("• ${sub.name}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Checkbox(
                                checked = sub.active,
                                onCheckedChange = { on -> scope.launch { ServiceLocator.categories.setActive(sub.id, on) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipRow(options: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("—") },
        )
        options.forEach { (id, label) ->
            androidx.compose.material3.FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label, maxLines = 1, fontSize = 11.sp) },
            )
        }
    }
}
