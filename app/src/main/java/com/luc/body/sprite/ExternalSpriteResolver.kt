package com.luc.body.sprite

import java.io.File

object ExternalSpriteResolver {
    fun resolve(directory: File, path: String): File? {
        val fileName = path.removePrefix("/")
        if (!SpriteCatalogLoader.isSafeSpriteFileName(fileName)) return null
        val root = directory.canonicalFile
        val candidate = File(root, fileName).canonicalFile
        return candidate.takeIf { it.parentFile == root && it.isFile }
    }
}
