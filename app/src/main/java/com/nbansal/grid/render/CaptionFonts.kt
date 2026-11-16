package com.nbansal.grid.render

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.nbansal.grid.R
import com.nbansal.grid.sheet.CaptionFont

object CaptionFonts {
    const val CLEAN_ASSET = "fonts/SpaceGrotesk.ttf"

    /** Null for [CaptionFont.Clean], which lives in assets. */
    @FontRes
    fun resId(f: CaptionFont): Int? = when (f) {
        CaptionFont.Handwritten -> R.font.caveat
        CaptionFont.Script -> R.font.homemade_apple
        CaptionFont.Pencil -> R.font.reenie_beanie
        CaptionFont.Marker -> R.font.permanent_marker
        CaptionFont.Typewriter -> R.font.special_elite
        CaptionFont.Serif -> R.font.dm_serif_italic
        CaptionFont.Mono -> R.font.space_mono
        CaptionFont.Clean -> null
    }

    fun load(context: Context, f: CaptionFont): Typeface {
        val id = resId(f)
        return (if (id != null) ResourcesCompat.getFont(context, id)
        else runCatching { Typeface.createFromAsset(context.assets, CLEAN_ASSET) }.getOrNull())
            ?: Typeface.DEFAULT
    }
}
