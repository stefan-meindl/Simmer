package com.simmer.app.domain

/** Appliances ticked on first run, before the user has chosen anything. */
val DEFAULT_APPLIANCES: Set<String> = setOf("hob", "oven", "microwave")

/** Shortest query for which Your kitchen offers to add an appliance of the user's own. */
const val MIN_ADD_QUERY_LENGTH = 2

/** One row of the Your kitchen checklist: [key] (catalogue key or "c:Name"), its name in the UI language and in English. */
data class ApplianceRow(val key: String, val label: String, val english: String)

/** Rows whose current-language or English name contains [query] (case-insensitive); all rows for a blank query. */
fun matchingAppliances(rows: List<ApplianceRow>, query: String): List<ApplianceRow> {
    val q = query.trim()
    if (q.isEmpty()) return rows
    return rows.filter { it.label.contains(q, ignoreCase = true) || it.english.contains(q, ignoreCase = true) }
}

/**
 * The name to offer as "Add '...'" for [query]: the trimmed query when it has at least
 * [MIN_ADD_QUERY_LENGTH] characters and is not exactly the name of a row; otherwise null.
 */
fun addOffer(rows: List<ApplianceRow>, query: String): String? {
    val q = query.trim()
    if (q.length < MIN_ADD_QUERY_LENGTH) return null
    val exact = rows.any { it.label.equals(q, ignoreCase = true) || it.english.equals(q, ignoreCase = true) }
    return if (exact) null else q
}

/**
 * What the user owns ([selected]: catalogue keys and "c:Name" keys) and the names of the appliances
 * they added ([custom]), which stay in the checklist even when unticked.
 */
data class KitchenChoice(val selected: Set<String>, val custom: Set<String>) {

    fun toggle(key: String): KitchenChoice =
        copy(selected = if (key in selected) selected - key else selected + key)

    /** Adds [name] (trimmed) to the checklist and ticks it. */
    fun addCustom(name: String): KitchenChoice {
        val n = name.trim()
        return copy(selected = selected + "c:$n", custom = custom + n)
    }

    companion object {
        val initial = KitchenChoice(DEFAULT_APPLIANCES, emptySet())
    }
}

/** Checklist keys: the catalogue in order, then the added appliances ("c:Name") alphabetically. */
fun kitchenKeys(custom: Set<String>): List<String> =
    APPLIANCE_CATALOGUE + custom.sorted().map { "c:$it" }
