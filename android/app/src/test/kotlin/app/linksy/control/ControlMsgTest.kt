package app.linksy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlMsgTest {
    private fun parse(json: String) = ControlMsg.parse(json)

    @Test fun pointer() {
        assertEquals(ControlMsg.Pointer(0.25, 0.5), parse("""{"type":"pointer","x":0.25,"y":0.5}"""))
    }

    @Test fun pointerIsKeptInsideTheScreen() {
        assertEquals(ControlMsg.Pointer(1.0, 0.0), parse("""{"type":"pointer","x":1.4,"y":-0.2}"""))
    }

    @Test fun pointerWithoutNumbersIsRefused() {
        assertNull(parse("""{"type":"pointer","x":"a","y":0.5}"""))
        assertNull(parse("""{"type":"pointer","x":0.5}"""))
        assertNull(parse("""{"type":"pointer","x":1e999,"y":0.5}"""))
    }

    @Test fun button() {
        assertEquals(ControlMsg.Button("left", true), parse("""{"type":"button","button":"left","pressed":true}"""))
        for (name in listOf("left", "middle", "right", "back", "forward")) {
            assertEquals(ControlMsg.Button(name, false), parse("""{"type":"button","button":"$name","pressed":false}"""))
        }
    }

    @Test fun anUnknownButtonOrMissingStateIsRefused() {
        assertNull(parse("""{"type":"button","button":"turbo","pressed":true}"""))
        assertNull(parse("""{"type":"button","button":"left"}"""))
    }

    @Test fun scroll() {
        assertEquals(ControlMsg.Scroll(0.0, 2.0, true), parse("""{"type":"scroll","dx":0,"dy":2,"discrete":true}"""))
        assertEquals(ControlMsg.Scroll(1.5, -3.0, false), parse("""{"type":"scroll","dx":1.5,"dy":-3}"""))
    }

    @Test fun key() {
        assertEquals(ControlMsg.Key(65307, true), parse("""{"type":"key","keysym":65307,"pressed":true}"""))
        assertEquals(ControlMsg.Key(0x010020AC, false), parse("""{"type":"key","keysym":16785580,"pressed":false}"""))
    }

    @Test fun aKeysymOutsideU32IsRefused() {
        assertNull(parse("""{"type":"key","keysym":-1,"pressed":true}"""))
        assertNull(parse("""{"type":"key","keysym":4294967296,"pressed":true}"""))
    }

    @Test fun text() {
        assertEquals(ControlMsg.Text("héllo"), parse("""{"type":"text","text":"héllo"}"""))
    }

    @Test fun textOverTheLimitIsRefused() {
        val long = "a".repeat(ControlMsg.MAX_TEXT + 1)
        assertNull(parse("""{"type":"text","text":"$long"}"""))
        assertEquals(
            ControlMsg.Text("a".repeat(ControlMsg.MAX_TEXT)),
            parse("""{"type":"text","text":"${"a".repeat(ControlMsg.MAX_TEXT)}"}"""),
        )
    }

    @Test fun otherTypesAndGarbageAreIgnored() {
        assertNull(parse("""{"type":"camera_control","fps":60}"""))
        assertNull(parse("""{"type":"screen_rotation","degrees":90}"""))
        assertNull(parse("""{"x":1}"""))
        assertNull(parse("not json"))
        assertNull(parse("[]"))
        assertNull(parse(""))
    }

    @Test fun whatIsSentOnIsOnlyWhatWasUnderstood() {
        val json = ControlMsg.Button("left", true).toJson()
        assertEquals(ControlMsg.Button("left", true), parse(json))
        val withExtras = parse("""{"type":"pointer","x":0.1,"y":0.2,"extra":"x","cmd":"rm"}""")!!
        assertFalse(withExtras.toJson().contains("extra"))
        assertFalse(withExtras.toJson().contains("cmd"))
    }

    @Test fun everyKindSurvivesTheRoundTrip() {
        val all = listOf(
            ControlMsg.Pointer(0.1, 0.9),
            ControlMsg.Button("right", false),
            ControlMsg.Scroll(0.0, -1.0, true),
            ControlMsg.Key(65293, true),
            ControlMsg.Text("a \"quoted\" line\n"),
        )
        for (m in all) assertEquals(m, parse(m.toJson()))
    }

    @Test fun onlyReleasesAreReleases() {
        assertTrue(ControlMsg.Button("left", false).releases)
        assertTrue(ControlMsg.Key(65, false).releases)
        assertFalse(ControlMsg.Button("left", true).releases)
        assertFalse(ControlMsg.Key(65, true).releases)
        assertFalse(ControlMsg.Pointer(0.0, 0.0).releases)
    }

    @Test fun textNeverShowsInToString() {
        assertFalse(ControlMsg.Text("hunter2").toString().contains("hunter2"))
    }
}
