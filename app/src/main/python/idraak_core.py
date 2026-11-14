
import json
import urllib.request
import urllib.error
import urllib.parse

SUPPORTED_LANGUAGES = ["Hindi", "English", "Urdu", "Arabic"]


def classify(text):
    original = str(text or "").strip()
    t = original.lower()

    aliases = {
        "whatsapp": [
            "whatsapp", "व्हाट्सएप", "वाट्सएप",
            "واٹس ایپ", "واتساب"
        ],
        "youtube": [
            "youtube", "यूट्यूब", "یوٹیوب", "يوتيوب"
        ],
        "chrome": [
            "chrome", "क्रोम", "browser", "ब्राउज़र",
            "براؤزر", "متصفح"
        ],
        "telegram": [
            "telegram", "टेलीग्राम", "ٹیلیگرام", "تيليجرام"
        ],
        "settings": [
            "phone settings", "फोन की सेटिंग",
            "मोबाइल सेटिंग", "android settings",
            "فون کی سیٹنگ", "موبائل سیٹنگ",
            "إعدادات الهاتف", "إعدادات أندرويد"
        ]
    }

    open_words = [
        "open", "launch", "start",
        "खोलो", "खोल दो", "ओपन", "चालू करो", "खोलना",
        "کھولو", "کھول دیں", "شروع کرو",
        "افتح", "تشغيل"
    ]

    if any(word in t for word in open_words):
        for app, words in aliases.items():
            if any(word in t for word in words):
                return {
                    "type": "open_app",
                    "target": app
                }

    settings_phrases = [
        "idraak settings", "आईड्राक सेटिंग",
        "अपनी सेटिंग", "तुम्हारी सेटिंग",
        "آئیڈراک سیٹنگ", "إعدادات إدراك"
    ]

    if any(phrase in t for phrase in settings_phrases):
        return {"type": "idraak_settings"}

    return {
        "type": "ai",
        "text": original
    }


def reply(prompt, api_key, model="gemini-2.5-flash"):
    prompt = str(prompt or "").strip()
    api_key = str(api_key or "").strip()
    model = str(model or "gemini-2.5-flash").strip()

    if not api_key:
        return json.dumps({
            "status": "needs_api_key",
            "message": "पहले IDRAAK Settings में Gemini API key डालें।"
        }, ensure_ascii=False)

    if not prompt:
        return json.dumps({
            "status": "error",
            "message": "कृपया अपना सवाल दोबारा बताइए।"
        }, ensure_ascii=False)

    if not model:
        model = "gemini-2.5-flash"

    safe_model = urllib.parse.quote(model, safe="._-")
    safe_key = urllib.parse.quote(api_key, safe="")

    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + safe_model
        + ":generateContent?key="
        + safe_key
    )

    body = {
        "systemInstruction": {
            "parts": [{
                "text": (
                    "You are IDRAAK, a helpful multilingual Android assistant. "
                    "Understand and reply in Hindi, English, Urdu, or Arabic "
                    "according to the user's language. Be clear and respectful. "
                    "Do not claim an Android action succeeded unless the Android "
                    "application confirms it. Ask for confirmation before "
                    "sending messages, making payments, or performing sensitive "
                    "account or security actions. Never ask the user to share "
                    "passwords, OTPs, or private API keys in conversation."
                )
            }]
        },
        "contents": [{
            "role": "user",
            "parts": [{
                "text": prompt
            }]
        }],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 2048
        }
    }

    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            data = json.loads(
                response.read().decode("utf-8")
            )

        candidates = data.get("candidates", [])

        if not candidates:
            return json.dumps({
                "status": "api_error",
                "message": "Gemini ने कोई जवाब नहीं दिया। कृपया दोबारा कोशिश करें।"
            }, ensure_ascii=False)

        content = candidates[0].get("content", {})
        parts = content.get("parts", [])

        answer = " ".join(
            str(part.get("text", "")).strip()
            for part in parts
            if part.get("text")
        ).strip()

        if not answer:
            return json.dumps({
                "status": "api_error",
                "message": "Gemini का जवाब खाली आया। कृपया दोबारा कोशिश करें।"
            }, ensure_ascii=False)

        return json.dumps({
            "status": "success",
            "message": answer
        }, ensure_ascii=False)

    except urllib.error.HTTPError as error:
        try:
            detail = error.read().decode(
                "utf-8", errors="replace"
            )[:700]
        except Exception:
            detail = "No additional error details."

        return json.dumps({
            "status": "api_error",
            "message": (
                "Gemini HTTP "
                + str(error.code)
                + ": "
                + detail
            )
        }, ensure_ascii=False)

    except urllib.error.URLError:
        return json.dumps({
            "status": "error",
            "message": (
                "इंटरनेट कनेक्शन उपलब्ध नहीं है। "
                "कृपया अपना नेटवर्क जाँचें।"
            )
        }, ensure_ascii=False)

    except Exception as error:
        return json.dumps({
            "status": "error",
            "message": "IDRAAK Error: " + str(error)[:500]
        }, ensure_ascii=False)
