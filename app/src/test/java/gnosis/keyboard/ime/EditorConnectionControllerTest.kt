package gnosis.keyboard.ime

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class EditorConnectionControllerTest {

    private lateinit var controller: EditorConnectionController

    @Before
    fun setUp() {
        controller = EditorConnectionController()
    }

    private fun createMockInputConnection(initialText: String): Pair<InputConnection, StringBuilder> {
        val sb = StringBuilder(initialText)
        val ic = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTextBeforeCursor" -> {
                    val n = args[0] as Int
                    val str = sb.toString()
                    str.takeLast(n)
                }
                "deleteSurroundingText" -> {
                    val before = args[0] as Int
                    if (before > 0 && sb.length >= before) {
                        sb.delete(sb.length - before, sb.length)
                    }
                    true
                }
                "commitText" -> {
                    val text = args[0] as CharSequence
                    sb.append(text)
                    true
                }
                else -> null
            }
        } as InputConnection
        return Pair(ic, sb)
    }

    @Test
    fun testSpaceAfterSingleLetterDoesNotTriggerPeriod() {
        val (ic, sb) = createMockInputConnection("r")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertFalse("handleDoubleSpacePeriod must not trigger when preceding character is a letter", handled)
        assertEquals("Buffer must remain unchanged", "r", sb.toString())
    }

    @Test
    fun testSpaceAfterFastTypingDoesNotDeleteKey() {
        val (ic, sb) = createMockInputConnection("fast r")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertFalse("handleDoubleSpacePeriod must not trigger after 'fast r'", handled)
        assertEquals("fast r", sb.toString())
    }

    @Test
    fun testDoubleSpaceAfterWordConvertsToPeriod() {
        val (ic, sb) = createMockInputConnection("hello ")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertTrue("handleDoubleSpacePeriod should trigger after a word followed by space", handled)
        assertEquals("Trailing space must be replaced by '. '", "hello. ", sb.toString())
    }

    @Test
    fun testDoubleSpaceAfterPunctuationDoesNotConvertToPeriod() {
        val (ic, sb) = createMockInputConnection("hello, ")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertFalse("Must not convert to period after comma", handled)
        assertEquals("hello, ", sb.toString())

        val (ic2, sb2) = createMockInputConnection("really? ")
        val handled2 = controller.handleDoubleSpacePeriod(ic2)
        assertFalse("Must not convert to period after question mark", handled2)
        assertEquals("really? ", sb2.toString())
    }

    @Test
    fun testDoubleSpaceAfterNewlineDoesNotConvertToPeriod() {
        val (ic, sb) = createMockInputConnection("\n ")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertFalse("Must not convert to period after newline", handled)
        assertEquals("\n ", sb.toString())
    }

    @Test
    fun testDoubleSpaceAfterExistingPeriodDoesNotDuplicate() {
        val (ic, sb) = createMockInputConnection("done. ")
        val handled = controller.handleDoubleSpacePeriod(ic)
        assertFalse("Must not convert to period after existing period", handled)
        assertEquals("done. ", sb.toString())
    }
}
