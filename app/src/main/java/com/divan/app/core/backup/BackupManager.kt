package com.divan.app.core.backup

import android.content.Context
import com.divan.app.core.db.AppDatabase
import com.divan.app.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Full JSON backup (optionally AES-encrypted with a user password) + CSV export.
 *  Backup covers: accounts, categories, counterparties, transactions, loans,
 *  installments, debts, checks, budgets, recurring, products, assets. */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepo,
) {

    @Serializable
    data class BackupData(
        val version: Int = 1,
        val exportedAt: Long,
        val family: String,
        val members: List<String>,
        val accounts: List<String>,
        val categories: List<String>,
        val counterparties: List<String>,
        val transactions: List<String>,
        val loans: List<String>,
        val installments: List<String>,
        val debts: List<String>,
        val checks: List<String>,
        val budgets: List<String>,
        val recurring: List<String>,
        val products: List<String>,
        val assets: List<String>,
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun buildBackup(): BackupData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        BackupData(
            exportedAt = now,
            family = db.familyDao().firstOnce()?.name ?: "",
            members = db.memberDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Member.serializer(), it) },
            accounts = db.accountDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Account.serializer(), it) },
            categories = db.categoryDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Category.serializer(), it) },
            counterparties = db.counterpartyDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Counterparty.serializer(), it) },
            transactions = db.txnDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Txn.serializer(), it) },
            loans = db.loanDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Loan.serializer(), it) },
            installments = db.loanDao().allInstallmentsOnce().map { json.encodeToString(com.divan.app.core.db.entity.Installment.serializer(), it) },
            debts = db.debtDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.DebtCredit.serializer(), it) },
            checks = db.checkDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.CheckItem.serializer(), it) },
            budgets = db.budgetDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Budget.serializer(), it) },
            recurring = db.recurringDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Recurring.serializer(), it) },
            products = db.productDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.Product.serializer(), it) },
            assets = db.assetDao().allOnce().map { json.encodeToString(com.divan.app.core.db.entity.AssetItem.serializer(), it) },
        )
    }

    suspend fun serializeBackup(): String = json.encodeToString(BackupData.serializer(), buildBackup())

    fun backupFileName(): String {
        val f = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "divan_backup_${f.format(Date())}.json"
    }

    /** AES-256-GCM encryption with password (portable across devices). */
    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = "DV1:" + Base64.encode(salt) + ":" + Base64.encode(iv) + ":" + Base64.encode(ct)
        return out
    }

    fun decrypt(data: String, password: String): String? {
        return runCatching {
            val parts = data.trim().split(":")
            // هم فرمت جدید (DV1) و هم فایل‌های قدیمی‌تر (KM1) خوانده می‌شوند
            if (parts.size != 4 || (parts[0] != "DV1" && parts[0] != "KM1")) return@runCatching null
            val salt = Base64.decode(parts[1])
            val iv = Base64.decode(parts[2])
            val ct = Base64.decode(parts[3])
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private object Base64 {
        fun encode(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
        fun decode(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
    }

    /** Restore: clears and re-inserts entities from a backup string. */
    suspend fun restore(data: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val b = json.decodeFromString(BackupData.serializer(), data)
            b.members.forEach {
                db.memberDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Member.serializer(), it))
            }
            b.accounts.forEach {
                db.accountDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Account.serializer(), it))
            }
            b.categories.forEach {
                db.categoryDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Category.serializer(), it))
            }
            b.transactions.forEach {
                db.txnDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Txn.serializer(), it))
            }
            b.counterparties.forEach {
                db.counterpartyDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Counterparty.serializer(), it))
            }
            b.loans.forEach {
                db.loanDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Loan.serializer(), it))
            }
            b.installments.forEach {
                db.loanDao().insertInstallments(listOf(json.decodeFromString(com.divan.app.core.db.entity.Installment.serializer(), it)))
            }
            b.debts.forEach {
                db.debtDao().insert(json.decodeFromString(com.divan.app.core.db.entity.DebtCredit.serializer(), it))
            }
            b.checks.forEach {
                db.checkDao().insert(json.decodeFromString(com.divan.app.core.db.entity.CheckItem.serializer(), it))
            }
            b.budgets.forEach {
                db.budgetDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Budget.serializer(), it))
            }
            b.recurring.forEach {
                db.recurringDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Recurring.serializer(), it))
            }
            b.products.forEach {
                db.productDao().insert(json.decodeFromString(com.divan.app.core.db.entity.Product.serializer(), it))
            }
            b.assets.forEach {
                db.assetDao().insert(json.decodeFromString(com.divan.app.core.db.entity.AssetItem.serializer(), it))
            }
            true
        }.getOrDefault(false)
    }

    /** CSV of transactions (UTF-8 BOM so Excel renders Persian correctly). */
    suspend fun transactionsCsv(from: Long, to: Long): String = withContext(Dispatchers.IO) {
        val txns = db.txnDao().betweenOnce(from, to)
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.appendLine("id,date,type,amount,currency,account_id,category_id,counterparty_id,status,title,note")
        txns.forEach { t ->
            fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
            sb.appendLine(
                listOf(
                    t.id,
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.date)),
                    t.type.name, t.amountMinor, t.currencyCode,
                    esc(t.accountId), esc(t.categoryId), esc(t.counterpartyId),
                    t.status.name, esc(t.title), esc(t.note),
                ).joinToString(",")
            )
        }
        sb.toString()
    }

    fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }
}
