package com.divan.app.core.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.divan.app.core.db.AppDatabase
import com.divan.app.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Two-way helpers for the OS calendar:
 *  – writes due-dates (installments / checks / recurring) as calendar events
 *  – can list device calendars for the user to pick a target calendar.
 *  Stored event ids prevent duplicates; deleting a record removes its event. */
class CalendarSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepo,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    data class CalInfo(val id: Long, val name: String, val account: String)

    fun listCalendars(): List<CalInfo> {
        if (!hasPermission()) return emptyList()
        val out = mutableListOf<CalInfo>()
        runCatching {
            val uri = CalendarContract.Calendars.CONTENT_URI
            val proj = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
            )
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += CalInfo(c.getLong(0), c.getString(1) ?: "?", c.getString(2) ?: "?")
                }
            }
        }
        return out
    }

    private suspend fun calendarId(): Long = settings.calendarIdNow()

    private fun upsertEvent(title: String, desc: String, at: Long, existing: Long?): Long? {
        if (!hasPermission()) return existing
        val resolver = context.contentResolver
        return runCatching {
            if (existing != null && existing > 0) {
                resolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), null, null
                )
            }
            val cid = calendarId()
            if (cid <= 0) return@runCatching null
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cid)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, desc)
                put(CalendarContract.Events.DTSTART, at)
                put(CalendarContract.Events.DTEND, at + 60 * 60 * 1000)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    private fun deleteEvent(existing: Long?) {
        if (existing == null || existing <= 0 || !hasPermission()) return
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), null, null
            )
        }
    }

    /** Sync all future due-dates into the selected calendar. */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        if (!settings.calendarEnabledNow() || !hasPermission()) return@withContext
        val now = System.currentTimeMillis()
        val horizon = now + 180L * 86400000

        // installments
        db.loanDao().unpaidOnce().filter { it.dueDate in now..horizon }.forEach { inst ->
            if (inst.calendarEventId == null) {
                val loan = db.loanDao().byId(inst.loanId)
                val ev = upsertEvent(
                    "قسط ${inst.seq} – ${loan?.title ?: "وام"}",
                    "سررسید قسط شماره ${inst.seq}", inst.dueDate, inst.calendarEventId
                )
                if (ev != null) db.loanDao().setInstallmentEvent(inst.id, ev)
            }
        }

        // checks
        db.checkDao().dueBetween(now, horizon).forEach { chk ->
            if (chk.calendarEventId == null) {
                val ev = upsertEvent(
                    if (chk.direction == com.divan.app.core.db.entity.CheckDirection.ISSUED) "سررسید چک پرداختی" else "سررسید چک دریافتی",
                    "مبلغ: ${chk.amountMinor}", chk.dueDate, chk.calendarEventId
                )
                if (ev != null) db.checkDao().setEvent(chk.id, ev)
            }
        }
    }

    suspend fun removeInstallmentEvent(installmentId: String) {
        val inst = db.loanDao().installmentById(installmentId) ?: return
        deleteEvent(inst.calendarEventId)
        db.loanDao().setInstallmentEvent(installmentId, null)
    }

    suspend fun removeCheckEvent(checkId: String) {
        val c = db.checkDao().byId(checkId) ?: return
        deleteEvent(c.calendarEventId)
        db.checkDao().setEvent(checkId, null)
    }
}
