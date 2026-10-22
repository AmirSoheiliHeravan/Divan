package com.divan.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** یک قلم انتخاب‌شده در تراکنش */
data class SelItem(
    val id: String,
    val name: String,
    val priceMinor: Long,
    val qty: Int,
    val groupId: String,
    val groupName: String,
)

fun selItemsToJson(sel: List<SelItem>): String {
    val arr = JSONArray()
    sel.forEach { s ->
        arr.put(JSONObject().apply {
            put("id", s.id)
            put("name", s.name)
            put("priceMinor", s.priceMinor)
            put("qty", s.qty)
            put("groupId", s.groupId)
            put("groupName", s.groupName)
        })
    }
    return arr.toString()
}

fun selItemsFromJson(json: String?): List<SelItem> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SelItem(
                id = o.optString("id"), name = o.optString("name"),
                priceMinor = o.optLong("priceMinor"), qty = o.optInt("qty", 1),
                groupId = o.optString("groupId"), groupName = o.optString("groupName"),
            )
        }
    }.getOrDefault(emptyList())
}

/**
 * پنجره اقلام ویزاردی (۲ مرحله‌ای): مجموعه‌ها ← اقلام
 * انتخاب قلم با تعداد، جست‌وجو، افزودن/حذف آیتم در هر مجموعه — استفاده در همه‌جای برنامه.
 * onConfirm(json, groupName) — نام مجموعه باز‌شده/انتخاب‌شده برای استفاده به‌عنوان دسته تراکنش.
 */
@Composable
fun ItemsPickerDialog(
    initialJson: String?,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val groups by ServiceLocator.itemsDao.groups().collectAsState(initial = emptyList())
    val allItems by ServiceLocator.itemsDao.items().collectAsState(initial = emptyList())

    var sel by remember { mutableStateOf(selItemsFromJson(initialJson)) }
    var stepGid by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }

    val pri = MaterialTheme.colorScheme.primary
    val priContainer = MaterialTheme.colorScheme.primaryContainer
    val onPriContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val err = Color(0xFFE2574C)
    val gold = Color(0xFFD9B25F)

    fun toggle(gid: String, gname: String, it: com.divan.app.core.db.entity.ItemEntry) {
        val ex = sel.firstOrNull { s -> s.id == it.id }
        sel = if (ex != null) sel.filter { s -> s.id != it.id }
        else sel + SelItem(it.id, it.name, it.priceMinor, 1, gid, gname)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // دسته = مجموعه اولین قلم انتخابی، وگرنه مجموعه باز‌شده
                val gname = sel.firstOrNull()?.groupName
                    ?: groups.firstOrNull { it.id == stepGid }?.name
                onConfirm(selItemsToJson(sel), gname)
                onDismiss()
            }) {
                Text("تأیید (${sel.size} قلم)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        title = {
            Text(
                if (stepGid == null) "انتخاب اقلام" else "اقلام «${groups.firstOrNull { it.id == stepGid }?.name ?: ""}»",
                fontSize = 15.sp,
            )
        },
        text = {
            Column {
                // نشانگر ویزارد
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("مجموعه‌ها", "اقلام").forEachIndexed { i, s ->
                        val on = (i == 0) == (stepGid == null)
                        Text(
                            "${i + 1} $s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) pri else dim,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (stepGid == null) {
                    // ===== مرحله ۱: مجموعه‌ها + جست‌وجوی سراسری =====
                    OutlinedTextField(
                        value = q, onValueChange = { q = it },
                        label = { Text("جست‌وجوی آیتم در همه مجموعه‌ها…") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        if (q.isBlank()) {
                            items(groups, key = { it.id }) { g ->
                                val count = allItems.count { it.groupId == g.id }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                        .clickable { stepGid = g.id; q = "" }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(12.dp).background(runCatching { Color(android.graphics.Color.parseColor(g.color)) }.getOrDefault(pri), CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("$count قلم", fontSize = 11.sp, color = dim)
                                    }
                                    Text("‹", color = dim, fontSize = 18.sp)
                                    IconButtonSmall {
                                        scope.launch {
                                            ServiceLocator.itemsDao.softDeleteItemsOfGroup(g.id)
                                            ServiceLocator.itemsDao.deleteGroup(g.id)
                                        }
                                    }
                                }
                            }
                        } else {
                            val found = allItems.filter { it.name.contains(q.trim()) }
                            items(found, key = { it.id }) { it2 ->
                                val g = groups.firstOrNull { g2 -> g2.id == it2.groupId }
                                ItemRow(it2, g?.name ?: "", sel, gold, err, onToggle = { toggle(it2.groupId, g?.name ?: "", it2) }, onQty = { dq ->
                                    sel = sel.map { s -> if (s.id == it2.id) s.copy(qty = (s.qty + dq).coerceIn(1, 99)) else s }
                                })
                            }
                        }
                        // افزودن مجموعه جدید
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newGroupName, onValueChange = { newGroupName = it },
                                    label = { Text("نام مجموعه جدید…") },
                                    modifier = Modifier.weight(1f), singleLine = true,
                                )
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = {
                                    if (newGroupName.isNotBlank()) {
                                        scope.launch {
                                            ServiceLocator.itemsDao.insertGroup(
                                                com.divan.app.core.db.entity.ItemGroup(
                                                    id = "g" + System.currentTimeMillis(),
                                                    name = newGroupName.trim(),
                                                    sort = groups.size,
                                                )
                                            )
                                            newGroupName = ""
                                        }
                                    }
                                }) { Icon(Icons.Filled.Add, null); Text("مجموعه") }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    // ===== مرحله ۲: آیتم‌های مجموعه =====
                    TextButton(onClick = { stepGid = null }) { Text("‹ همه مجموعه‌ها") }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        val gid = stepGid ?: ""
                        val gname = groups.firstOrNull { it.id == gid }?.name ?: ""
                        val itemsOf = allItems.filter { it.groupId == gid }
                        items(itemsOf, key = { it.id }) { it2 ->
                            ItemRow(it2, gname, sel, gold, err, onToggle = { toggle(gid, gname, it2) }, onQty = { dq ->
                                sel = sel.map { s -> if (s.id == it2.id) s.copy(qty = (s.qty + dq).coerceIn(1, 99)) else s }
                            }, onDelete = {
                                scope.launch { ServiceLocator.itemsDao.softDeleteItem(it2.id) }
                                sel = sel.filter { s -> s.id != it2.id }
                            })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("افزودن آیتم به این مجموعه:", style = MaterialTheme.typography.labelMedium, color = dim)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newItemName, onValueChange = { newItemName = it },
                            label = { Text("نام آیتم") },
                            modifier = Modifier.weight(1f), singleLine = true,
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = newItemPrice, onValueChange = { s -> newItemPrice = s.filter { it.isDigit() || it in "۰۱۲۳۴۵۶۷۸۹" } },
                            label = { Text("قیمت") },
                            modifier = Modifier.width(110.dp), singleLine = true,
                        )
                    }
                    TextButton(onClick = {
                        val price = newItemPrice.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        if (newItemName.isNotBlank() && price > 0) {
                            scope.launch {
                                ServiceLocator.itemsDao.insertItem(
                                    com.divan.app.core.db.entity.ItemEntry(
                                        id = "i" + System.currentTimeMillis(),
                                        groupId = stepGid ?: return@launch,
                                        name = newItemName.trim(),
                                        priceMinor = price,
                                    )
                                )
                            }
                            newItemName = ""; newItemPrice = ""
                        }
                    }) { Icon(Icons.Filled.Add, null); Text("افزودن آیتم") }
                }
            }
        },
    )
}

@Composable
private fun IconButtonSmall(onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(Color(0x14E2574C), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = "حذف", tint = Color(0xFFE2574C), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ItemRow(
    it: com.divan.app.core.db.entity.ItemEntry,
    groupName: String,
    sel: List<SelItem>,
    gold: Color,
    err: Color,
    onToggle: () -> Unit,
    onQty: (Int) -> Unit,
    onDelete: () -> Unit = {},
) {
    val s = sel.firstOrNull { x -> x.id == it.id }
    val pri = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (s != null) gold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // دایره انتخاب
        Box(
            Modifier
                .size(26.dp)
                .background(if (s != null) pri else Color.Transparent, CircleShape)
                .border(1.5.dp, if (s != null) pri else dim, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (s != null) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(it.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${com.divan.app.ui.util.Jalali.faNum((it.priceMinor / 1000).toInt())}٬۰۰۰ ریال", fontSize = 10.5.sp, color = dim)
        }
        if (s != null) {
            TextButton(onClick = { onQty(-1) }, modifier = Modifier.width(30.dp)) { Text("−", fontWeight = FontWeight.Bold) }
            Text(com.divan.app.ui.util.Jalali.faNum(s.qty), fontWeight = FontWeight.Bold, color = pri)
            TextButton(onClick = { onQty(1) }, modifier = Modifier.width(30.dp)) { Text("+", fontWeight = FontWeight.Bold) }
        }
        IconButtonSmall(onClick = onDelete)
    }
}
