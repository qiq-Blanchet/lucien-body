package com.luc.body.web

import android.graphics.Color
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.luc.body.sprite.ExternalSpritePathHandler
import com.luc.body.sprite.SpriteCatalogLoader
import com.luc.body.state.UiSink
import com.luc.body.state.VisibleState

class WebRenderer(
    private val petWebView: WebView,
    private val bubbleWebView: WebView,
) : UiSink {
    private val externalSpriteDirectory = SpriteCatalogLoader.externalDirectory(petWebView.context)
    private val spriteCatalog = SpriteCatalogLoader.load(petWebView.context)
    private val assetLoader = WebViewAssetLoader.Builder().apply {
        addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(petWebView.context))
        externalSpriteDirectory?.let { directory ->
            addPathHandler(SpriteCatalogLoader.EXTERNAL_PATH, ExternalSpritePathHandler(directory))
        }
    }.build()
    private val renderGate = WebRenderGate(::renderReadyState)

    init {
        configure(petWebView, CLAWD_URL, renderGate::onPetPageFinished)
        configure(bubbleWebView, BUBBLE_URL, renderGate::onBubblePageFinished)
        petWebView.loadUrl(CLAWD_URL)
        bubbleWebView.loadUrl(BUBBLE_URL)
    }

    override fun render(state: VisibleState) {
        renderGate.render(state)
    }

    fun showHeartParticles() {
        petWebView.evaluateJavascript("window.LucPet.heart()", null)
    }

    private fun renderReadyState(state: VisibleState) {
        val sprite = spriteCatalog.choose(state.expression.name)
        petWebView.evaluateJavascript(
            JavascriptCommandBuilder.setSprite(sprite.url, state.expression.name.lowercase()),
            null,
        )
        val bubbleCommand = state.bubbleText?.let {
            JavascriptCommandBuilder.showBubble(
                text = it,
                style = state.bubbleStyle.name.lowercase(),
                revision = state.revision,
            )
        } ?: JavascriptCommandBuilder.hideBubble()
        bubbleWebView.evaluateJavascript(bubbleCommand, null)
    }

    private fun configure(
        webView: WebView,
        expectedUrl: String,
        onExpectedPageFinished: () -> Unit,
    ) {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
        }
        webView.webViewClient = AssetOnlyWebViewClient(
            assetLoader = assetLoader,
            expectedUrl = expectedUrl,
            onExpectedPageFinished = onExpectedPageFinished,
        )
        webView.setDownloadListener(DownloadListener { _, _, _, _, _ -> Unit })
    }

    private class AssetOnlyWebViewClient(
        private val assetLoader: WebViewAssetLoader,
        private val expectedUrl: String,
        private val onExpectedPageFinished: () -> Unit,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isBundledAsset(request.url)

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            !isBundledAsset(Uri.parse(url))

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (url == expectedUrl) onExpectedPageFinished()
        }

        private fun isBundledAsset(uri: Uri): Boolean =
            uri.scheme == "https" &&
                uri.host == WebViewAssetLoader.DEFAULT_DOMAIN &&
                (uri.path?.startsWith("/assets/") == true || uri.path?.startsWith(SpriteCatalogLoader.EXTERNAL_PATH) == true)
    }

    private companion object {
        const val ASSET_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets"
        const val CLAWD_URL = "$ASSET_URL/clawd.html"
        const val BUBBLE_URL = "$ASSET_URL/bubble.html"
    }
}
