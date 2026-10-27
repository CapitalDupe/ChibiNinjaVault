package com.ninjavault.app

import com.ninjavault.app.data.VaultFileType
import com.ninjavault.app.vault.VaultNames
import org.junit.Assert.*
import org.junit.Test

class VaultNamesTest {

    @Test
    fun `adx display name replaces the extension`() {
        assertEquals("photo.adx", VaultNames.adxDisplayName("photo.jpg"))
        assertEquals("a.b.adx", VaultNames.adxDisplayName("a.b.png"))
        assertEquals("notes.adx", VaultNames.adxDisplayName("notes"))
    }

    @Test
    fun `adx display name handles dot files and blanks`() {
        assertEquals(".hidden.adx", VaultNames.adxDisplayName(".hidden"))
        assertEquals("file.adx", VaultNames.adxDisplayName("   "))
        assertEquals("clip.adx", VaultNames.adxDisplayName("clip.adx"))
    }

    @Test
    fun `strip adx suffix only affects a trailing adx`() {
        assertEquals("photo", VaultNames.stripAdxSuffix("photo.adx"))
        assertEquals("photo", VaultNames.stripAdxSuffix("photo.ADX"))
        assertEquals("", VaultNames.stripAdxSuffix(".adx"))
        assertEquals("adx photo", VaultNames.stripAdxSuffix("adx photo"))
    }

    @Test
    fun `generic mime falls back to the extension`() {
        assertEquals("video/x-matroska", VaultNames.resolveMime("application/octet-stream", "movie.MKV"))
        assertEquals("image/heic", VaultNames.resolveMime(null, "shot.heic"))
        assertEquals("application/octet-stream", VaultNames.resolveMime("application/octet-stream", "mystery.xyz"))
        assertEquals("image/png", VaultNames.resolveMime("image/png; charset=binary", "whatever.adx"))
    }

    @Test
    fun `type inference uses extension when mime is generic`() {
        assertEquals(VaultFileType.VIDEO, VaultNames.typeFor("application/octet-stream", "a.mkv"))
        assertEquals(VaultFileType.AUDIO, VaultNames.typeFor("application/octet-stream", "a.flac"))
        assertEquals(VaultFileType.IMAGE, VaultNames.typeFor("image/jpeg", "a.adx"))
        assertEquals(VaultFileType.OTHER, VaultNames.typeFor("application/octet-stream", "a.adx"))
    }

    @Test
    fun `html detection works from mime or name`() {
        assertTrue(VaultNames.isHtml("text/html", "page.adx"))
        assertTrue(VaultNames.isHtml("application/octet-stream", "page.HTM"))
        assertFalse(VaultNames.isHtml("text/plain", "page.txt"))
    }

    @Test
    fun `playback extension never returns adx`() {
        assertEquals("mp4", VaultNames.playbackExtension("video/mp4", "clip.mp4"))
        assertEquals("mp4", VaultNames.playbackExtension("video/mp4", "clip.adx"))
        assertEquals("jpg", VaultNames.playbackExtension("image/jpeg", "no_extension"))
        assertEquals("", VaultNames.playbackExtension("application/octet-stream", "mystery"))
    }

    @Test
    fun `text-like files are detected and typed as documents`() {
        for (name in listOf("data.json", "script.py", "app.js", "notes.txt", "README.md", "conf.yaml")) {
            assertTrue(name, VaultNames.isText("application/octet-stream", name))
            assertEquals(name, VaultFileType.DOCUMENT, VaultNames.typeFor("application/octet-stream", name))
        }
        assertTrue(VaultNames.isText("application/json", "x.adx"))
    }

    @Test
    fun `binaries and html are not treated as text`() {
        assertFalse(VaultNames.isText("application/x-msdownload", "setup.exe"))
        assertFalse(VaultNames.isText("application/octet-stream", "setup.exe"))
        assertEquals(VaultFileType.OTHER, VaultNames.typeFor("application/octet-stream", "setup.exe"))
        assertFalse(VaultNames.isText("text/html", "page.html"))
    }

    @Test
    fun `key file name uses the adxkey extension`() {
        assertEquals("photo.adxkey", VaultNames.keyFileName("photo.jpg"))
        assertEquals("notes.adxkey", VaultNames.keyFileName("notes"))
    }
}
