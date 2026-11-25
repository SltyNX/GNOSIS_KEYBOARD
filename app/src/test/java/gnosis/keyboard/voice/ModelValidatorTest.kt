package gnosis.keyboard.voice

import gnosis.keyboard.voice.models.ModelValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testNonExistentDirectoryReturnsInvalid() {
        val nonExistent = File(tempFolder.root, "does_not_exist")
        val result = ModelValidator.validate(nonExistent)
        assertTrue(result is ModelValidator.ValidationResult.Invalid)
    }

    @Test
    fun testEmptyDirectoryReturnsInvalid() {
        val emptyDir = tempFolder.newFolder("empty_model")
        val result = ModelValidator.validate(emptyDir)
        assertTrue(result is ModelValidator.ValidationResult.Invalid)
    }

    @Test
    fun testCompleteVoskDirectoryReturnsValid() {
        val modelDir = tempFolder.newFolder("valid_vosk_model")
        val amDir = File(modelDir, "am").apply { mkdirs() }
        File(amDir, "final.mdl").writeText("fake acoustic parameters")

        val confDir = File(modelDir, "conf").apply { mkdirs() }
        File(confDir, "mfcc.conf").writeText("--sample-frequency=16000")
        File(confDir, "model.conf").writeText("--min-active=200")

        val graphDir = File(modelDir, "graph").apply { mkdirs() }
        File(graphDir, "HCLr.fst").writeText("fake fst")
        File(graphDir, "Gr.fst").writeText("fake grammar")

        val result = ModelValidator.validate(modelDir)
        assertEquals(ModelValidator.ValidationResult.Valid, result)
    }

    @Test
    fun testNestedArchiveDirectoryResolvesRoot() {
        val wrapperDir = tempFolder.newFolder("extracted_archive")
        val innerDir = File(wrapperDir, "vosk-model-small-en-us-0.15").apply { mkdirs() }

        val amDir = File(innerDir, "am").apply { mkdirs() }
        File(amDir, "final.mdl").writeText("data")
        val confDir = File(innerDir, "conf").apply { mkdirs() }
        File(confDir, "mfcc.conf").writeText("data")
        val graphDir = File(innerDir, "graph").apply { mkdirs() }
        File(graphDir, "words.txt").writeText("data")

        val resolved = ModelValidator.resolveModelRoot(wrapperDir)
        assertEquals(innerDir.absolutePath, resolved.absolutePath)

        val result = ModelValidator.validate(wrapperDir)
        assertEquals(ModelValidator.ValidationResult.Valid, result)
    }
}
