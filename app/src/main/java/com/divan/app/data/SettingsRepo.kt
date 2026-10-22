package com.divan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** App settings (DataStore). Every value is editable again from Settings screen. */
class SettingsRepo(private val store: DataStore<Preferences>) {

    private object K {
        val language = stringPreferencesKey("language")            // system|fa|en|de|tr
        val themeMode = stringPreferencesKey("theme_mode")        // system|light|dark
        val baseCurrency = stringPreferencesKey("base_currency")  // IRR default
        val persianDigits = booleanPreferencesKey("persian_digits")
        val wizardDone = booleanPreferencesKey("wizard_done")
        val familyName = stringPreferencesKey("family_name")
        val defaultMemberId = stringPreferencesKey("default_member_id")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val smsAutoConfirm = booleanPreferencesKey("sms_auto_confirm")
        val smsDefaultAccount = stringPreferencesKey("sms_default_account")
        val calendarEnabled = booleanPreferencesKey("calendar_enabled")
        val calendarId = longPreferencesKey("calendar_id")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val biometric = booleanPreferencesKey("biometric")
        val serverUrl = stringPreferencesKey("server_url")
        val serverToken = stringPreferencesKey("server_token")
        val deviceId = stringPreferencesKey("device_id")
        val firstDayOffset = longPreferencesKey("first_day_offset") // 0=Sat
        val warnThreshold = longPreferencesKey("warn_threshold")    // percent
        val fontScale = longPreferencesKey("font_scale")            // percent: 90|100|115|130
        val fiscalStartMonth = longPreferencesKey("fiscal_start_month") // 1..12 — شروع سال مالی
    }

    val languageCode: Flow<String> = store.data.map { it[K.language] ?: "system" }
    val themeMode: Flow<String> = store.data.map { it[K.themeMode] ?: "system" }
    val baseCurrency: Flow<String> = store.data.map { it[K.baseCurrency] ?: "IRR" }
    val persianDigits: Flow<Boolean> = store.data.map { it[K.persianDigits] ?: true }
    val wizardDone: Flow<Boolean> = store.data.map { it[K.wizardDone] ?: false }
    val familyName: Flow<String> = store.data.map { it[K.familyName] ?: "" }
    val defaultMemberId: Flow<String> = store.data.map { it[K.defaultMemberId] ?: "" }
    val smsEnabled: Flow<Boolean> = store.data.map { it[K.smsEnabled] ?: false }
    val smsAutoConfirm: Flow<Boolean> = store.data.map { it[K.smsAutoConfirm] ?: false }
    val smsDefaultAccount: Flow<String> = store.data.map { it[K.smsDefaultAccount] ?: "" }
    val calendarEnabled: Flow<Boolean> = store.data.map { it[K.calendarEnabled] ?: false }
    val calendarId: Flow<Long> = store.data.map { it[K.calendarId] ?: -1L }
    val biometric: Flow<Boolean> = store.data.map { it[K.biometric] ?: false }
    val serverUrl: Flow<String> = store.data.map { it[K.serverUrl] ?: "" }
    val serverToken: Flow<String> = store.data.map { it[K.serverToken] ?: "" }
    val deviceId: Flow<String> = store.data.map { it[K.deviceId] ?: "" }
    val warnThreshold: Flow<Int> = store.data.map { (it[K.warnThreshold] ?: 80L).toInt() }
    val fontScale: Flow<Float> = store.data.map { ((it[K.fontScale] ?: 100L) / 100f) }
    val fiscalStartMonth: Flow<Int> = store.data.map { ((it[K.fiscalStartMonth] ?: 1L)).toInt() }

    // one-shot helpers
    suspend fun baseCurrencyNow(): String = store.data.first()[K.baseCurrency] ?: "IRR"
    suspend fun persianDigitsNow(): Boolean = store.data.first()[K.persianDigits] ?: true
    suspend fun wizardDoneNow(): Boolean = store.data.first()[K.wizardDone] ?: false
    suspend fun defaultMemberNow(): String = store.data.first()[K.defaultMemberId] ?: ""
    suspend fun familyNameNow(): String = store.data.first()[K.familyName] ?: ""
    suspend fun smsAutoConfirmNow(): Boolean = store.data.first()[K.smsAutoConfirm] ?: false
    suspend fun smsDefaultAccountNow(): String = store.data.first()[K.smsDefaultAccount] ?: ""
    suspend fun calendarEnabledNow(): Boolean = store.data.first()[K.calendarEnabled] ?: false
    suspend fun calendarIdNow(): Long = store.data.first()[K.calendarId] ?: -1L
    suspend fun deviceIdNow(): String {
        val v = store.data.first()[K.deviceId]
        if (!v.isNullOrEmpty()) return v
        val newId = java.util.UUID.randomUUID().toString()
        store.edit { it[K.deviceId] = newId }
        return newId
    }

    suspend fun setLanguage(v: String) = store.edit { it[K.language] = v }
    suspend fun setThemeMode(v: String) = store.edit { it[K.themeMode] = v }
    suspend fun setBaseCurrency(v: String) = store.edit { it[K.baseCurrency] = v }
    suspend fun setPersianDigits(v: Boolean) = store.edit { it[K.persianDigits] = v }
    suspend fun setWizardDone(v: Boolean) = store.edit { it[K.wizardDone] = v }
    suspend fun setFamilyName(v: String) = store.edit { it[K.familyName] = v }
    suspend fun setDefaultMember(v: String) = store.edit { it[K.defaultMemberId] = v }
    suspend fun setSmsEnabled(v: Boolean) = store.edit { it[K.smsEnabled] = v }
    suspend fun setSmsAutoConfirm(v: Boolean) = store.edit { it[K.smsAutoConfirm] = v }
    suspend fun setSmsDefaultAccount(v: String) = store.edit { it[K.smsDefaultAccount] = v }
    suspend fun setCalendarEnabled(v: Boolean) = store.edit { it[K.calendarEnabled] = v }
    suspend fun setCalendarId(v: Long) = store.edit { it[K.calendarId] = v }
    suspend fun setBiometric(v: Boolean) = store.edit { it[K.biometric] = v }
    suspend fun setServerUrl(v: String) = store.edit { it[K.serverUrl] = v }
    suspend fun setServerToken(v: String) = store.edit { it[K.serverToken] = v }
    suspend fun setWarnThreshold(v: Int) = store.edit { it[K.warnThreshold] = v.toLong() }
    suspend fun setFontScale(v: Float) = store.edit { it[K.fontScale] = (v * 100).toLong() }
    suspend fun setFiscalStartMonth(v: Int) = store.edit { it[K.fiscalStartMonth] = v.toLong() }
    suspend fun fiscalStartMonthNow(): Int = store.data.first()[K.fiscalStartMonth]?.toInt() ?: 1

    suspend fun setPin(hash: String, salt: String) = store.edit {
        it[K.pinHash] = hash; it[K.pinSalt] = salt
    }

    suspend fun clearPin() = store.edit {
        it.remove(K.pinHash); it.remove(K.pinSalt)
    }

    suspend fun pinHashNow(): Pair<String, String>? {
        val d = store.data.first()
        val h = d[K.pinHash] ?: return null
        return h to (d[K.pinSalt] ?: "")
    }
}
