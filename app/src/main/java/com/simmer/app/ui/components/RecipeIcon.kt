package com.simmer.app.ui.components

import com.simmer.app.R

/** Drawable for a recipe's `icon` name; unknown names fall back to the utensils. */
fun recipeIconRes(name: String): Int = when (name) {
    "flame" -> R.drawable.ic_flame
    "chef-hat" -> R.drawable.ic_chef_hat
    "egg" -> R.drawable.ic_egg
    "carrot" -> R.drawable.ic_carrot
    "zap" -> R.drawable.ic_zap
    "timer" -> R.drawable.ic_timer
    "layers" -> R.drawable.ic_layers
    "clock" -> R.drawable.ic_clock
    else -> R.drawable.ic_utensils
}
