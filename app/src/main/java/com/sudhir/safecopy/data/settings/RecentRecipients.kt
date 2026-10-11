package com.sudhir.safecopy.data.settings

/** Most-recent-first recipient list (spec 10: last 10 only), stored as one string. */
object RecentRecipients {
    const val MAX = 10

    /** Unit separator: can't be typed into a text field, so it never collides with a name. */
    private const val SEP = '\u001F'

    fun add(list: List<String>, recipient: String): List<String> {
        val r = recipient.trim().replace(Regex("\\s+"), " ")
        if (r.isEmpty()) return list
        return (listOf(r) + list.filterNot { it.equals(r, ignoreCase = true) }).take(MAX)
    }

    /** Suggestions for what's typed so far: matches anywhere, case-insensitive, exact match hidden. */
    fun suggest(list: List<String>, typed: String): List<String> {
        val t = typed.trim()
        return list.filter { it.contains(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }
    }

    fun encode(list: List<String>): String = list.joinToString(SEP.toString())

    fun decode(stored: String?): List<String> =
        stored?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()
}
