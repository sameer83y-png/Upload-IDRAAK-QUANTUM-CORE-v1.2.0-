import json
import urllib.request
import urllib.error

SUPPORTED_LANGUAGES = ["Hindi", "English", "Urdu", "Arabic"]

def classify(text):
    t = text.lower().strip()

    aliases = {
        "whatsapp": ["whatsapp", "व्हाट्सएप", "वाट्सएप"],
        "youtube": ["youtube", "यूट्यूब"],
        "chrome": ["chrome", "क्रोम", "browser", "ब्राउज़र"],
        "telegram": ["telegram", "टेलीग्राम"],
        "settings": ["phone settings", "फोन की सेटिंग",
                     "मोबाइल सेटिंग", "android settings"]
    }

    open_words = [
        "open", "launch", "start", "खोलो", "खोल दो",
        "ओपन", "चालू करो", "खोलना"
    ]

    if any(word in t for word in open_words):
        for app, words in aliases.items():
            if any(word in t for word in words):
                return {"type": "open_app", "target": app}

    if any(x in t for x in [
        "idraak settings", "आईड्राक सेटिंग",
        "अपनी सेटिंग", "तुम्हारी सेटिंग"
    ]):
        return {"type": "idraak_settings"}

    return {"type": "ai", "text": text}


def reply(prompt, api_key, model="gemini-2.5-flash"):
    if not api_key:
        return json.dumps({
            "status": "needs_api_key",
            "message": "पहले IDRAAK Settings में Gemini API key डालें।"
        }, ensure_ascii=False)

    model = model.strip() or "gemini-2.5-flash"

    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + model + ":generateContent?key=" + api_key
    )

    body = {
        "contents": [{
            "parts": [{
                "text": (
                    "You are IDRAAK, a helpful multilingual Android assistant. "
                    "Reply in the user's language. Never claim an Android action "
                    "succeeded unless the Android app confirms it. "
                    "Ask confirmation before sending messages, payments, "
                    "or other sensitive actions.\n\nUser: " + prompt
                )
            }]
        }]
    }

    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            data = json.loads(response.read().decode("utf-8"))

        answer = data["candidates"][0]["content"]["parts"][0]["text"]
        return json.dumps({
            "status": "success",
            "message": answer
        }, ensure_ascii=False)

    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:700]
        return json.dumps({
            "status": "api_error",
            "message": "Gemini HTTP " + str(error.code) + ": " + detail
        }, ensure_ascii=False)

    except Exception as error:
        return json.dumps({
            "status": "error",
            "message": "Network/API error: " + str(error)
        }, ensure_ascii=False)
