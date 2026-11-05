# REA Console

تطبيق Android أصلي بواجهة عربية RTL لا يشغّل REA على الهاتف. يرفع ملف APK إلى `targets/` في المستودع ويشغّل التحليل على GitHub Actions.

## إعداد GitHub Token
من GitHub افتح **Settings → Developer settings → Personal access tokens**. أنشئ Fine-grained token للمستودع مع **Contents: Read and write** و**Actions: Read and write**؛ أو Classic token بالنطاقين **repo** و**workflow**. أدخل الرمز واسم المستودع بصيغة `owner/repo` في إعدادات التطبيق. يُحفظ الرمز باستخدام EncryptedSharedPreferences ولا يُطبع في السجلات.

## أسرار توقيع APK
أنشئ keystore محلياً ولا ترفعه. في **Repository → Settings → Secrets and variables → Actions** أضف:
- `SIGNING_KEY`: ملف keystore بصيغة Base64.
- `ALIAS`: اسم المفتاح.
- `KEY_STORE_PASSWORD`: كلمة مرور keystore.
- `KEY_PASSWORD`: كلمة مرور المفتاح.

عند دفع tag يبدأ بـ `v`، يبني workflow نسخة Release ويوقّعها ثم ينشرها في GitHub Releases. لا تضع أي مفتاح توقيع أو keystore في المستودع.

## تنزيل APK
افتح **Actions → Android APK**، واختر أحدث تشغيل ناجح، ثم نزّل artifact باسم `rea-console-debug-apk`. للإصدار الموقّع افتح **Releases** ونزّل APK المرتبط بالـ tag.

## تحليل APK
أدخل Token واسم المستودع، ثم اختر APK. يُرفع الملف إلى `targets/` ويُشغّل `rea-apk-analysis.yml`. يحتوي artifact `rea-results` على `help.txt` ونتيجة الفحص أو سبب تعذر تشغيله.

الـ workflow يحفظ ناتج `npx -y rea-agents@latest --help` أولاً، ثم لا يستعمل إلا أمراً يظهر صراحة في help. إن لم تعلن نسخة CLI أمراً لفحص Android APK، يفشل بوضوح ولا يخمّن اسماً أو ينشئ تقريراً وهمياً. استخدم التطبيق فقط لتحليل تطبيقات وملفات APK التي تملكها أو لديك تصريح صريح بفحصها.

## الوحدات
- `app`: واجهة Compose.
- `core:network`: Retrofit وKotlinx Serialization.
- `core:data`: التخزين المحلي الآمن.
- `feature:analysis`: ميزة التحليل.
