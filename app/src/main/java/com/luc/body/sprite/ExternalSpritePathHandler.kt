package com.luc.body.sprite

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.ByteArrayInputStream

class ExternalSpritePathHandler(
    directory: File,
) : WebViewAssetLoader.PathHandler {
    private val root = directory

    override fun handle(path: String): WebResourceResponse? {
        val candidate = ExternalSpriteResolver.resolve(root, path) ?: return null
        val source = runCatching(candidate::readText).getOrNull() ?: return null
        val bytes = SvgMarkupNormalizer.normalize(source)
            .toByteArray(Charsets.UTF_8)
        return WebResourceResponse("image/svg+xml", "UTF-8", ByteArrayInputStream(bytes))
    }
}
