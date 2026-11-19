package com.example.data

data class VoiceLanguageOption(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val localeTag: String,
    val greetingTemplate: String,
    val closingMessage: String,
    val romanizedGreetingFallback: String = greetingTemplate,
    val romanizedClosingFallback: String = closingMessage
) {
    fun formatGreeting(status: String): String {
        return greetingTemplate.replace("{status}", status)
    }

    fun formatRomanizedGreeting(status: String): String {
        return romanizedGreetingFallback.replace("{status}", status)
    }
}

object VoiceLanguages {
    const val ID_TELUGU = "te"
    const val ID_ENGLISH = "en"
    const val ID_HINDI = "hi"
    const val ID_TAMIL = "ta"
    const val ID_KANNADA = "kn"
    const val ID_CUSTOM = "custom"

    val builtInList: List<VoiceLanguageOption> = listOf(
        VoiceLanguageOption(
            id = ID_TELUGU,
            displayName = "తెలుగు (Telugu)",
            shortLabel = "తెలుగు",
            localeTag = "te-IN",
            greetingTemplate = "నమస్కారం, వారు ఇప్పుడు {status}. ప్రస్తుతం బిజీగా ఉన్నారు. దయచేసి బీప్ తర్వాత మీ మెసేజ్ చెప్పండి, నేను నోట్ చేసి వారికి పంపుతాను.",
            closingMessage = "ధన్యవాదాలు, మీ మెసేజ్ నోట్ చేసుకున్నాను. వారు ఫ్రీ అయిన తర్వాత మీకు కాల్ చేస్తారు.",
            romanizedGreetingFallback = "Namaskaram, varu ippudu {status}. Prasthutham busy ga unnaru. Dayachesi beep tarvata mee message cheppandi, nenu note chesi pampisthanu.",
            romanizedClosingFallback = "Dhanyavaadalu, mee message note chesukonnanu. Varu free ayina tarvata meeku call chestharu."
        ),
        VoiceLanguageOption(
            id = ID_ENGLISH,
            displayName = "English",
            shortLabel = "English",
            localeTag = "en-IN",
            greetingTemplate = "Hello, he is currently in {status}. He is busy. Please tell your message after beep, I will note and send to him.",
            closingMessage = "Thank you, message noted. He will call you back when free."
        ),
        VoiceLanguageOption(
            id = ID_HINDI,
            displayName = "हिन्दी (Hindi)",
            shortLabel = "हिन्दी",
            localeTag = "hi-IN",
            greetingTemplate = "नमस्ते, वह अभी {status} में व्यस्त हैं। कृपया बीप के बाद अपना संदेश बोलें, मैं इसे नोट करके उन्हें भेज दूँगा।",
            closingMessage = "धन्यवाद, आपका संदेश नोट कर लिया गया है। खाली होते ही वे आपको वापस कॉल करेंगे।",
            romanizedGreetingFallback = "Namaste, vah abhi {status} mein vyast hain. Kripya beep ke baad apna sandesh bolein, main note karke bhej dunga.",
            romanizedClosingFallback = "Dhanyawad, aapka sandesh note kar liya gaya hai. Khaali hote hi ve aapko call karenge."
        ),
        VoiceLanguageOption(
            id = ID_TAMIL,
            displayName = "தமிழ் (Tamil)",
            shortLabel = "தமிழ்",
            localeTag = "ta-IN",
            greetingTemplate = "வணக்கம், அவர்கள் தற்போது {status} இல் பிஸியாக உள்ளார்கள். பீப் சத்தத்திற்குப் பிறகு உங்கள் செய்தியைச் சொல்லுங்கள், நான் குறித்து அவர்களுக்கு அனுப்புகிறேன்.",
            closingMessage = "நன்றி, உங்கள் செய்தி குறிக்கப்பட்டது. அவர்கள் ஓய்வாக இருக்கும்போது மீண்டும் அழைப்பார்கள்.",
            romanizedGreetingFallback = "Vanakkam, avargal tharpothu {status} il busy aga ullargal. Beep satham ketta piragu ungal message sollungal.",
            romanizedClosingFallback = "Nandri, ungal message note seiyappattathu. Avargal free aanathum call seivargal."
        ),
        VoiceLanguageOption(
            id = ID_KANNADA,
            displayName = "ಕನ್ನಡ (Kannada)",
            shortLabel = "ಕನ್ನಡ",
            localeTag = "kn-IN",
            greetingTemplate = "ನಮಸ್ಕಾರ, ಅವರು ಈಗ {status} ನಲ್ಲಿ ಬ್ಯುಸಿಯಾಗಿದ್ದಾರೆ. ದಯವಿಟ್ಟು ಬೀಪ್ ಶಬ್ದದ ನಂತರ ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ಹೇಳಿ, ನಾನು ಅದನ್ನು ನೋಟ್ ಮಾಡಿ ಅವರಿಗೆ ಕಳುಹಿಸುತ್ತೇನೆ.",
            closingMessage = "ಧನ್ಯವಾದಗಳು, ನಿಮ್ಮ ಸಂದೇಶವನ್ನು ನೋಟ್ ಮಾಡಲಾಗಿದೆ. ಅವರು ಫ್ರೀ ಆದ ನಂತರ ನಿಮಗೆ ವಾಪಸ್ ಕರೆ ಮಾಡುತ್ತಾರೆ.",
            romanizedGreetingFallback = "Namaskara, avaru eega {status} nalli busy agiddare. Dayavittu beep nantara nimma message heli.",
            romanizedClosingFallback = "Dhanyavadagalu, nimma message note madalagide. Avaru free ada nantara call maduttare."
        )
    )

    val speechLocaleChoices: List<Pair<String, String>> = listOf(
        "te-IN" to "Telugu (te-IN)",
        "en-IN" to "English India (en-IN)",
        "hi-IN" to "Hindi (hi-IN)",
        "ta-IN" to "Tamil (ta-IN)",
        "kn-IN" to "Kannada (kn-IN)",
        "ml-IN" to "Malayalam (ml-IN)",
        "mr-IN" to "Marathi (mr-IN)"
    )

    fun findBuiltIn(id: String): VoiceLanguageOption? = builtInList.firstOrNull { it.id == id }
}
