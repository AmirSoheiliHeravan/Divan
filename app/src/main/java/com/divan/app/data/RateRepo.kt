package com.divan.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * نرخ‌های زنده بازار آزاد — ارز/سکه/طلا از tgju.org، اتحادیه طلا از estjt.ir
 * قانون واحد: همه نرخ‌ها به «ریال» نرمال می‌شوند —
 *   · tgju.org: مقادیر ریال است و دست نمی‌خورد
 *   · estjt.ir: مقادیر «تومان» است → ×۱۰ و به ریال تبدیل می‌شود (انس طلا دلاری است)
 * مرجع‌ها ثابت در کد نیستند: فهرست نمادها در DataStore/SharedPreferences نگه داشته می‌شود
 * و کاربر می‌تواند هر نماد از tgju/estjt را اضافه یا حذف کند.
 * به‌روزرسانی: خودکار روزانه (WorkManager) + لحظه‌ای با بازکردن صفحه نرخ‌ها.
 */
data class LiveRate(
    val id: String,
    val name: String,
    val source: String,      // tgju | estjt
    val valueRial: Long,     // همیشه ریال (نمادهای دلاری → دلار)
    val changePercent: Double,
    val at: Long,
)

data class RateSymbol(val id: String, val name: String, val source: String, val key: String)

class RateRepo(private val ctx: Context) {

    companion object {
        val TGJU_URLS = listOf(
            "https://call4.tgju.org/ajax.json",
            "https://call1.tgju.org/ajax.json",
        )
        const val ESTJT_URL = "https://www.estjt.ir/"
        /** tgju: ریال است؛ estjt: تومان است و ×۱۰ می‌شود (usdKey = نماد دلاری بدون تبدیل) */
        val DEFAULT_SYMBOLS = listOf(
            RateSymbol("usd", "دلار آمریکا (بازار آزاد)", "tgju", "price_dollar_rl|price_dollar"),
            RateSymbol("eur", "یورو", "tgju", "price_eur_rl|price_eur"),
            RateSymbol("aed", "درهم امارات", "tgju", "price_aed"),
            RateSymbol("try", "لیر ترکیه", "tgju", "price_try"),
            RateSymbol("coin-emami", "سکه امامی", "tgju", "sekee"),
            RateSymbol("coin-bahar", "سکه بهار آزادی", "tgju", "sekeb"),
            RateSymbol("coin-half", "نیم‌سکه", "tgju", "nim"),
            RateSymbol("coin-quarter", "ربع‌سکه", "tgju", "rob"),
            RateSymbol("coin-gram", "سکه گرمی", "tgju", "gerami"),
            RateSymbol("gold18", "طلای ۱۸ عیار", "tgju", "geram18"),
            RateSymbol("gold24", "طلای ۲۴ عیار", "tgju", "geram24"),
            RateSymbol("mesghal", "مثقال طلا", "tgju", "mesghal"),
            RateSymbol("btc", "بیت‌کوین", "tgju", "crypto-bitcoin"),
            RateSymbol("tether", "تتر", "tgju", "crypto-tether"),
            RateSymbol("gold18-ej", "طلای ۱۸ عیار (اتحادیه)", "estjt", "طلا ۱۸ عیار"),
            RateSymbol("gold24-ej", "طلای ۲۴ عیار (اتحادیه)", "estjt", "طلای ۲۴ عیار"),
            RateSymbol("mesghal-ej", "مظنه تهران (مثقال)", "estjt", "مظنه تهران"),
            RateSymbol("coin-emami-ej", "سکه طرح جدید (اتحادیه)", "estjt", "سکه طرح جدید"),
            RateSymbol("ounce", "انس جهانی طلا", "estjt", "انس طلا"),
        )
        /** نمادهای دلاری estjt — بدون تبدیل تومان→ریال */
        private val ESTJT_USD_KEYS = setOf("انس طلا")
        /** نسخه کاتالوگ نمادها — با تغییر قانون واحد/کلیدها باید بالا برود */
        private const val SYMBOLS_VER = 2
    }

    private val prefs = ctx.getSharedPreferences("divan_rates", Context.MODE_PRIVATE)
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /* ---------- نمادهای کاربر ---------- */

    fun symbols(): List<RateSymbol> {
        // مهاجرت: فهرست قدیمی (کلیدهای شکسته مثل «طلای ۱۸» و نرخ تومانی) → کاتالوگ جدید
        if (prefs.getInt("symbols_ver", 1) != SYMBOLS_VER) {
            prefs.edit().putInt("symbols_ver", SYMBOLS_VER).remove("symbols").apply()
            return DEFAULT_SYMBOLS
        }
        val raw = prefs.getString("symbols", null) ?: return DEFAULT_SYMBOLS
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RateSymbol(o.getString("id"), o.getString("name"), o.getString("source"), o.getString("key"))
            }
        }.getOrDefault(DEFAULT_SYMBOLS)
    }

    fun addSymbol(id: String, name: String, source: String, key: String) {
        val list = symbols().filter { it.id != id } + RateSymbol(id, name, source, key)
        saveSymbols(list)
    }

    fun removeSymbol(id: String) {
        saveSymbols(symbols().filter { it.id != id })
    }

    private fun saveSymbols(list: List<RateSymbol>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("source", it.source).put("key", it.key))
        }
        prefs.edit().putString("symbols", arr.toString()).apply()
    }

    /* ---------- شخصی‌سازی نرخ‌ها (کدام نرخ لازم است + ترتیب) ---------- */

    /** نمادهای پنهان‌شده توسط کاربر — از جدول و کاشی‌های داشبورد حذف می‌شوند ولی حذف واقعی نمی‌شوند */
    fun hiddenIds(): Set<String> = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()

    fun setHidden(ids: Set<String>) {
        prefs.edit().putStringSet("hidden_ids", ids).apply()
    }

    /** ترتیب نمایش ذخیره‌شده (idها با | جدا می‌شوند) — خالی = ترتیب پیش‌فرض */
    fun savedOrder(): List<String> =
        prefs.getString("order", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    fun setOrder(ids: List<String>) {
        prefs.edit().putString("order", ids.joinToString("|")).apply()
    }

    /** فهرست کامل نمادها با ترتیب شخصی — شامل پنهان‌شده‌ها (برای دیالوگ شخصی‌سازی) */
    fun orderedSymbols(): List<RateSymbol> {
        val byId = symbols().associateBy { it.id }
        val saved = savedOrder().filter { byId.containsKey(it) }
        return (saved + symbols().filter { s -> s.id !in saved.toSet() }).mapNotNull { byId[it.id] }
    }

    /** اعمال شخصی‌سازی روی نرخ‌ها: ترتیب ذخیره‌شده + حذف پنهان‌ها؛ نمادهای جدید به انتها اضافه می‌شوند */
    fun personalize(list: List<LiveRate>): List<LiveRate> {
        val hidden = hiddenIds()
        val saved = savedOrder()
        if (hidden.isEmpty() && saved.isEmpty()) return list
        val savedSet = saved.toSet()
        val byId = list.associateBy { it.id }
        val order = saved + list.map { it.id }.filter { it !in savedSet }
        return order.mapNotNull { id -> byId[id]?.takeUnless { it.id in hidden } }
    }

    /* ---------- واکشی ---------- */

    /** واکشی لحظه‌ای همه نمادها؛ در صورت خطا، کش قبلی دست‌نخورده می‌ماند */
    suspend fun refresh(): List<LiveRate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LiveRate>()
        val syms = symbols()
        val tg = syms.filter { it.source == "tgju" }
        if (tg.isNotEmpty()) runCatching { fetchTgju(tg) }.getOrNull()?.let { out += it }
        val ej = syms.filter { it.source == "estjt" }
        if (ej.isNotEmpty()) runCatching { fetchEstjt(ej) }.getOrNull()?.let { out += it }
        if (out.isNotEmpty()) cache(out)
        out
    }
    /** آخرین نرخ‌های موفق (کش) — برای نمایش آفلاین */
    fun cached(): List<LiveRate> {
        val raw = prefs.getString("cache", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LiveRate(o.getString("id"), o.getString("name"), o.getString("source"),
                    o.getLong("val"), o.getDouble("chg"), o.getLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    fun lastUpdate(): Long = prefs.getLong("cache_at", 0L)

    private fun cache(list: List<LiveRate>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("source", it.source)
                .put("val", it.valueRial).put("chg", it.changePercent).put("at", it.at))
        }
        prefs.edit().putString("cache", arr.toString()).putLong("cache_at", System.currentTimeMillis()).apply()
    }

    private fun fetchTgju(syms: List<RateSymbol>): List<LiveRate> {
        var body: String? = null
        for (url in TGJU_URLS) {
            body = runCatching {
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()
                }
            }.getOrNull()
            if (!body.isNullOrEmpty()) break
        }
        if (body.isNullOrEmpty()) return emptyList()
        val root = JSONObject(body)
        val curObj = root.optJSONObject("current")
        val curArr = root.optJSONArray("current")
        fun find(key: String): JSONObject? {
            if (curObj != null) return curObj.optJSONObject(key)
            if (curArr != null) for (i in 0 until curArr.length()) {
                val o = curArr.optJSONObject(i) ?: continue
                if (o.optString("k") == key) return o
            }
            return null
        }
        val now = System.currentTimeMillis()
        return syms.mapNotNull { s ->
            // کلید می‌تواند چند گزینه باشد: "price_eur_rl|price_eur" — اولی که پیدا شود
            val o = s.key.split("|").mapNotNull { find(it.trim()) }.firstOrNull() ?: return@mapNotNull null
            val raw = parseNum(o.optString("p")) ?: return@mapNotNull null
            val dp = o.optString("dp").replace(",", "").toDoubleOrNull() ?: 0.0
            LiveRate(s.id, s.name, "tgju", Math.round(raw), dp, now)
        }
    }

    /**
     * estjt: جدول <td class="name">نام</td><td class="price">قیمت</td>
     * مقادیر «تومان» است → ×۱۰ و به ریال تبدیل می‌شود (انس طلا دلاری است و دست نمی‌خورد)
     */
    private fun fetchEstjt(syms: List<RateSymbol>): List<LiveRate> {
        val body = client.newCall(okhttp3.Request.Builder().url(ESTJT_URL).build()).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            r.body?.string() ?: return emptyList()
        }
        val now = System.currentTimeMillis()
        return syms.mapNotNull { s ->
            val re = Regex(Regex.escape(s.key) + "</td>\\s*<td class=\"price\">([^<]+)<")
            val m = re.find(body) ?: return@mapNotNull null
            val raw = parseNum(m.groupValues[1]) ?: return@mapNotNull null
            // ✅ تبدیل تومان → ریال (×۱۰)؛ انس طلا دلاری است و دست نمی‌خورد
            val rial = if (s.key in ESTJT_USD_KEYS) Math.round(raw) else Math.round(raw * 10.0)
            LiveRate(s.id, s.name, "estjt", rial, 0.0, now)
        }
    }

    /** ارقام فارسی/عربی → لاتین + حذف جداکننده هزارگان (٫ ٬ ، ,) با حفظ ممیز؛ null اگر عدد نباشد */
    private fun parseNum(s: String): Double? {
        if (s.isBlank()) return null
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder()
        for (c in s) {
            when {
                c in '۰'..'۹' -> sb.append(fa.indexOf(c))
                c in '٠'..'٩' -> sb.append(ar.indexOf(c))
                c in '0'..'9' -> sb.append(c)
                c == '.' -> sb.append(c)
            }
        }
        val v = sb.toString().toDoubleOrNull() ?: return null
        return if (v > 0) v else null
    }
}
