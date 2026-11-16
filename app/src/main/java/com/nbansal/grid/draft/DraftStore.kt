package com.nbansal.grid.draft

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Drafts live at `<root>/<id>/draft.json` with photos in `<root>/<id>/photos/`. Pure JVM so it is unit-testable. */
class DraftStore(val root: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun dir(id: String) = File(root, id)
    fun photosDir(id: String) = File(dir(id), "photos")
    private fun file(id: String) = File(dir(id), "draft.json")

    fun fileUri(f: File) = "file://" + f.absolutePath

    private fun prefix(id: String) = "file://" + photosDir(id).absolutePath + "/"

    /** Atomic: written to a temp file then renamed over draft.json. */
    fun save(d: Draft) {
        val dir = dir(d.id)
        dir.mkdirs()
        val rel = d.mapUris { if (it.startsWith(prefix(d.id))) "photos/" + it.removePrefix(prefix(d.id)) else it }
        val tmp = File(dir, "draft.json.tmp")
        tmp.writeText(json.encodeToString(Draft.serializer(), rel))
        Files.move(tmp.toPath(), file(d.id).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun load(id: String): Draft? = runCatching {
        val f = file(id)
        if (!f.exists()) return null
        json.decodeFromString(Draft.serializer(), f.readText()).mapUris {
            if (it.startsWith("photos/")) fileUri(File(dir(id), it)) else it
        }
    }.getOrNull()

    /** Newest first. Unreadable drafts are skipped. */
    fun list(): List<Draft> =
        (root.listFiles { f -> f.isDirectory } ?: emptyArray()).mapNotNull { load(it.name) }.sortedByDescending { it.updatedAt }

    fun delete(id: String) { dir(id).deleteRecursively() }

    /** Copies the whole folder (photos included); relative uris keep the copy self-contained. */
    fun duplicate(id: String, newId: String, newName: String, now: Long): Draft? {
        val src = load(id) ?: return null
        dir(id).copyRecursively(dir(newId), overwrite = true)
        val copy = (load(newId) ?: return null).copy(id = newId, name = newName, createdAt = now, updatedAt = now)
        save(copy)
        return copy
    }

    fun rename(id: String, name: String, now: Long) {
        val d = load(id) ?: return
        save(d.copy(name = name, updatedAt = now))
    }

    /** Deletes photo files no card references any more. */
    fun prunePhotos(d: Draft) {
        val used = d.cards.flatMap { c -> c.slots.mapNotNull { it.uri } }.map { it.substringAfterLast('/') }.toSet()
        photosDir(d.id).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}
