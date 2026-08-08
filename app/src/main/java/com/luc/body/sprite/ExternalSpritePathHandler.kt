package com.luc.body.sprite

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

class ExternalSpritePathHandler(
    directory: File,
) : WebViewAssetLoader.PathHandler {
    private val root = directory

    override fun handle(path: String): WebResourceResponse? {
        val candidate = ExternalSpriteResolver.resolve(root, path) ?: return null
        return WebResourceResponse("image/svg+xml", "UTF-8", FileInputStream(candidate))
    }
}
