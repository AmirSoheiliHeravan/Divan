package com.divan.app.ui.util

import java.util.Calendar
import java.util.TimeZone

/**
 * تبدیل تاریخ جلالی ↔ میلادی (الگوریتم استاندارد jalaali)
 * تقویم همه‌جای برنامه از همین ابزار استفاده می‌کند.
 */
object Jalali {

    data class JDate(val jy: Int, val jm: Int, val jd: Int)

    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val WEEK_FULL = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private fun div(a: Int, b: Int) = Math.floorDiv(a, b)
    private fun mod(a: Int, b: Int) = Math.floorMod(a, b)

    private fun jalCal(jy: Int): Triple<Int, Int, Int> {
        val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        var n = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Triple(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val (_, gy, march) = jalCal(jy)
        return g2d(gy, 3, march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    private fun d2g(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> = d2g(j2d(jy, jm, jd))

    fun toJalali(gy: Int, gm: Int, gd: Int): JDate {
        val jdn = g2d(gy, gm, gd)
        var jy = d2g(jdn).first - 621
        val (leap, gy2, march) = jalCal(jy)
        val jdn1f = g2d(gy2, 3, march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (leap == 1) k += 1
        }
        return JDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun monthLen(jy: Int, jm: Int): Int {
        if (jm <= 6) return 31
        if (jm <= 11) return 30
        return j2d(jy + 1, 1, 1) - j2d(jy, 12, 1) // اسفند (کبیسه = ۳۰)
    }

    /** ۰ = شنبه … ۶ = جمعه */
    fun weekday(j: JDate): Int {
        val (gy, gm, gd) = toGregorian(j.jy, j.jm, j.jd)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(gy, gm - 1, gd)
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday … 7=Saturday
        return (dow % 7 + 1) % 7 // Saturday→0 … Friday→6
    }

    fun fromMillis(ts: Long): JDate {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        return toJalali(gy, gm, gd)
    }

    fun toMillis(j: JDate): Long {
        val (gy, gm, gd) = toGregorian(j.jy, j.jm, j.jd)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(gy, gm - 1, gd, 12, 0, 0)
        return cal.timeInMillis
    }

    fun fmt(ts: Long): String {
        val j = fromMillis(ts)
        return "${faNum(j.jd)} ${MONTHS[j.jm - 1]} ${faNum(j.jy)}"
    }

    fun fmtFull(ts: Long): String {
        val j = fromMillis(ts)
        return "${WEEK_FULL[weekday(j)]}، ${faNum(j.jd)} ${MONTHS[j.jm - 1]} ${faNum(j.jy)}"
    }

    /** عدد بدون جداکننده هزار با ارقام فارسی */
    fun faNum(x: Int): String {
        val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return x.toString().map { if (it.isDigit()) fa[it - '0'] else it }.joinToString("")
    }
}
