from kivy.utils import platform

class VoiceManager:
    def __init__(self, db_manager):
        self.db = db_manager
        self.tts = None
        if platform == "android":
            try:
                from jnius import autoclass
                self.tts = autoclass("android.speech.tts.TextToSpeech")(
                    autoclass("org.kivy.android.PythonActivity").mActivity, None
                )
            except Exception as e:
                print("TTS Init Error:", e)

    def speak(self, text):
        if self.db.get_setting("voice_enabled", "1") != "1" or not self.tts:
            return
        try:
            cleaned = text.replace("*", "").replace("#", "").replace("`", "")
            self.tts.speak(cleaned, 0, None, None)
        except Exception as e:
            print("Speak Error:", e)

    def stop_speaking(self):
        if self.tts:
            try:
                self.tts.stop()
            except:
                pass

    def start_speech_to_text(self, on_result_callback):
        if platform != "android":
            on_result_callback(False, "Voice input sirf Android device par chalta hai Boss!")
            return
        
        try:
            from jnius import autoclass
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            Intent = autoclass('android.content.Intent')
            RecognizerIntent = autoclass('android.speech.RecognizerIntent')
            
            activity = PythonActivity.mActivity
            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Idraak sun raha hai, boliye Sameer Boss...")
            
            activity.startActivity(intent)
            on_result_callback(True, "🎙 Listening Sameer Boss...")
        except Exception as e:
            on_result_callback(False, f"STT Error: {str(e)}")
