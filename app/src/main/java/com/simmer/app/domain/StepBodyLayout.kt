package com.simmer.app.domain

import com.simmer.app.domain.model.BodySegment
import com.simmer.app.domain.model.ResolvedIngredient

/** Placeholder char the step text carries where the lightbulb icon is drawn (object replacement char). */
const val BODY_TIP_MARK = '￼'

/**
 * One Link in the laid-out step text.
 *
 * `[start, underlineEnd)` is the word plus its "(amount)" and is what gets the orange underline;
 * `[start, end)` also holds the gap and [BODY_TIP_MARK] of a link with tips, and is the tap target.
 */
data class BodyLink(
    val id: String,
    val start: Int,
    val underlineEnd: Int,
    val end: Int,
    /** Only a link whose ingredient has tips can be tapped. */
    val tappable: Boolean,
)

data class BodyLayout(val text: String, val links: List<BodyLink>)

/** Step text with each Link followed by its scaled amount, and the ranges the screen draws and taps. */
fun layoutBody(segs: List<BodySegment>, ingredients: Map<String, ResolvedIngredient>): BodyLayout {
    val links = mutableListOf<BodyLink>()
    val text = buildString {
        segs.forEach { seg ->
            when (seg) {
                is BodySegment.TextSegment -> append(seg.text)
                is BodySegment.IngredientLinkSegment -> {
                    val ingredient = ingredients[seg.id]
                    if (ingredient == null) {
                        append(seg.text)
                    } else {
                        val start = length
                        append(seg.text)
                        if (ingredient.amount.isNotBlank()) append(" (").append(ingredient.amount).append(')')
                        val underlineEnd = length
                        val tappable = ingredient.tips.isNotEmpty()
                        if (tappable) append(' ').append(BODY_TIP_MARK)
                        links += BodyLink(seg.id, start, underlineEnd, length, tappable)
                    }
                }
            }
        }
    }
    return BodyLayout(text, links)
}

/** Where the Tips tooltip goes (px, window coordinates) and where its arrow sits from the tooltip's left edge. */
data class TooltipPlacement(val x: Int, val y: Int, val below: Boolean, val arrowX: Int)

/**
 * Centres the tooltip on the tapped word, clamps it to the window [margin], puts it [gap] below the word
 * and flips it above when it does not fit below but does fit above.
 */
fun placeTooltip(
    anchorLeft: Int,
    anchorTop: Int,
    anchorRight: Int,
    anchorBottom: Int,
    width: Int,
    height: Int,
    windowWidth: Int,
    windowHeight: Int,
    margin: Int,
    gap: Int,
    arrowInset: Int,
): TooltipPlacement {
    val center = (anchorLeft + anchorRight) / 2
    val x = (center - width / 2).coerceIn(margin, maxOf(margin, windowWidth - width - margin))
    var y = anchorBottom + gap
    var below = true
    if (y + height > windowHeight - margin && anchorTop - gap - height > margin) {
        y = anchorTop - gap - height
        below = false
    }
    return TooltipPlacement(x, y, below, (center - x).coerceIn(arrowInset, maxOf(arrowInset, width - arrowInset)))
}
