package com.divan.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divan.app.ui.util.Jalali

/**
 * تقویم شمسی لمس‌کردنی — در همه جای برنامه که تاریخ وجود دارد باز می‌شود.
 * ناوبری ماه و سال + دکمه «امروز» + هایلایت امروز و روز انتخابی؛ جمعه‌ها قرمز.
 */
@Composable
fun JalaliDatePickerDialog(
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var view by remember { mutableStateOf(Jalali.fromMillis(initial)) }
    var sel by remember { mutableStateOf(Jalali.fromMillis(initial)) }

    val today = remember { Jalali.fromMillis(System.currentTimeMillis()) }
    val pri = MaterialTheme.colorScheme.primary
    val gold = Color(0xFFD9B25F)
    val onGold = Color(0xFF3A2E10)
    val red = Color(0xFFE2574C)

    val len = Jalali.monthLen(view.jy, view.jm)
    val first = Jalali.weekday(Jalali.JDate(view.jy, view.jm, 1))
    val days: List<Int?> = List(first) { null } + (1..len).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(Jalali.toMillis(sel))
                onDismiss()
            }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        title = { Text("انتخاب تاریخ") },
        text = {
            Column {
                // ناوبری ماه / سال
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { view = view.copy(jy = view.jy - 1) }) { Text("»") }
                        TextButton(onClick = {
                            view = if (view.jm == 1) view.copy(jy = view.jy - 1, jm = 12) else view.copy(jm = view.jm - 1)
                        }) { Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${Jalali.MONTHS[view.jm - 1]} ${Jalali.faNum(view.jy)}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("تقویم هجری شمسی", style = MaterialTheme.typography.labelSmall, color = gold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            view = if (view.jm == 12) view.copy(jy = view.jy + 1, jm = 1) else view.copy(jm = view.jm + 1)
                        }) { Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { view = view.copy(jy = view.jy + 1) }) { Text("«") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // روزهای هفته
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { i, w ->
                        Text(w, style = MaterialTheme.typography.labelSmall, color = if (i == 6) red else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // شبکه روزها
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                ) {
                    items(days) { d ->
                        if (d == null) {
                            Box(Modifier.size(40.dp))
                        } else {
                            val jd = Jalali.JDate(view.jy, view.jm, d)
                            val isSel = jd == sel
                            val isToday = jd == today
                            val isFriday = Jalali.weekday(jd) == 6
                            Box(
                                Modifier
                                    .padding(2.dp)
                                    .size(40.dp)
                                    .background(if (isSel) gold else Color.Transparent, RoundedCornerShape(10.dp))
                                    .then(
                                        if (isToday && !isSel) Modifier.border(1.5.dp, gold, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable { sel = jd },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    Jalali.faNum(d),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSel -> onGold
                                        isToday -> gold
                                        isFriday -> red
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("امروز: ${Jalali.fmt(System.currentTimeMillis())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        sel = today
                        view = today
                        onPick(Jalali.toMillis(today))
                        onDismiss()
                    }) { Text("رفتن به امروز") }
                }
            }
        },
    )
}
