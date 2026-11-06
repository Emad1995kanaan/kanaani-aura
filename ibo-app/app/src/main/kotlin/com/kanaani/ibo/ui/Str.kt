package com.kanaani.ibo.ui

import com.kanaani.ibo.data.Store

/** Langues proposées : arabe (par défaut), anglais, hébreu. */
enum class Lang(val code: String, val label: String, val rtl: Boolean) {
    AR("ar", "العربية", true),
    EN("en", "English", false),
    HE("he", "עברית", true);

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: AR
    }
}

/** Textes de l'interface. Lus pendant la composition : un changement de langue recompose tout. */
object Str {
    val lang: Lang get() = Lang.of(Store.language)
    private fun t(en: String, ar: String, he: String) = when (lang) { Lang.AR -> ar; Lang.HE -> he; Lang.EN -> en }

    // Accueil
    val live get() = t("Live TV", "البث المباشر", "שידור חי")
    val movies get() = t("Movies", "الأفلام", "סרטים")
    val series get() = t("Series", "المسلسلات", "סדרות")
    val favorites get() = t("Favorites", "المفضلة", "מועדפים")
    val account get() = t("Account", "الحساب", "חשבון")
    val settings get() = t("Settings", "الإعدادات", "הגדרות")
    val welcome get() = t("Welcome", "أهلاً وسهلاً", "ברוכים הבאים")
    val expires get() = t("Expires", "ينتهي في", "תוקף עד")
    val daysLeft get() = t("days left", "يوم متبقٍ", "ימים נותרו")
    val unlimited get() = t("Unlimited", "غير محدود", "ללא הגבלה")
    val dollar get() = t("Dollar", "دولار", "דולר")
    val dinar get() = t("Dinar", "دينار", "דינר")
    val euro get() = t("Euro", "يورو", "יורו")

    // Connexion
    val signIn get() = t("Sign in", "تسجيل الدخول", "התחברות")
    val signInHint get() = t("Enter the username and password of your subscription", "أدخل اسم المستخدم وكلمة المرور الخاصة باشتراكك", "הזינו את שם המשתמש והסיסמה של המנוי שלכם")
    val username get() = t("Username", "اسم المستخدم", "שם משתמש")
    val password get() = t("Password", "كلمة المرور", "סיסמה")
    val login get() = t("Login", "دخول", "כניסה")
    val checking get() = t("Checking…", "جارٍ التحقق…", "בודק…")
    val tagline get() = t("Live TV · Movies · Series", "بث مباشر · أفلام · مسلسلات", "שידור חי · סרטים · סדרות")
    val errWrong get() = t("Wrong username or password.", "اسم المستخدم أو كلمة المرور غير صحيحة.", "שם משתמש או סיסמה שגויים.")
    val errNetwork get() = t("Cannot reach the server. Check your internet connection.", "تعذّر الاتصال بالسيرفر. تأكد من اتصال الإنترنت.", "אין גישה לשרת. בדקו את חיבור האינטרנט.")
    val errExpired get() = t("Your subscription has expired. Contact your reseller to renew it.", "انتهى اشتراكك. تواصل مع الموزّع لتجديده.", "המנוי שלכם הסתיים. פנו למשווק כדי לחדש אותו.")
    val errDisabled get() = t("Your subscription is suspended. Contact your reseller.", "اشتراكك موقوف. تواصل مع الموزّع.", "המנוי שלכם מושהה. פנו למשווק.")

    // Listes
    val sortBy get() = t("Sort by", "ترتيب حسب", "מיון לפי")
    val sortNames get() = listOf(t("Default", "الافتراضي", "ברירת מחדל"), t("Year", "السنة", "שנה"), t("Rating", "التقييم", "דירוג"), "A - Z", "Z - A")
    val refreshAsk get() = t("Refresh all content?", "هل أنت متأكد أنك تريد تحديث جميع المحتويات؟", "לרענן את כל התוכן?")
    val catOptions get() = t("Category options", "خيارات الفئة", "אפשרויות קטגוריה")
    val lock get() = t("Lock", "قفل", "נעילה")
    val unlock get() = t("Unlock", "فتح القفل", "ביטול נעילה")
    val moveCat get() = t("Move category", "نقل الفئة", "הזזת קטגוריה")
    val enterPin get() = t("Enter PIN", "أدخل الرمز السري", "הזן קוד")
    val wrongPin get() = t("Wrong PIN", "الرمز غلط", "קוד שגוי")
    val changePin get() = t("Change parental PIN", "تغيير كلمة المرور", "שינוי קוד הורים")
    // Aura : historique du direct, panneau Réglages
    val watchHistory get() = t("Watching History", "سجل المشاهدة", "היסטוריית צפייה")
    val resetContent get() = t("Reset content", "إعادة تعيين المحتوى", "איפוס תוכן")
    val resetContentAsk get() = t("Erase favorites, history, continue watching, category order and locks?", "مسح المفضلة وسجل المشاهدة ومتابعة المشاهدة وترتيب الفئات والأقفال؟", "למחוק מועדפים, היסטוריה, המשך צפייה, סדר קטגוריות ונעילות?")
    val resetDone get() = t("Content reset", "تمت إعادة التعيين", "התוכן אופס")
    val resetCode get() = t("Reset activation code", "إعادة تعيين رمز التفعيل", "איפוס קוד הפעלה")
    val chooseLanguage get() = t("Choose language", "اختر اللغة", "בחר שפה")
    val accountInfo get() = t("Account information", "معلومات الحساب", "פרטי חשבון")
    val appInfo get() = t("App information", "معلومات التطبيق", "פרטי האפליקציה")
    val activationCode get() = t("Activation code", "رمز التفعيل", "קוד הפעלה")
    val expiryDate get() = t("Expiry date", "تاريخ الانتهاء", "תאריך תפוגה")
    val macAddress get() = t("MAC address", "عنوان MAC", "כתובת MAC")
    val days get() = t("days", "يوم", "ימים")
    val autoQuality get() = t("Auto lower quality when a channel freezes", "تبديل الجودة تلقائيًا لما القناة تعلّق", "הורדת איכות אוטומטית כשערוץ נתקע")
    val onOff get() = t("On|Off", "شغّال|مطفي", "פועל|כבוי")
    val trailer get() = t("Trailer", "المقدمة", "טריילר")
    val watchNow get() = t("Watch now", "شاهد الآن", "צפה עכשיו")
    val recentlyViewed get() = t("Recently viewed", "شوهد مؤخراً", "נצפו לאחרונה")
    val all get() = t("All", "الكل", "הכול")
    val downloads get() = t("Downloads", "التنزيلات", "הורדות")
    val download get() = t("Download", "تحميل", "הורדה")
    val downloadSeason get() = t("Download season", "تحميل الموسم", "הורדת העונה")
    val downloaded get() = t("Downloaded", "محمّل", "הורד")
    val dlRunning get() = t("Downloading", "عم يتحمّل", "מוריד")
    val dlQueued get() = t("Waiting", "بالانتظار", "ממתין")
    val dlPaused get() = t("Paused", "موقّف", "מושהה")
    val dlFailed get() = t("Failed — retry", "فشل — جرّب مرة ثانية", "נכשל — נסה שוב")
    val pause get() = t("Pause", "إيقاف", "השהיה")
    val resumeDl get() = t("Resume", "كمّل", "המשך")
    val delete get() = t("Delete", "حذف", "מחיקה")
    val deleteAsk get() = t("Delete this download?", "بدك تحذف هاد التحميل؟", "למחוק את ההורדה?")
    val noDownloads get() = t("No downloads yet. Open a movie or an episode and press Download.", "ما في تنزيلات. افتح فيلم أو حلقة واكبس تحميل.", "אין הורדות. פתחו סרט או פרק ולחצו הורדה.")
    val offlineLocked get() = t("Connect to the internet to verify your subscription, then try again.", "اتصل بالإنترنت عشان نتأكد من اشتراكك، وبعدين جرّب مرة ثانية.", "התחברו לאינטרנט לאימות המנוי ונסו שוב.")
    val noSpace get() = t("Not enough storage space.", "ما في مساحة كافية على الجهاز.", "אין מספיק מקום אחסון.")
    val freeSpace get() = t("Free space", "المساحة الفاضية", "מקום פנוי")
    val pausedWhileWatching get() = t("Downloads pause while you watch online", "التحميل بيوقف وإنت بتحضر أونلاين", "ההורדות מושהות בזמן צפייה אונליין")
    val homeTheme get() = t("Home theme", "ثيم الصفحة الرئيسية", "ערכת דף הבית")
    val themeClassic get() = t("Classic", "الكلاسيك", "קלאסי")
    val themeBillboard get() = t("Billboard", "اللوحة الإعلانية", "לוח פרסום")
    val aspectFit get() = t("Fit to screen", "ملاءمة الشاشة", "התאמה למסך")
    val aspectFill get() = t("Full screen", "ملء الشاشة", "מסך מלא")
    val aspectZoom get() = t("Zoom", "تكبير", "הגדלה")
    fun aspectName(mode: Int) = when (mode) { 1 -> aspectFill; 2 -> aspectZoom; else -> aspectFit }
    val continueWatching get() = t("Continue watching", "استكمال المشاهدة", "המשך צפייה")
    val resume get() = t("Resume", "استكمال", "המשך")
    val fromStart get() = t("From the beginning", "من البداية", "מההתחלה")
    val latestMovies get() = t("Latest additions", "آخر الإضافات", "נוספו לאחרונה")
    val latestMoviesUpdates get() = t("Latest movie updates", "آخر تحديثات الأفلام", "עדכוני סרטים אחרונים")
    val latestSeries get() = t("Latest series updates", "آخر تحديثات المسلسلات", "סדרות שעודכנו לאחרונה")
    val categories get() = t("Categories", "الفئات", "קטגוריות")
    val search get() = t("Search…", "بحث…", "חיפוש…")
    val searchCats get() = t("Search categories", "بحث بالتصنيفات", "חיפוש קטגוריות")
    val searchButton get() = t("Search", "بحث", "חיפוש")
    val abcKeyboard get() = t("ABC", "ABC", "ABC")
    val numKeyboard get() = t("123", "123", "123")
    val loading get() = t("Loading…", "جارٍ التحميل…", "טוען…")
    val empty get() = t("Nothing here", "لا يوجد محتوى", "אין כאן תוכן")
    val loadFailed get() = t("Could not load. Press to retry.", "تعذّر التحميل. اضغط لإعادة المحاولة.", "הטעינה נכשלה. לחצו לנסות שוב.")
    val channels get() = t("channels", "قناة", "ערוצים")
    val previewHint get() = t("Press OK again for full screen", "اضغط مرة ثانية لملء الشاشة", "לחצו שוב למסך מלא")
    val chooseChannel get() = t("Choose a channel to watch", "اختر قناة للمشاهدة", "בחרו ערוץ לצפייה")

    // Fiches
    val play get() = t("Play", "تشغيل", "הפעלה")
    val addFav get() = t("Add to favorites", "أضف للمفضلة", "הוספה למועדפים")
    val removeFav get() = t("Remove from favorites", "إزالة من المفضلة", "הסרה מהמועדפים")
    val season get() = t("Season", "الموسم", "עונה")
    val episode get() = t("Episode", "الحلقة", "פרק")
    val director get() = t("Director", "إخراج", "במאי")
    val cast get() = t("Cast", "بطولة", "שחקנים")
    val noEpisodes get() = t("No episodes available", "لا توجد حلقات", "אין פרקים זמינים")

    // Lecteur
    val playError get() = t("This stream cannot be played right now.", "تعذّر تشغيل البث حالياً.", "לא ניתן להפעיל את השידור כרגע.")
    val retry get() = t("Retry", "إعادة المحاولة", "נסו שוב")
    val nextEpisode get() = t("Next episode", "الحلقة التالية", "הפרק הבא")
    fun nextIn(sec: Long) = t("Starts in $sec s", "تبدأ خلال $sec ثانية", "מתחיל בעוד $sec שניות")
    val playNow get() = t("Play now", "شغّل الآن", "הפעלה עכשיו")

    // Compte
    val status get() = t("Status", "الحالة", "סטטוס")
    val active get() = t("Active", "فعّال", "פעיל")
    val connections get() = t("Connections", "الاتصالات", "חיבורים")
    val created get() = t("Created", "تاريخ الإنشاء", "נוצר")
    val trial get() = t("Trial", "تجريبي", "ניסיון")
    val yes get() = t("Yes", "نعم", "כן")
    val no get() = t("No", "لا", "לא")

    // Réglages
    val language get() = t("Language", "اللغة", "שפה")
    val orderLive get() = t("Order channels", "ترتيب القنوات", "סידור ערוצים")
    val orderMovies get() = t("Order movies", "ترتيب الأفلام", "סידור סרטים")
    val orderSeries get() = t("Order series", "ترتيب المسلسلات", "סידור סדרות")
    val orderHint get() = t(
        "Press OK on a category to pick it up, move it with ▲ ▼, then press OK again to drop it. On a phone, use the arrows next to each category.",
        "اضغط OK على الفئة لتمسكها، حرّكها بالأسهم ▲ ▼، واضغط OK مرة ثانية لتثبيتها. على الجوال استعمل الأسهم اللي جنب كل فئة.",
        "לחצו OK על קטגוריה כדי לתפוס אותה, הזיזו עם ▲ ▼ ולחצו OK שוב כדי לשחרר. בטלפון השתמשו בחצים שליד כל קטגוריה.")
    val hide get() = t("Hide", "إخفاء", "הסתרה")
    val show get() = t("Show", "إظهار", "הצגה")
    val hiddenTag get() = t("Hidden", "مخفي", "מוסתר")
    val showAll get() = t("Show all", "إظهار الكل", "הצג הכול")
    val orderReset get() = t("Restore original order", "إرجاع الترتيب الأصلي", "שחזור הסדר המקורי")
    val moving get() = t("Moving", "عم تتحرك", "בהזזה")
    val reorderHint get() = t(
        "OK selects categories (✓). Remote colour keys: 🔴 up · 🔵 down · 🟢 rename · 🟡 hide/show — or press ▶ to reach the buttons. Without a selection, they act on the category under the cursor.",
        "OK بيحدد الفئات (✓). أزرار الريموت الملوّنة: 🔴 لفوق · 🔵 لتحت · 🟢 إعادة تسمية · 🟡 إخفاء/إظهار، أو اضغط ▶ للأزرار اللي على الجنب. بدون تحديد بتشتغل على الفئة اللي واقف عليها.",
        "OK בוחר קטגוריות (✓). כפתורי הצבע בשלט: 🔴 למעלה · 🔵 למטה · 🟢 שינוי שם · 🟡 הסתרה/הצגה, או ▶ לכפתורים בצד. בלי בחירה הם פועלים על הקטגוריה המסומנת.")
    fun selected(n: Int) = t("$n selected", "محدد $n", "נבחרו $n")
    val focusedCategory get() = t("Category under cursor", "الفئة اللي واقف عليها", "הקטגוריה שמסומנת")
    val moveUp get() = t("Up", "لفوق", "למעלה")
    val moveDown get() = t("Down", "لتحت", "למטה")
    val toTop get() = t("To top", "لأول القائمة", "לראש הרשימה")
    val toBottom get() = t("To bottom", "لآخر القائمة", "לסוף הרשימה")
    val rename get() = t("Rename", "إعادة تسمية", "שינוי שם")
    val clearSelection get() = t("Clear", "إلغاء التحديد", "ניקוי בחירה")
    val save get() = t("Save", "حفظ", "שמירה")
    val originalName get() = t("Original name", "الاسم الأصلي", "השם המקורי")
    val restoreName get() = t("Restore original name", "إرجاع الاسم الأصلي", "שחזור השם המקורי")
    val refresh get() = t("Refresh content", "تحديث المحتوى", "רענון תוכן")
    val refreshed get() = t("Content will be reloaded", "رح يتم تحديث المحتوى", "התוכן ייטען מחדש")
    val logout get() = t("Log out", "تسجيل الخروج", "התנתקות")
    val logoutAsk get() = t("Log out of this device?", "تسجيل الخروج من هذا الجهاز؟", "להתנתק מהמכשיר הזה?")
    val about get() = t("About", "حول التطبيق", "אודות")
    val version get() = t("Version", "الإصدار", "גרסה")
    val cancel get() = t("Cancel", "إلغاء", "ביטול")

    // Mises à jour
    val checkUpdates get() = t("Check for updates", "التحقق من التحديثات", "בדיקת עדכונים")
    val updateAvailable get() = t("New update available", "تحديث جديد متوفر", "עדכון חדש זמין")
    val upToDate get() = t("You have the latest version", "عندك آخر إصدار", "יש לכם את הגרסה האחרונה")
    val updateFailed get() = t("Update failed", "تعذّر التحديث", "העדכון נכשל")
    val current get() = t("current", "الحالي", "נוכחית")
    val updateNow get() = t("Update now", "تحديث الآن", "עדכון עכשיו")
    val later get() = t("Later", "لاحقاً", "אחר כך")
    val install get() = t("Install", "تثبيت", "התקנה")
    val downloading get() = t("Downloading update…", "جارٍ تنزيل التحديث…", "מוריד עדכון…")
    val installHint get() = t(
        "If Android asks, allow Kanaani to install apps, then press Install again.",
        "إذا طلب أندرويد، اسمح لـ Kanaani بتثبيت التطبيقات، بعدين اضغط تثبيت مرة ثانية.",
        "אם אנדרואיד מבקש, אפשרו ל-Kanaani להתקין אפליקציות ולחצו שוב התקנה.")
    val problemDownload get() = t("Download failed. Check the internet connection and try again.", "فشل التنزيل. تأكد من الإنترنت وجرّب مرة ثانية.", "ההורדה נכשלה. בדקו את האינטרנט ונסו שוב.")
    val problemCorrupt get() = t("The downloaded file is damaged. Try again.", "الملف اللي نزل فيه خلل. جرّب مرة ثانية.", "הקובץ שהורד פגום. נסו שוב.")
    val problemOld get() = t("This update is not newer than the installed version.", "هاد التحديث مش أحدث من النسخة المنزّلة.", "העדכון הזה אינו חדש יותר מהגרסה המותקנת.")
    val problemSignature get() = t("This update is signed differently. Uninstall the app once, then install it from the download link.", "هاد التحديث موقّع بشكل مختلف. احذف التطبيق مرة وحدة ونزّله من رابط التحميل.", "העדכון חתום אחרת. הסירו את האפליקציה פעם אחת והתקינו מקישור ההורדה.")
    val problemInstall get() = t("Android could not open the installer.", "أندرويد ما قدر يفتح شاشة التثبيت.", "אנדרואיד לא הצליח לפתוח את ההתקנה.")
    val retryUpdate get() = t("Try again", "جرّب مرة ثانية", "נסו שוב")
    val ok get() = t("OK", "موافق", "אישור")
    val layout get() = t("Channel order", "الترتيب", "סידור")
    val layoutMine get() = t("My own order", "ترتيبي الخاص", "הסידור שלי")
    val layoutApplied get() = t("Order applied", "تم تطبيق الترتيب", "הסידור הוחל")
    val layoutDefaultTag get() = t("default", "الافتراضي", "ברירת מחדל")
    val adminTools get() = t("Admin tools", "أدوات الأدمن", "כלי ניהול")
    val exportLists get() = t("Export lists", "تصدير القوائم", "ייצוא רשימות")
    val uploadLayout get() = t("Upload my order to the panel", "رفع ترتيبي للوحة", "העלאת הסידור שלי לפאנל")
    val panelUser get() = t("Panel admin username", "يوزر أدمن اللوحة", "שם משתמש מנהל")
    val panelPass get() = t("Panel admin password", "باسورد أدمن اللوحة", "סיסמת מנהל")
    val layoutName get() = t("Name for the new order", "اسم الترتيب الجديد", "שם לסידור החדש")
    val newLayout get() = t("Save as new order", "حفظ كترتيب جديد", "שמור כסידור חדש")
    val replaceLayout get() = t("Replace", "استبدال", "החלף")
    val uploaded get() = t("Uploaded to the panel ✓", "انرفع للوحة ✓", "הועלה לפאנל ✓")
    val trialEnded get() = t("Your trial has ended. To subscribe, contact us on WhatsApp.", "انتهت فترة التجربة. للاشتراك تواصل معنا على واتساب.", "תקופת הניסיון הסתיימה. להרשמה פנו אלינו בוואטסאפ.")
    val trialFull get() = t("The trial is full right now. Try again a bit later.", "التجربة ممتلئة حالياً. جرّب كمان شوي.", "הניסיון מלא כרגע. נסו שוב מאוחר יותר.")
    val trialDevice get() = t("This trial account is open on another device (one device at a time).", "الحساب التجريبي مفتوح على جهاز تاني (جهاز واحد بنفس الوقت).", "חשבון הניסיון פתוח במכשיר אחר (מכשיר אחד בכל פעם).")
    val trialOff get() = t("This trial account is stopped.", "الحساب التجريبي موقوف.", "חשבון הניסיון הושהה.")
    val whatsapp get() = t("WhatsApp", "واتساب", "וואטסאפ")
}
