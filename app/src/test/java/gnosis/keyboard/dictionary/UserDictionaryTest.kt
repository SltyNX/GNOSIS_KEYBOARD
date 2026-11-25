package gnosis.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserDictionaryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDefaultShortcutsLoaded() {
        val repo = UserDictionaryRepository(tempFolder.root)
        assertEquals("on my way", repo.getExpansion("omw"))
        assertEquals("be right back", repo.getExpansion("brb"))
        assertEquals("thank you", repo.getExpansion("ty"))
        assertEquals("no problem", repo.getExpansion("np"))
        assertEquals("I don't know", repo.getExpansion("idk"))
    }

    @Test
    fun testAddAndRemoveCustomWords() {
        val repo = UserDictionaryRepository(tempFolder.root)

        assertTrue(repo.addWord("gnosis"))
        assertTrue(repo.isCustomWord("gnosis"))
        assertTrue(repo.isCustomWord("GNOSIS"))
        assertTrue(repo.isCustomWord("  gnosis  "))

        assertFalse(repo.addWord("a"))
        assertFalse(repo.isCustomWord("a"))

        assertFalse(repo.addWord("gnosis"))

        assertTrue(repo.removeWord("gnosis"))
        assertFalse(repo.isCustomWord("gnosis"))
        assertFalse(repo.removeWord("nonexistent"))
    }

    @Test
    fun testAddAndRemoveShortcuts() {
        val repo = UserDictionaryRepository(tempFolder.root)

        assertTrue(repo.addShortcut("btw", "by the way"))
        assertEquals("by the way", repo.getExpansion("btw"))
        assertEquals("BY THE WAY", repo.getExpansion("BTW"))
        assertEquals("By the way", repo.getExpansion("Btw"))
        assertEquals("by the way", repo.getExpansion("  btw  "))

        assertTrue(repo.removeShortcut("btw"))
        assertNull(repo.getExpansion("btw"))
        assertFalse(repo.removeShortcut("btw"))
    }

    @Test
    fun testDiskPersistenceAcrossInstances() {
        val baseDir = tempFolder.newFolder("dict_dir")

        val repo1 = UserDictionaryRepository(baseDir)
        repo1.addWord("cryptography")
        repo1.addShortcut("gn", "good night")

        val repo2 = UserDictionaryRepository(baseDir)
        assertTrue(repo2.isCustomWord("cryptography"))
        assertEquals("good night", repo2.getExpansion("gn"))
        assertEquals("on my way", repo2.getExpansion("omw"))
    }

    @Test
    fun testExportAndImportJson() {
        val repo1 = UserDictionaryRepository(tempFolder.newFolder("dir1"))
        repo1.addWord("quantum")
        repo1.addShortcut("afaik", "as far as I know")

        val exported = repo1.exportJson()
        assertNotNull(exported)
        assertTrue(exported.contains("quantum"))
        assertTrue(exported.contains("afaik"))

        val repo2 = UserDictionaryRepository(tempFolder.newFolder("dir2"))
        repo2.clearAll()
        assertEquals(0, repo2.getAllWords().size)
        assertEquals(0, repo2.getAllShortcuts().size)

        val imported = repo2.importJson(exported)
        assertTrue(imported)
        assertTrue(repo2.isCustomWord("quantum"))
        assertEquals("as far as I know", repo2.getExpansion("afaik"))
    }
}
