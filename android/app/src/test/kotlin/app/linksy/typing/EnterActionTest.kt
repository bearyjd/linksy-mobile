package app.linksy.typing

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterActionTest {
    @Test fun anExplicitActionIsTheEnterAction() {
        assertEquals(EditorInfo.IME_ACTION_SEND, EnterAction.of(EditorInfo.IME_ACTION_SEND))
        assertEquals(EditorInfo.IME_ACTION_GO, EnterAction.of(EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI))
    }

    @Test fun noActionOrNoEnterActionMeansAPlainEnter() {
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_NONE))
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
    }

    @Test fun nextAndPreviousMoveFocusSoTheyAreNotAnEnterAction() {
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_NEXT))
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_PREVIOUS))
        assertNull(EnterAction.of(EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI))
    }

    @Test fun multiLineComesFromTheInputType() {
        assertTrue(EnterAction.isMultiLine(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(EnterAction.isMultiLine(InputType.TYPE_CLASS_TEXT))
        assertFalse(EnterAction.isMultiLine(InputType.TYPE_CLASS_NUMBER))
    }
}
