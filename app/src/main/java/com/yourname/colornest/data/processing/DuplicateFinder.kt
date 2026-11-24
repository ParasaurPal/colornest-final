package com.yourname.colornest.data.processing

import com.yourname.colornest.data.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateGroup(
    val hash: String,
    val pages: List<Page>
)

object DuplicateFinder {
    private fun keepScore(page: Page): Int {
        return (if (page.categoryId != null) 4 else 0) +
            (if (page.isFavorite) 2 else 0) +
            (if (page.isCompleted) 1 else 0)
    }

    private fun sortGroup(pages: List<Page>): List<Page> {
        return pages.sortedWith(
            compareByDescending<Page> { keepScore(it) }
                .thenBy { it.id }
        )
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { input ->
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun findDuplicateGroups(
        pages: List<Page>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val hashable = pages.filter { page ->
            !page.isPreviewOnly && page.imageUrl.isNotBlank() && File(page.imageUrl).let { it.exists() && it.isFile }
        }
        val byHash = LinkedHashMap<String, MutableList<Page>>()

        for (i in hashable.indices) {
            val page = hashable[i]
            val file = File(page.imageUrl)
            val hash = runCatching { sha256Hex(file) }.getOrNull()
            if (hash != null) {
                byHash.getOrPut(hash) { mutableListOf() }.add(page)
            }
            if (i % 5 == 4 || i == hashable.lastIndex) {
                onProgress(i + 1, hashable.size)
            }
        }

        if (hashable.isEmpty()) {
            onProgress(0, 0)
        }

        byHash.entries
            .filter { it.value.size >= 2 }
            .map { (hash, members) -> DuplicateGroup(hash = hash, pages = sortGroup(members)) }
            .sortedByDescending { it.pages.size }
    }
}
