package app.linksy.typing

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldPolicyTest {
    private fun of(inputType: Int, connection: Boolean = true) = FieldPolicy.of(inputType, connection)

    @Test fun textFieldsTakeTyping() {
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_TEXT))
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_NUMBER))
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_PHONE))
    }

    @Test fun passwordFieldsPause() {
        assertEquals(FieldState.Password, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(FieldState.Password, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(FieldState.Password, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(FieldState.Password, of(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test fun theVariationIsReadInItsOwnClass() {
        // The number password variation and the text URI variation share a value.
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(FieldState.Typable, of(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL))
    }

    @Test fun editorsWithoutATextClassOrAConnectionCannotTakeIt() {
        assertEquals(FieldState.NoInput, of(InputType.TYPE_NULL))
        assertEquals(FieldState.NoInput, of(InputType.TYPE_CLASS_TEXT, connection = false))
        assertEquals(FieldState.NoInput, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, connection = false))
    }
}
