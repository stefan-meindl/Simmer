package com.simmer.app.domain

/** A Shopping list entry. Recipe items carry the source [recipeId]; manual items have none. */
data class ShoppingItem(
    val id: String,
    val recipeId: String?,
    val name: String,
    val amount: String,
    val aisle: String,
    /** Source recipe title for recipe items. */
    val note: String,
    val checked: Boolean,
)

data class AisleGroup(val aisle: String, val items: List<ShoppingItem>)

/** What the Shopping list tab shows: aisle groups, manual items, and the checked "In your basket" items. */
data class ShoppingListView(
    val aisles: List<AisleGroup>,
    val manual: List<ShoppingItem>,
    val basket: List<ShoppingItem>,
) {
    /** Items still to buy; drives the tab badge. */
    val uncheckedCount: Int get() = aisles.sumOf { it.items.size } + manual.size
}

/**
 * Aisles in the order a shopper walks the shop, so the list reads the same whichever recipes were added first.
 * Aisles not listed here (e.g. from a User Recipe) follow alphabetically.
 */
private val AISLE_ORDER = listOf(
    "Produce", "Meat", "Chilled", "Dairy", "Dairy & eggs", "Baking", "Pantry", "Tins & jars", "World foods", "Spices", "Drinks",
)

private val aisleComparator = compareBy<String>(
    { AISLE_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
    { it.lowercase() },
)

/** Items still to buy. */
fun uncheckedCount(items: List<ShoppingItem>): Int = items.count { !it.checked }

/** Arranges [items] into aisle groups (shop walk order), manual items and the basket. */
fun shoppingListView(items: List<ShoppingItem>): ShoppingListView {
    val (basket, open) = items.partition { it.checked }
    val (recipeItems, manual) = open.partition { it.recipeId != null }
    return ShoppingListView(
        aisles = recipeItems.groupBy { it.aisle }.toSortedMap(aisleComparator).map { (aisle, group) -> AisleGroup(aisle, group) },
        manual = manual,
        basket = basket,
    )
}
