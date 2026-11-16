package com.nbansal.grid.sheet

import com.nbansal.grid.layout.CardTemplate

private fun Card.retarget(t: CardTemplate, slots: List<Slot>): Card {
    val labelWasDefault = label == defaultLabel(templateId)
    return copy(
        templateId = t.id,
        slots = slots,
        caption = caption.take(t.captionMax),
        label = if (templateId == t.id || !labelWasDefault) label else defaultLabel(t.id),
        stats = if (stats.isEmpty() || templateId != t.id && stats == defaultStats(templateId)) defaultStats(t.id) else stats,
    )
}

/**
 * Changes the shape of the [targetIds] cards (all cards when empty).
 * single -> single keeps content; a multi-window target merges the targets' filled photos in order into
 * ceil(k/windows) cards (placed where the first target was); a single-window target splits a multi-photo
 * card into one card per filled photo.
 */
fun applyShape(cards: List<Card>, targetIds: Set<Long>, templateId: String, newId: () -> Long): List<Card> {
    val t = CardTemplate.byId(templateId)
    val targets = cards.filter { targetIds.isEmpty() || it.id in targetIds }
    if (targets.isEmpty() || targets.all { it.templateId == t.id }) return cards
    val targetSet = targets.map { it.id }.toSet()
    val n = t.windowCount

    if (n == 1) {
        return cards.flatMap { c ->
            if (c.id !in targetSet) return@flatMap listOf(c)
            val filled = c.filledSlots
            if (filled.size <= 1) listOf(c.retarget(t, listOf(filled.firstOrNull() ?: c.slots.firstOrNull() ?: Slot())))
            else filled.mapIndexed { i, s -> c.retarget(t, listOf(s)).let { if (i == 0) it else it.copy(id = newId()) } }
        }
    }

    // Multi-window: flatten filled slots of all targets, in order, remembering whose text each takes.
    val flat = targets.flatMap { c -> c.filledSlots.map { it to c } }
    val chunks = flat.chunked(n)
    val merged: List<Card> = if (chunks.isEmpty()) {
        listOf(targets.first().retarget(t, emptySlots(t)))
    } else {
        chunks.mapIndexed { i, chunk ->
            val owner = chunk.first().second
            val slots = chunk.map { it.first } + List(n - chunk.size) { Slot() }
            owner.retarget(t, slots).copy(id = if (i == 0) targets.first().id else newId())
        }
    }
    val out = mutableListOf<Card>()
    var emitted = false
    for (c in cards) {
        if (c.id !in targetSet) out += c
        else if (!emitted) { out += merged; emitted = true }
    }
    return out
}

/** The shared template of all [cards], or null when mixed/empty. */
fun commonTemplate(cards: List<Card>): CardTemplate? =
    cards.map { it.templateId }.distinct().singleOrNull()?.let { CardTemplate.byId(it) }
