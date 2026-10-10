package org.jellyfin.androidtv.ui.home

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.io.File

/**
 * Saved copies of Home rows whose server request is slow (Continue Watching, Recently Added TV),
 * shown immediately on the next launch while the fresh request runs.
 */
class HomeRowCache(private val dir: File) {
    private val serializer = ListSerializer(BaseItemDto.serializer())

    fun read(key: String): List<BaseItemDto>? = runCatching {
        val file = file(key)
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else null
    }.onFailure { Timber.w(it, "Could not read saved Home row $key") }.getOrNull()

    fun write(key: String, items: List<BaseItemDto>) {
        runCatching {
            dir.mkdirs()
            val target = file(key)
            val temporary = File(dir, target.name + ".tmp")
            temporary.writeText(json.encodeToString(serializer, items))
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        }.onFailure { Timber.w(it, "Could not save Home row $key") }
    }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
