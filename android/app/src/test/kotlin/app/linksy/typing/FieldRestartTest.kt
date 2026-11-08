package app.linksy.typing

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldRestartTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private fun key(
        id: Int = 7, pkg: String? = "chat", type: Int = text, ime: Int = EditorInfo.IME_ACTION_SEND,
    ) = FieldKey(id, pkg, type, ime)

    @Test fun aRestartOfTheSameFieldKeepsWhatIsInFlight() {
        val r = FieldRestart()
        assertFalse(r.keeps(key(), FieldState.Typable, restarting = false))
        assertTrue(r.keeps(key(), FieldState.Typable, restarting = true))
        assertTrue(r.keeps(key(), FieldState.Typable, restarting = true))
    }

    @Test fun aFreshStartIsAlwaysANewField() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        assertFalse(r.keeps(key(), FieldState.Typable, restarting = false))
    }

    @Test fun aDifferentFieldDropsIt() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        assertFalse(r.keeps(key(id = 8), FieldState.Typable, restarting = true))
    }

    @Test fun aDifferentAppDropsIt() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        assertFalse(r.keeps(key(pkg = "other"), FieldState.Typable, restarting = true))
    }

    @Test fun aTurnIntoAPasswordDropsIt() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        val pw = key(type = text or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(r.keeps(pw, FieldState.Password, restarting = true))
    }

    @Test fun aChangeOfInputClassOrImeOptionsDropsIt() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        assertFalse(r.keeps(key(type = InputType.TYPE_CLASS_NUMBER), FieldState.Typable, restarting = true))
        assertFalse(r.keeps(key(type = InputType.TYPE_CLASS_NUMBER, ime = EditorInfo.IME_ACTION_GO), FieldState.Typable, restarting = true))
    }

    @Test fun aMoreRestrictivePolicyDropsItEvenForTheSameKey() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        assertFalse(r.keeps(key(), FieldState.NoInput, restarting = true))
    }

    @Test fun nothingIsKeptAfterTheInputFinished() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        r.reset()
        assertFalse(r.keeps(key(), FieldState.Typable, restarting = true))
    }

    @Test fun textFlagsAloneDoNotMakeANewField() {
        val r = FieldRestart()
        r.keeps(key(), FieldState.Typable, restarting = false)
        val flagged = key(type = text or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertTrue(r.keeps(flagged, FieldState.Typable, restarting = true))
    }
}
