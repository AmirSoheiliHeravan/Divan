package com.divan.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.MemberRole
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import kotlinx.coroutines.launch

@Composable
fun FamilyScreen(nav: NavHostController) {
    val members by ServiceLocator.family.members().collectAsState(initial = emptyList())
    val familyName by ServiceLocator.settings.familyName.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.MEMBER) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "خانواده: ${familyName.ifBlank { "خانواده من" }}") { nav.popBackStack() }
        Text(
            "هر عضو نقش و مجوز دارد. در نسخه آفلاین نقش‌ها محلی ذخیره می‌شوند و پس از اتصال سرور، مجوزها در سمت سرور اعمال خواهند شد.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SectionCard(title = "افزودن عضو") {
            com.divan.app.ui.common.Field("نام عضو", newName) { newName = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    MemberRole.MEMBER to "عضو",
                    MemberRole.FINANCE to "مدیر مالی",
                    MemberRole.ADMIN to "مدیر",
                    MemberRole.READ_ONLY to "فقط‌خواندنی",
                    MemberRole.CHILD to "کودک",
                ).forEach { (r, lbl) ->
                    androidx.compose.material3.FilterChip(
                        selected = role == r, onClick = { role = r }, label = { Text(lbl, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = newName.isNotBlank(),
                onClick = {
                    scope.launch {
                        ServiceLocator.family.addMember(newName.trim(), role, "#2E7D5B")
                        newName = ""
                    }
                },
            ) { Text("افزودن") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(members, key = { it.id }) { m ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text(roleLabel(m.role), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { scope.launch { ServiceLocator.family.deleteMember(m.id) } }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(r: MemberRole): String = when (r) {
    MemberRole.OWNER -> "مالک فضای خانوادگی"
    MemberRole.ADMIN -> "مدیر اصلی"
    MemberRole.FINANCE -> "مدیر مالی"
    MemberRole.MEMBER -> "عضو"
    MemberRole.READ_ONLY -> "فقط‌خواندنی"
    MemberRole.CHILD -> "عضو محدود"
    MemberRole.GUEST -> "مهمان"
}
