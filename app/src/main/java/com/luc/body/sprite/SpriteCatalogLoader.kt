package com.luc.body.sprite

import android.content.Context
import androidx.webkit.WebViewAssetLoader
import java.io.File

object SpriteCatalogLoader {
    const val ASSET_DIRECTORY = "clawd_sprites"
    const val EXTERNAL_DIRECTORY = "clawd_sprites"
    const val EXTERNAL_PATH = "/sprites/"

    fun load(context: Context): SpriteCatalog = SpriteCatalog(
        assets = merge(externalSprites(context), bundledSprites(context)),
    )

    fun externalDirectory(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, EXTERNAL_DIRECTORY) }

    fun bundledSprites(context: Context): List<SpriteAsset> =
        context.assets.list(ASSET_DIRECTORY).orEmpty()
            .filter(::isSafeSpriteFileName)
            .sorted()
            .map { fileName ->
                SpriteAsset(fileName, "$ASSET_BASE_URL/$ASSET_DIRECTORY/$fileName")
            }

    fun externalSprites(context: Context): List<SpriteAsset> =
        externalDirectory(context)?.listFiles().orEmpty()
            .asSequence()
            .filter { file -> file.isFile && isSafeSpriteFileName(file.name) }
            .sortedBy(File::getName)
            .map { file -> SpriteAsset(file.name, "$EXTERNAL_BASE_URL$EXTERNAL_PATH${file.name}") }
            .toList()

    fun merge(external: List<SpriteAsset>, bundled: List<SpriteAsset>): List<SpriteAsset> =
        (external + bundled).distinctBy(SpriteAsset::fileName)

    fun isSafeSpriteFileName(fileName: String): Boolean =
        SPRITE_FILE_NAME.matches(fileName)

    private const val ASSET_BASE_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets"
    private const val EXTERNAL_BASE_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}"
    private val SPRITE_FILE_NAME = Regex("[A-Za-z0-9_]+\\.svg")
}
