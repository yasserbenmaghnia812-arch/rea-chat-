# تشغيل محرك REA محلياً على Android عبر Termux

> هذه الطريقة مجانية ولا تحتاج Render أو بطاقة بنكية. شغّلها على الهاتف نفسه الذي عليه تطبيق REA Chat.

## المتطلبات
- Termux من F-Droid أو GitHub الرسمي.
- مساحة تخزين كافية وذاكرة متاحة.
- Android 15.

## التثبيت
نفّذ الأوامر التالية داخل Termux:

```bash
pkg update -y && pkg upgrade -y
pkg install -y nodejs-lts openjdk-21 curl
npm install -g rea-agents
mkdir -p ~/rea-chat
cd ~/rea-chat
```

نزّل ملف JADX الرسمي وثبّت بصمته:

```bash
curl -fL -o jadx-headless-mcp-0.7.1-all.jar https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar
echo "6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75  jadx-headless-mcp-0.7.1-all.jar" | sha256sum -c
export REA_JADX_MCP_JAR="$HOME/rea-chat/jadx-headless-mcp-0.7.1-all.jar"
```

## تشغيل جسر API المحلي
نزّل مجلد `termux-rea-bridge` من المستودع إلى الهاتف، ثم داخل المجلد:

```bash
npm install
node server.js
```

يستمع الخادم على `127.0.0.1:8765`. افحصه من Termux:

```bash
curl http://127.0.0.1:8765/health
```

## حدود مهمة
- لا تفتح المنفذ على الإنترنت؛ الخادم مصمم للاتصال المحلي فقط.
- لا توقف Termux أثناء التحليل.
- تحليل APK كبير قد يستهلك ذاكرة الهاتف أو يتوقف؛ ابدأ بملف صغير.
- افحص فقط ملفات APK التي تملكها أو لديك إذن بتحليلها.
- هذا الجسر المحلي لا يوفر بعد خادماً عاماً أو MCP عبر الإنترنت.
