package com.idraak.quantum.core

import java.util.Locale

/** Safe offline Kotlin tutor. It reviews text but never compiles or executes supplied code. */
class CodeLearningEngine {
    fun handle(raw: String): String? {
        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)
        if (lower in listOf("coding help", "teach me coding", "learn coding", "code help", "کوڈنگ سکھاؤ", "کوڈنگ مدد", "مجھے کوڈنگ سکھاؤ", "کوڈنگ کیا ہے")) return helpText()

        val reviewPrefix = listOf("review code:", "check code:", "analyze code:", "کوڈ چیک کرو:", "کوڈ کا جائزہ لو:")
            .firstOrNull { lower.startsWith(it) }
        if (reviewPrefix != null) {
            val source = command.drop(reviewPrefix.length).trim()
            if (source.isBlank()) return "کوڈ چیک کرنے کے لیے اس طرح بھیجیں: review code: [اپنا Kotlin code]"
            return review(source)
        }

        val explainPrefix = listOf("explain kotlin ", "teach kotlin ", "kotlin samjhao ", "kotlin سکھاؤ ", "سمجھاؤ ")
            .firstOrNull { lower.startsWith(it) }
        if (explainPrefix != null) {
            val topic = command.drop(explainPrefix.length).trim().lowercase(Locale.ROOT)
            if (topic.isBlank()) return "کون سا موضوع سمجھاؤں؟ مثال: explain kotlin variables"
            return explain(topic)
        }
        if (lower in listOf("kotlin basics", "kotlin سکھاؤ", "kotlin sikhao", "android coding سکھاؤ")) return explain("basics")
        return null
    }

    private fun helpText() = """
        میں آف لائن کوڈنگ میں یہ مدد کر سکتا ہوں:
        1. Kotlin سیکھنا: explain kotlin variables
        2. Functions سمجھنا: explain kotlin functions
        3. Android basics: explain kotlin android
        4. کوڈ کی ابتدائی جانچ: review code: [اپنا کوڈ]

        میں مثال، مسئلے کی وضاحت اور درست کرنے کا مشورہ دوں گا۔
        یہ ابتدائی static review ہے؛ اصل compile اور tests چلائے بغیر کامیابی کی ضمانت نہیں دوں گا۔
    """.trimIndent()

    private fun explain(topic: String): String = when {
        topic.contains("variable") || topic.contains("var") || topic.contains("val") || topic.contains("متغیر") ->
            """
            Kotlin میں variable ڈیٹا محفوظ کرتا ہے۔

            val name = "Idraak"   // دوبارہ assign نہیں ہو سکتا
            var count = 1        // بعد میں تبدیل ہو سکتا
            count = count + 1

            جہاں ممکن ہو val استعمال کریں؛ صرف ضرورت پر var رکھیں۔
            """.trimIndent()
        topic.contains("function") || topic.contains("فنکشن") ->
            """
            Function ایسا code block ہے جسے نام دے کر دوبارہ استعمال کیا جا سکتا ہے۔

            fun greet(name: String): String {
                return "Assalam-o-alaikum, ${'"
            }

            println(greet("Idraak"))

            fun function بناتا ہے، name اس کا input ہے، اور String return type ہے۔
            """.trimIndent()
        topic.contains("loop") || topic.contains("for") || topic.contains("while") ->
            """
            Loop کسی کام کو بار بار چلاتا ہے۔

            for (number in 1..3) {
                println(number)
            }

            یہ 1، 2 اور 3 پر عمل کرے گا۔ غیر ضروری infinite loops سے بچیں۔
            """.trimIndent()
        topic.contains("class") || topic.contains("object") ->
            """
            Class ایک blueprint ہے جس سے objects بنتے ہیں۔

            class Assistant(val name: String) {
                fun introduce(): String {
                    return "I am $name"
                }
            }

            val idraak = Assistant("Idraak")
            println(idraak.introduce())
            """.trimIndent()
        topic.contains("android") || topic.contains("activity") ->
            """
            Android میں Activity ایک screen کی نمائندگی کرتی ہے۔
            onCreate() screen بننے پر چلتا ہے۔

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // یہاں screen تیار کریں
            }

            Android permissions اور lifecycle کا خیال رکھیں۔
            """.trimIndent()
        else ->
            """
            Kotlin ایک modern programming language ہے جو Android apps میں استعمال ہوتی ہے۔

            مثال:
            fun main() {
                val message = "Hello Idraak"
                println(message)
            }

            خاص موضوع: explain kotlin variables / functions / loops / classes / android
            """.trimIndent()
    }

    private fun review(source: String): String {
        if (source.length > 20000) return "یہ code بہت لمبا ہے۔ اسے چھوٹے حصوں میں بھیجیں (زیادہ سے زیادہ 20,000 characters فی جائزہ)۔"
        if (source.isBlank()) return "کوڈ خالی ہے۔"
        val findings = mutableListOf<String>()
        if (source.count { it == '{' } != source.count { it == '}' }) findings += "Curly braces { } کی تعداد برابر نہیں؛ blocks چیک کریں۔"
        if (source.count { it == '(' } != source.count { it == ')' }) findings += "Parentheses ( ) کی تعداد برابر نہیں؛ function calls اور conditions چیک کریں۔"
        if (Regex("\\bvar\\s+\\w+").containsMatchIn(source) && !Regex("\\bvar\\s+\\w+\\s*=").containsMatchIn(source)) {
            findings += "کسی var declaration میں '=' یا ابتدائی value غائب ہو سکتی ہے۔"
        }
        if (source.contains("!!")) findings += "!! استعمال ہوا ہے؛ null ہونے پر crash ہو سکتا ہے۔ Safe call ?. یا null check پر غور کریں۔"
        if (source.contains("Thread.sleep")) findings += "Thread.sleep نظر آیا؛ Android main thread پر UI freeze ہو سکتی ہے۔"
        if (source.contains("TODO(")) findings += "TODO() موجود ہے؛ چلانے پر exception دے سکتا ہے۔"
        return if (findings.isEmpty()) {
            "ابتدائی جانچ میں کوئی عام مسئلہ نہیں ملا۔ یہ compile success کا ثبوت نہیں؛ Gradle build اور tests سے تصدیق ہوگی۔"
        } else {
            "ابتدائی Code Review:\n" + findings.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n") +
                "\n\nیہ static hints ہیں، مکمل Kotlin compiler نہیں۔ اصل build سے تصدیق ضروری ہے۔"
        }
    }
}
}name"
            }

            println(greet("Idraak"))

            fun function بناتا ہے، name اس کا input ہے، اور String return type ہے۔
            """.trimIndent()
        topic.contains("loop") || topic.contains("for") || topic.contains("while") ->
            """
            Loop کسی کام کو بار بار چلاتا ہے۔

            for (number in 1..3) {
                println(number)
            }

            یہ 1، 2 اور 3 پر عمل کرے گا۔ غیر ضروری infinite loops سے بچیں۔
            """.trimIndent()
        topic.contains("class") || topic.contains("object") ->
            """
            Class ایک blueprint ہے جس سے objects بنتے ہیں۔

            class Assistant(val name: String) {
                fun introduce(): String {
                    return "I am ${'"
                }
            }

            val idraak = Assistant("Idraak")
            println(idraak.introduce())
            """.trimIndent()
        topic.contains("android") || topic.contains("activity") ->
            """
            Android میں Activity ایک screen کی نمائندگی کرتی ہے۔
            onCreate() screen بننے پر چلتا ہے۔

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // یہاں screen تیار کریں
            }

            Android permissions اور lifecycle کا خیال رکھیں۔
            """.trimIndent()
        else ->
            """
            Kotlin ایک modern programming language ہے جو Android apps میں استعمال ہوتی ہے۔

            مثال:
            fun main() {
                val message = "Hello Idraak"
                println(message)
            }

            خاص موضوع: explain kotlin variables / functions / loops / classes / android
            """.trimIndent()
    }

    private fun review(source: String): String {
        if (source.length > 20000) return "یہ code بہت لمبا ہے۔ اسے چھوٹے حصوں میں بھیجیں (زیادہ سے زیادہ 20,000 characters فی جائزہ)۔"
        if (source.isBlank()) return "کوڈ خالی ہے۔"
        val findings = mutableListOf<String>()
        if (source.count { it == '{' } != source.count { it == '}' }) findings += "Curly braces { } کی تعداد برابر نہیں؛ blocks چیک کریں۔"
        if (source.count { it == '(' } != source.count { it == ')' }) findings += "Parentheses ( ) کی تعداد برابر نہیں؛ function calls اور conditions چیک کریں۔"
        if (Regex("\\bvar\\s+\\w+").containsMatchIn(source) && !Regex("\\bvar\\s+\\w+\\s*=").containsMatchIn(source)) {
            findings += "کسی var declaration میں '=' یا ابتدائی value غائب ہو سکتی ہے۔"
        }
        if (source.contains("!!")) findings += "!! استعمال ہوا ہے؛ null ہونے پر crash ہو سکتا ہے۔ Safe call ?. یا null check پر غور کریں۔"
        if (source.contains("Thread.sleep")) findings += "Thread.sleep نظر آیا؛ Android main thread پر UI freeze ہو سکتی ہے۔"
        if (source.contains("TODO(")) findings += "TODO() موجود ہے؛ چلانے پر exception دے سکتا ہے۔"
        return if (findings.isEmpty()) {
            "ابتدائی جانچ میں کوئی عام مسئلہ نہیں ملا۔ یہ compile success کا ثبوت نہیں؛ Gradle build اور tests سے تصدیق ہوگی۔"
        } else {
            "ابتدائی Code Review:\n" + findings.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n") +
                "\n\nیہ static hints ہیں، مکمل Kotlin compiler نہیں۔ اصل build سے تصدیق ضروری ہے۔"
        }
    }
}
}name"
                }
            }

            val idraak = Assistant("Idraak")
            println(idraak.introduce())
            """.trimIndent()
        topic.contains("android") || topic.contains("activity") ->
            """
            Android میں Activity ایک screen کی نمائندگی کرتی ہے۔
            onCreate() screen بننے پر چلتا ہے۔

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // یہاں screen تیار کریں
            }

            Android permissions اور lifecycle کا خیال رکھیں۔
            """.trimIndent()
        else ->
            """
            Kotlin ایک modern programming language ہے جو Android apps میں استعمال ہوتی ہے۔

            مثال:
            fun main() {
                val message = "Hello Idraak"
                println(message)
            }

            خاص موضوع: explain kotlin variables / functions / loops / classes / android
            """.trimIndent()
    }

    private fun review(source: String): String {
        if (source.length > 20000) return "یہ code بہت لمبا ہے۔ اسے چھوٹے حصوں میں بھیجیں (زیادہ سے زیادہ 20,000 characters فی جائزہ)۔"
        if (source.isBlank()) return "کوڈ خالی ہے۔"
        val findings = mutableListOf<String>()
        if (source.count { it == '{' } != source.count { it == '}' }) findings += "Curly braces { } کی تعداد برابر نہیں؛ blocks چیک کریں۔"
        if (source.count { it == '(' } != source.count { it == ')' }) findings += "Parentheses ( ) کی تعداد برابر نہیں؛ function calls اور conditions چیک کریں۔"
        if (Regex("\\bvar\\s+\\w+").containsMatchIn(source) && !Regex("\\bvar\\s+\\w+\\s*=").containsMatchIn(source)) {
            findings += "کسی var declaration میں '=' یا ابتدائی value غائب ہو سکتی ہے۔"
        }
        if (source.contains("!!")) findings += "!! استعمال ہوا ہے؛ null ہونے پر crash ہو سکتا ہے۔ Safe call ?. یا null check پر غور کریں۔"
        if (source.contains("Thread.sleep")) findings += "Thread.sleep نظر آیا؛ Android main thread پر UI freeze ہو سکتی ہے۔"
        if (source.contains("TODO(")) findings += "TODO() موجود ہے؛ چلانے پر exception دے سکتا ہے۔"
        return if (findings.isEmpty()) {
            "ابتدائی جانچ میں کوئی عام مسئلہ نہیں ملا۔ یہ compile success کا ثبوت نہیں؛ Gradle build اور tests سے تصدیق ہوگی۔"
        } else {
            "ابتدائی Code Review:\n" + findings.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n") +
                "\n\nیہ static hints ہیں، مکمل Kotlin compiler نہیں۔ اصل build سے تصدیق ضروری ہے۔"
        }
    }
}
