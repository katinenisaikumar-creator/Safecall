package com.example.data

data class SafeCallMode(
    val id: String,
    val name: String,
    val defaultStatus: String,
    val iconKey: String = ICON_CUSTOM,
    val isBuiltIn: Boolean = false
) {
    companion object {
        const val ICON_SCHOOL = "school"
        const val ICON_EXAM = "exam"
        const val ICON_STUDY = "study"
        const val ICON_SLEEP = "sleep"
        const val ICON_TRAVEL = "travel"
        const val ICON_WORK = "work"
        const val ICON_CUSTOM = "custom"
    }
}

/**
 * Add any new built-in mode to [list] below — it will automatically appear
 * in the mode selector bar on the Home Screen.
 */
object DefaultSafeCallModes {
    val list: List<SafeCallMode> = listOf(
        SafeCallMode(
            id = "college",
            name = "College Mode",
            defaultStatus = "Lab Session lo unna",
            iconKey = SafeCallMode.ICON_SCHOOL,
            isBuiltIn = true
        ),
        SafeCallMode(
            id = "exam",
            name = "Exam Mode",
            defaultStatus = "Exam Hall lo unna",
            iconKey = SafeCallMode.ICON_EXAM,
            isBuiltIn = true
        ),
        SafeCallMode(
            id = "study",
            name = "Study Mode",
            defaultStatus = "Library lo chaduvuthunna",
            iconKey = SafeCallMode.ICON_STUDY,
            isBuiltIn = true
        ),
        SafeCallMode(
            id = "sleep",
            name = "Sleep Mode",
            defaultStatus = "Hostel lo nidrapothunna",
            iconKey = SafeCallMode.ICON_SLEEP,
            isBuiltIn = true
        )
    )
}
