package com.simmer.app.domain

/** Appliances ticked on first run, before the user has chosen anything. */
val DEFAULT_APPLIANCES: Set<String> = setOf("hob", "oven", "microwave")

/** Shortest query for which Your kitchen offers to add an appliance of the user's own. */
const val MIN_ADD_QUERY_LENGTH = 2

/** Prefix of the key of an appliance the user added: "c:Name". */
private const val CUSTOM_PREFIX = "c:"

/** The key of the appliance the user added under [name]. */
fun customKey(name: String): String = CUSTOM_PREFIX + name

/** One row of the Your kitchen checklist: [key] (catalogue key or "c:Name"), its name in the UI language and in English. */
data class ApplianceRow(val key: String, val label: String, val english: String) {
    /** True for an appliance the user added. */
    val isCustom: Boolean get() = key.startsWith(CUSTOM_PREFIX)

    companion object {
        /** The row of an appliance the user added: it has one name in every language. */
        fun custom(name: String) = ApplianceRow(customKey(name), label = name, english = name)
    }
}

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
        return copy(selected = selected + customKey(n), custom = custom + n)
    }
}

/** Checklist rows: the catalogue in order (each built by [catalogueRow] from its key), then the added appliances alphabetically. */
fun kitchenRows(custom: Set<String>, catalogueRow: (key: String) -> ApplianceRow): List<ApplianceRow> =
    APPLIANCE_CATALOGUE.map(catalogueRow) + custom.sorted().map(ApplianceRow::custom)
