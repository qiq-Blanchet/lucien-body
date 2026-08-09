package com.luc.body.sprite

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

class NormalizingAssetsPathHandler(
    private val context: Context,
) : WebViewAssetLoader.PathHandler {
    private val fallback = WebViewAssetLoader.AssetsPathHandler(context)

    override fun handle(path: String): WebResourceResponse? {
        val prefix = "${SpriteCatalogLoader.ASSET_DIRECTORY}/"
        if (!path.startsWith(prefix)) return fallback.handle(path)
        val fileName = path.removePrefix(prefix)
        if (!SpriteCatalogLoader.isSafeSpriteFileName(fileName)) return null
        val source = runCatching {
            context.assets.open("$prefix$fileName").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        val bytes = SvgMarkupNormalizer.normalize(source).toByteArray(Charsets.UTF_8)
        return WebResourceResponse("image/svg+xml", "UTF-8", ByteArrayInputStream(bytes))
    }
}
