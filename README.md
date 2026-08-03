# أخبار المدرسة — دليل الرفع والنشر على GitHub Pages

هذا المشروع جاهز بالكامل، ولا يحتاج منك سوى رفعه إلى GitHub وتفعيل الاستضافة المجانية (GitHub Pages). اتبع الخطوات التالية بالترتيب.

## الخطوة 1: إنشاء حساب ومستودع على GitHub
1. افتح https://github.com وأنشئ حساباً مجانياً إذا لم يكن لديك واحد.
2. اضغط على زر **New** (مستودع جديد).
3. اسم المستودع (Repository name): مثلاً `school-news`.
4. اجعله **Public** (عام) — ضروري حتى يعمل GitHub Pages المجاني.
5. لا تُفعّل خيار "Add a README file" لتجنب التعارض، ثم اضغط **Create repository**.

## الخطوة 2: رفع ملفات المشروع
ارفع هذا المجلد بالكامل كما هو (بما فيه المجلدات الفرعية `assets` و `data`) بإحدى الطريقتين:

**الطريقة السهلة (من المتصفح):**
1. داخل صفحة المستودع الجديد، اضغط **Add file → Upload files**.
2. اسحب جميع الملفات والمجلدات (`index.html`, `admin.html`, `assets/`, `data/`) وأفلتها.
3. اضغط **Commit changes**.

**الطريقة الاحترافية (سطر الأوامر):**
```bash
git init
git add .
git commit -m "أول نسخة من موقع أخبار المدرسة"
git branch -M main
git remote add origin https://github.com/USERNAME/school-news.git
git push -u origin main
```
استبدل `USERNAME` باسم حسابك على GitHub.

## الخطوة 3: تفعيل GitHub Pages
1. داخل المستودع، اذهب إلى **Settings → Pages**.
2. تحت "Build and deployment" اختر **Source: Deploy from a branch**.
3. اختر الفرع **main** والمجلد **/ (root)**، ثم اضغط **Save**.
4. انتظر دقيقة أو دقيقتين، وسيظهر لك رابط الموقع في نفس الصفحة بالشكل:
   ```
   https://USERNAME.github.io/school-news/
   ```

## روابطك بعد التفعيل
- **صفحة القراءة (عامة لجميع الزوار):**
  `https://USERNAME.github.io/school-news/`
- **صفحة التحكم (بكلمة مرور):**
  `https://USERNAME.github.io/school-news/admin.html`

> استبدل `USERNAME` و `school-news` باسم حسابك واسم المستودع الفعليين.

## الخطوة 4: ربط صفحة التحكم بـ GitHub لنشر الأخبار فعلياً
بما أن الموقع مستضاف كملفات ثابتة، تحتاج صفحة التحكم إلى إذن للكتابة داخل المستودع حتى تنشر الأخبار الجديدة لجميع الزوار. لهذا أنشئ **Personal Access Token**:

1. اذهب إلى: https://github.com/settings/tokens?type=beta
2. اضغط **Generate new token**.
3. أعطه اسماً مثل `school-news-admin`.
4. تحت **Repository access** اختر **Only select repositories** ثم اختر مستودع `school-news`.
5. تحت **Permissions → Repository permissions** اختر **Contents: Read and write**.
6. اضغط **Generate token** وانسخ المفتاح فوراً (لن يظهر مرة أخرى).
7. افتح صفحة التحكم `admin.html`، أدخل كلمة المرور، ثم في قسم **"إعداد الربط مع GitHub"** أدخل:
   - اسم المستخدم (Owner): USERNAME
   - اسم المستودع (Repo): school-news
   - الفرع (Branch): main
   - المفتاح (Token): الصق المفتاح الذي أنشأته
   - اضغط **حفظ الإعدادات في هذا المتصفح**

بعدها ستتمكن من إضافة وتعديل وحذف الأخبار من صفحة التحكم، وستظهر التغييرات تلقائياً في صفحة القراءة العامة خلال ثوانٍ إلى دقيقة.

## معلومات مهمة عن الأمان
- كلمة مرور صفحة التحكم هي: `Aborayan1392` (مخزنة كبصمة تشفير SHA-256 داخل الكود وليست نصاً صريحاً، لكنها بكل الأحوال حماية بسيطة على مستوى المتصفح لأن الموقع بأكمله ثابت (Static) بلا خادم خلفي).
- مفتاح GitHub (Token) يُحفظ فقط داخل متصفحك على جهازك (localStorage) ولا يُرسل لأي مكان سوى GitHub نفسه.
- لا تشارك رابط صفحة التحكم أو المفتاح مع أي شخص لا تثق به.
- لتغيير كلمة المرور مستقبلاً: احسب بصمة SHA-256 الجديدة واستبدلها بالمتغير `PASSWORD_HASH` داخل ملف `admin.html`.

## هيكل الملفات
```
school-news/
├── index.html          ← صفحة القراءة العامة
├── admin.html           ← صفحة التحكم
├── assets/
│   └── style.css        ← التصميم الموحّد
└── data/
    └── news.json         ← قاعدة بيانات الأخبار (تُحدَّث تلقائياً من صفحة التحكم)
```
