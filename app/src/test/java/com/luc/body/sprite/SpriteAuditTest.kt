package com.luc.body.sprite

import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteAuditTest {
    @Test
    fun `bundled sprites exactly match the checked in v2 inventory hash manifest`() {
        val manifest = JSONObject(File("../docs/clawd-v2-svg-hashes.json").readText())
        val expected = manifest.getJSONObject("files").let { files ->
            files.keys().asSequence().associateWith(files::getJSONObject)
        }
        val spriteDirectory = File("src/main/assets/clawd_sprites")
        val actual = spriteDirectory.listFiles { file -> file.extension == "svg" }
            ?.associateBy { it.name }
            ?: error("Sprite directory is missing")

        assertEquals(37, expected.size)
        assertEquals("svg-template-26", manifest.getString("excluded_unreferenced_template_id"))
        assertEquals(26, manifest.getJSONObject("night_card").getInt("card_no"))
        assertEquals("svg-template-27", manifest.getJSONObject("night_card").getString("template_id"))
        assertEquals("night.svg", manifest.getJSONObject("night_card").getString("target_file"))
        assertEquals(expected.keys, actual.keys)
        assertFalse(expected.containsKey("template-26.svg"))
        var gitBackedCount = 0
        var customCount = 0
        expected.forEach { (name, entry) ->
            val file = requireNotNull(actual[name])
            assertEquals(entry.getInt("bytes"), file.length().toInt())
            assertEquals(entry.getString("sha256"), file.sha256())
            assertEquals("svg", file.svgRootName())
            when (entry.getString("origin_type")) {
                "git_blob" -> {
                    gitBackedCount++
                    assertTrue(entry.getString("source_url").startsWith("https://github.com/"))
                    assertEquals(entry.getString("git_blob_sha"), file.gitBlobSha1())
                }
                "zip_custom_template" -> {
                    customCount++
                    assertTrue(entry.isNull("source_url"))
                    assertTrue(entry.isNull("git_blob_sha"))
                    assertTrue(entry.getString("template_id") in setOf("svg-template-6", "svg-template-7"))
                }
                else -> error("Unknown origin type for $name")
            }
        }
        assertEquals(35, gitBackedCount)
        assertEquals(2, customCount)
    }

    private fun File.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }

    private fun File.gitBlobSha1(): String {
        val bytes = readBytes()
        return MessageDigest.getInstance("SHA-1").run {
            update("blob ${bytes.size}\u0000".toByteArray())
            digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    private fun File.svgRootName(): String = inputStream().use { input ->
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(input).documentElement.localName
    }
}
