package com.nbansal.grid.filter

import kotlinx.serialization.Serializable

/**
 * What the user picked: a preset plus adjustments. The three toggles add the recipe's grain / vignette /
 * light leak (or a mild default when the recipe has none). [brightness], [contrast] and [warmth] are -1..1.
 */
@Serializable
data class FilterSettings(
    val filterId: String = FilmFilter.None.id,
    val intensity: Float = 1f,
    val grain: Boolean = false,
    val vignette: Boolean = false,
    val lightLeak: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val warmth: Float = 0f,
) {
    val filter: FilmFilter get() = FilmFilter.byId(filterId)

    val isIdentity: Boolean
        get() = filter.recipe.isNeutral && !grain && !vignette && !lightLeak &&
            brightness == 0f && contrast == 0f && warmth == 0f

    /** Picks [f], switching the toggles to what the preset naturally has. */
    fun withFilter(f: FilmFilter): FilterSettings = copy(
        filterId = f.id,
        grain = f.recipe.grainAmount > 0f,
        vignette = f.recipe.vignette > 0f,
        lightLeak = f.recipe.lightLeak > 0f,
    )
}
