package com.divan.app.core.db

import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.CategoryKind
import com.divan.app.core.db.entity.CurrencyUnit
import com.divan.app.core.db.entity.FamilySpace
import com.divan.app.core.db.entity.Member
import com.divan.app.core.db.entity.MemberRole
import java.util.UUID

/** Idempotent first-run seeding: family space, owner member, extensive
 *  lifestyle categories (fa/en/de/tr), standard currencies (no Toman),
 *  and Iranian bank SMS rules. Safe to call on every start. */
object SeedData {

    fun seed(db: AppDatabase) {
        if (db.familyDao().countAny() == 0) {
            db.familyDao().insert(FamilySpace("family", "خانواده من", System.currentTimeMillis()))
            db.memberDao().insert(
                Member(
                    id = UUID.randomUUID().toString(),
                    name = "من",
                    role = MemberRole.OWNER,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        if (db.categoryDao().countAll() == 0) seedCategories(db)
        if (db.currencyDao().allOnce().isEmpty()) seedCurrencies(db)
        if (db.smsDao().enabledRulesOnce().isEmpty()) db.smsDao().insertRules(BankSmsDefaults.rules())
    }

    private fun seedCurrencies(db: AppDatabase) {
        val list = listOf(
            CurrencyUnit("IRR", "Iranian Rial", "ریال", "﷼", 0, isBase = true),
            CurrencyUnit("USD", "US Dollar", "دلار آمریکا", "$", 2),
            CurrencyUnit("EUR", "Euro", "یورو", "€", 2),
            CurrencyUnit("TRY", "Turkish Lira", "لیر ترکیه", "₺", 2),
            CurrencyUnit("GBP", "British Pound", "پوند انگلیس", "£", 2),
            CurrencyUnit("AED", "UAE Dirham", "درهم امارات", "د.إ", 2),
            CurrencyUnit("CHF", "Swiss Franc", "فرانک سوئیس", "₣", 2),
            CurrencyUnit("CNY", "Chinese Yuan", "یوان چین", "¥", 2),
            CurrencyUnit("CAD", "Canadian Dollar", "دلار کانادا", "C$", 2),
            CurrencyUnit("AUD", "Australian Dollar", "دلار استرالیا", "A$", 2),
            CurrencyUnit("SAR", "Saudi Riyal", "ریال عربستان", "﷼", 2),
            CurrencyUnit("IQD", "Iraqi Dinar", "دینار عراق", "ع.د", 0),
            CurrencyUnit("AFN", "Afghan Afghani", "افغانی", "؋", 0),
            CurrencyUnit("AZN", "Azerbaijani Manat", "منات آذربایجان", "₼", 2),
            CurrencyUnit("RUB", "Russian Ruble", "روبل روسیه", "₽", 2),
        )
        db.currencyDao().insertAll(list)
    }

    private data class Sub(val fa: String, val en: String)
    private data class Grp(
        val fa: String, val en: String, val de: String, val tr: String,
        val kind: CategoryKind, val icon: String, val subs: List<Sub>
    )

    private val groups = listOf(
        Grp("درآمدها", "Income", "Einnahmen", "Gelirler", CategoryKind.INCOME, "💰", listOf(
            Sub("حقوق پایه", "Base salary"), Sub("اضافه‌کاری", "Overtime"),
            Sub("پاداش و عیدی", "Bonus"), Sub("درآمد پروژه‌ای", "Freelance income"),
            Sub("فروش کالا", "Product sales"), Sub("فروش خدمات", "Service sales"),
            Sub("اجاره دریافتی", "Rental income"), Sub("سود سپرده", "Deposit interest"),
            Sub("سود سرمایه‌گذاری", "Investment profit"), Sub("هدیه نقدی", "Cash gift"),
            Sub("بازپرداخت بدهی توسط دیگران", "Debt repayment received"),
            Sub("دریافت وام", "Loan received"), Sub("درآمد متفرقه", "Other income"),
        )),
        Grp("خانه", "Home", "Hauskosten", "Ev giderleri", CategoryKind.EXPENSE, "🏠", listOf(
            Sub("اجاره", "Rent"), Sub("شارژ ساختمان", "Building charges"),
            Sub("رهن و ودیعه", "Deposit"), Sub("آب", "Water bill"),
            Sub("برق", "Electricity"), Sub("گاز", "Gas bill"),
            Sub("اینترنت و تلفن", "Internet & phone"), Sub("تعمیرات", "Repairs"),
            Sub("نظافت", "Cleaning service"), Sub("لوازم مصرفی خانه", "Home supplies"),
            Sub("مبلمان و دکوراسیون", "Furniture & decor"),
            Sub("لوازم آشپزخانه", "Kitchenware"), Sub("بیمه منزل", "Home insurance"),
            Sub("اسباب‌کشی", "Moving"),
        )),
        Grp("سوپرمارکت", "Groceries", "Supermarkt", "Süpermarket", CategoryKind.EXPENSE, "🛒", listOf(
            Sub("لبنیات و تخم‌مرغ", "Dairy & eggs"), Sub("نان و غلات", "Bread & grains"),
            Sub("گوشت و پروتئین", "Meat & protein"), Sub("میوه و سبزی", "Fruits & vegetables"),
            Sub("حبوبات و خشکبار", "Legumes & nuts"), Sub("چاشنی و مواد پخت", "Spices & cooking"),
            Sub("نوشیدنی", "Beverages"), Sub("تنقلات و شیرینی", "Snacks & sweets"),
            Sub("غذای آماده", "Ready food"),
        )),
        Grp("شوینده و بهداشتی", "Cleaning & hygiene", "Reinigung & Hygiene", "Deterjan ve hijyen", CategoryKind.EXPENSE, "🧴", listOf(
            Sub("مایع ظرف‌شویی", "Dish soap"), Sub("لباس‌شویی", "Laundry"),
            Sub("سفیدکننده و لکه‌بر", "Bleach & stain"), Sub("کیسه زباله و دستمال", "Bags & tissues"),
            Sub("لوازم نظافت", "Cleaning tools"), Sub("بهداشت شخصی", "Personal hygiene"),
            Sub("مراقبت مو و پوست", "Hair & skin care"),
            Sub("پوشک و کودک", "Diapers & baby"), Sub("بهداشت بانوان", "Feminine care"),
        )),
        Grp("پوشاک و شخصی", "Clothing & personal", "Kleidung & Persönliches", "Giyim ve kişisel", CategoryKind.EXPENSE, "👕", listOf(
            Sub("لباس روزمره", "Everyday clothes"), Sub("لباس رسمی", "Formal wear"),
            Sub("لباس ورزشی", "Sportswear"), Sub("لباس زیر و جوراب", "Underwear & socks"),
            Sub("کفش", "Shoes"), Sub("کیف و کوله", "Bags"),
            Sub("ساعت و زیورآلات", "Watches & jewelry"), Sub("عینک", "Glasses"),
            Sub("خیاطی و تعمیر", "Tailoring & repair"), Sub("لوازم آرایشی و عطر", "Cosmetics & perfume"),
        )),
        Grp("کودک و خانواده", "Child & family", "Kind & Familie", "Çocuk ve aile", CategoryKind.EXPENSE, "👶", listOf(
            Sub("پوشاک کودک", "Kids clothes"), Sub("شیرخشک و غذای کودک", "Baby formula & food"),
            Sub("اسباب‌بازی", "Toys"), Sub("مهدکودک و نگهداری", "Daycare"),
            Sub("مدرسه و لوازم آموزشی", "School & supplies"), Sub("تفریح کودک", "Kids fun"),
            Sub("هدیه و مناسبت‌های خانوادگی", "Gifts & occasions"),
            Sub("کمک به اعضای خانواده", "Family support"),
            Sub("مراقبت از سالمند", "Elder care"),
        )),
        Grp("سلامت و درمان", "Health", "Gesundheit", "Sağlık", CategoryKind.EXPENSE, "🏥", listOf(
            Sub("ویزیت پزشک", "Doctor visit"), Sub("دندان‌پزشکی", "Dentist"),
            Sub("آزمایش و تصویربرداری", "Lab & imaging"), Sub("دارو", "Medicine"),
            Sub("بیمه درمانی", "Health insurance"), Sub("عینک و وسایل بینایی", "Vision aids"),
            Sub("فیزیوتراپی", "Physiotherapy"), Sub("تجهیزات پزشکی", "Medical equipment"),
            Sub("خدمات اورژانسی", "Emergency services"), Sub("واکسیناسیون", "Vaccination"),
        )),
        Grp("حمل‌ونقل و خودرو", "Transport & car", "Transport & Auto", "Ulaşım ve araç", CategoryKind.EXPENSE, "🚗", listOf(
            Sub("سوخت", "Fuel"), Sub("سرویس دوره‌ای", "Periodic service"),
            Sub("تعمیرات خودرو", "Car repairs"), Sub("لاستیک و باتری", "Tires & battery"),
            Sub("بیمه خودرو", "Car insurance"), Sub("معاینه فنی", "Technical inspection"),
            Sub("پارکینگ", "Parking"), Sub("جریمه", "Fines"),
            Sub("تاکسی و اسنپ", "Taxi & ride-hailing"), Sub("حمل‌ونقل عمومی", "Public transport"),
            Sub("بلیت هواپیما", "Flight tickets"), Sub("لوازم جانبی خودرو", "Car accessories"),
        )),
        Grp("ارتباطات و فناوری", "Tech & telecom", "Kommunikation & Technik", "İletişim ve teknoloji", CategoryKind.EXPENSE, "📱", listOf(
            Sub("شارژ سیم‌کارت", "Mobile top-up"), Sub("بسته اینترنت", "Data package"),
            Sub("اینترنت خانگی", "Home internet"), Sub("گوشی و تبلت", "Phone & tablet"),
            Sub("رایانه و لپ‌تاپ", "Computer & laptop"), Sub("لوازم جانبی", "Accessories"),
            Sub("نرم‌افزار و اشتراک", "Software & subscriptions"), Sub("فضای ابری", "Cloud storage"),
            Sub("تعمیر تجهیزات", "Repairs"), Sub("تلویزیون و سیستم صوتی", "TV & audio"),
            Sub("خانه هوشمند", "Smart home"),
        )),
        Grp("آموزش و کار", "Education & work", "Bildung & Beruf", "Eğitim ve meslek", CategoryKind.EXPENSE, "📚", listOf(
            Sub("شهریه", "Tuition"), Sub("کتاب و لوازم‌التحریر", "Books & stationery"),
            Sub("دوره آموزشی", "Courses"), Sub("کلاس زبان", "Language classes"),
            Sub("آزمون و گواهی‌نامه", "Exams & certificates"), Sub("تجهیزات کاری", "Work equipment"),
            Sub("سفر کاری", "Business trips"), Sub("فضای کار", "Workspace"),
        )),
        Grp("تفریح و سفر", "Leisure & travel", "Freizeit & Reisen", "Eğlence ve seyahat", CategoryKind.EXPENSE, "🎯", listOf(
            Sub("رستوران و کافه", "Restaurant & cafe"), Sub("سینما و تئاتر", "Cinema & theater"),
            Sub("ورزش و باشگاه", "Sport & gym"), Sub("کتاب و مجله", "Books & magazines"),
            Sub("سفر و اقامت", "Travel & stay"), Sub("هتل", "Hotel"),
            Sub("کمپینگ و طبیعت", "Camping & nature"), Sub("اشتراک سرگرمی", "Entertainment subs"),
            Sub("هدیه و جشن", "Gifts & parties"), Sub("مراسم", "Ceremonies"),
        )),
        Grp("مالی و اداری", "Finance & legal", "Finanzen & Verwaltung", "Mali ve idari", CategoryKind.EXPENSE, "🏛", listOf(
            Sub("کارمزد بانکی", "Bank fees"), Sub("مالیات", "Taxes"),
            Sub("بیمه", "Insurance"), Sub("مشاوره حقوقی", "Legal advice"),
            Sub("هزینه ثبت سند", "Document registration"), Sub("جریمه و دیرکرد", "Penalties & late fees"),
            Sub("هزینه تبدیل ارز", "Currency exchange fee"), Sub("کمک خیریه", "Charity"),
        )),
        Grp("سرمایه‌گذاری", "Investments", "Investment", "Yatırım", CategoryKind.BOTH, "📈", listOf(
            Sub("خرید ملک", "Property purchase"), Sub("فروش ملک", "Property sale"),
            Sub("خرید خودرو", "Car purchase"), Sub("فروش خودرو", "Car sale"),
            Sub("طلا و جواهر", "Gold & jewelry"), Sub("ارز نقدی", "Cash currency"),
            Sub("سپرده بانکی", "Bank deposit"), Sub("سهام و اوراق", "Stocks & bonds"),
            Sub("صندوق سرمایه‌گذاری", "Mutual funds"), Sub("تجهیزات باارزش", "Valuable equipment"),
        )),
        Grp("بدهی و تعهدات", "Debts & commitments", "Schulden", "Borçlar", CategoryKind.BOTH, "🧾", listOf(
            Sub("بازپرداخت وام", "Loan repayment"), Sub("قرض خانوادگی", "Family loan"),
            Sub("خرید اقساطی", "Installment purchase"), Sub("بدهی کارت اعتباری", "Credit card debt"),
            Sub("چک پرداختنی", "Issued cheque"), Sub("چک دریافتنی", "Received cheque"),
            Sub("پرداخت بدهی", "Debt payment"), Sub("پیش‌پرداخت و ودیعه", "Prepayment & deposit"),
        )),
        Grp("سایر", "Other", "Sonstiges", "Diğer", CategoryKind.EXPENSE, "📦", listOf(
            Sub("خوراک حیوان خانگی", "Pet food"), Sub("دامپزشکی", "Veterinary"),
            Sub("لوازم حیوان", "Pet supplies"), Sub("باغبانی", "Gardening"),
            Sub("کالای دست‌دوم", "Second-hand"), Sub("هزینه متفرقه", "Miscellaneous"),
        )),
    )

    private fun seedCategories(db: AppDatabase) {
        val now = System.currentTimeMillis()
        val cats = mutableListOf<Category>()
        var order = 0
        groups.forEach { g ->
            val rootId = UUID.randomUUID().toString()
            cats += Category(
                id = rootId, parentId = null, name = g.fa, nameEn = g.en,
                kind = g.kind, icon = g.icon, sortOrder = order++, isSystem = true,
                createdAt = now, updatedAt = now,
            )
            g.subs.forEachIndexed { i, s ->
                cats += Category(
                    id = UUID.randomUUID().toString(), parentId = rootId,
                    name = s.fa, nameEn = s.en, kind = g.kind,
                    icon = g.icon, sortOrder = order + i, isSystem = true,
                    createdAt = now, updatedAt = now,
                )
            }
            order += g.subs.size
        }
        db.categoryDao().insertAll(cats)
    }
}
