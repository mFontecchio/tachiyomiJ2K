package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

object MangaCoverRatios {
    private var coverRatioMap = ConcurrentHashMap<Long, Float>()
    val preferences by injectLazy<PreferencesHelper>()

    fun load() {
        val ratios = preferences.coverRatios().get()
        coverRatioMap = ConcurrentHashMap(
            ratios.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val ratio = splits.lastOrNull()?.toFloatOrNull()
                if (id != null && ratio != null) {
                    id to ratio
                } else {
                    null
                }
            }.toMap()
        )
    }

    fun addCover(manga: Manga, ratio: Float) {
        val id = manga.id ?: return
        coverRatioMap[id] = ratio
    }

    fun getRatio(manga: Manga): Float? {
        return coverRatioMap[manga.id]
    }

    fun savePrefs() {
        val mapCopy = coverRatioMap.toMap()
        preferences.coverRatios().set(mapCopy.map { "${it.key}|${it.value}" }.toSet())
    }
}
