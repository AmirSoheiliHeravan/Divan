package com.divan.app.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Read-only contact access for linking counterparties (طرف‌حساب). */
object ContactsHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    data class ContactInfo(val name: String, val phone: String?)

    /** Resolve a picked contact URI into name + first phone. */
    fun resolve(context: Context, contactUri: Uri): ContactInfo? {
        if (!hasPermission(context)) return null
        return runCatching {
            var name: String? = null
            context.contentResolver.query(
                contactUri, arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(1)
                    if (c.getInt(2) > 0) {
                        val id = c.getString(0)
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id), null
                        )?.use { pc ->
                            if (pc.moveToFirst()) {
                                return ContactInfo(name ?: "", pc.getString(0))
                            }
                        }
                    }
                }
            }
            ContactInfo(name ?: "", null)
        }.getOrNull()
    }
}
