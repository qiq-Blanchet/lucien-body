package com.luc.body.sprite

import java.nio.file.Files
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNoException
import org.junit.Test

class ExternalSpriteResolverTest {
    @Test
    fun `accepts a safe direct child and rejects traversal`() {
        val parent = Files.createTempDirectory("sprite-resolver").toFile()
        val root = parent.resolve("sprites").apply { mkdir() }
        val safe = root.resolve("idle.svg").apply { writeText("<svg></svg>") }

        assertEquals(safe.canonicalFile, ExternalSpriteResolver.resolve(root, "idle.svg"))
        assertNull(ExternalSpriteResolver.resolve(root, "../outside.svg"))
        assertNull(ExternalSpriteResolver.resolve(root, "%2e%2e%2foutside.svg"))
    }

    @Test
    fun `rejects a symlink escaping the sprite directory when symlinks are available`() {
        val parent = Files.createTempDirectory("sprite-resolver-link").toFile()
        val root = parent.resolve("sprites").apply { mkdir() }
        val outside = parent.resolve("outside.svg").apply { writeText("<svg></svg>") }

        val link = root.resolve("idle.svg")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (error: Exception) {
            assumeNoException("Symbolic links unavailable in this JVM", error)
        }
        assertNull(ExternalSpriteResolver.resolve(root, "idle.svg"))
    }
}
