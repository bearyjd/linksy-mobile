package app.linksy.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonShapeTest {
    private fun nested(depth: Int) = "[".repeat(depth) + "]".repeat(depth)

    @Test fun aFlatMessageIsFine() {
        assertTrue(JsonShape.withinDepth("""{"type":"pointer","x":0.5,"y":0.5}"""))
    }

    @Test fun theLimitIsInclusive() {
        assertTrue(JsonShape.withinDepth(nested(JsonShape.MAX_DEPTH)))
        assertFalse(JsonShape.withinDepth(nested(JsonShape.MAX_DEPTH + 1)))
    }

    @Test fun objectsAndArraysBothCount() {
        val mixed = """{"a":""".repeat(5) + "[[[[" + "]]]]" + "}".repeat(5)
        assertFalse(JsonShape.withinDepth(mixed))
        assertTrue(JsonShape.withinDepth("""{"a":{"b":[{"c":[1]}]}}"""))
    }

    @Test fun bracketsInsideStringsAreNotCounted() {
        assertTrue(JsonShape.withinDepth("""{"type":"text","text":"${"[".repeat(100)}"}"""))
        assertTrue(JsonShape.withinDepth("""{"text":"\"${"[".repeat(100)}"}"""))
    }

    @Test fun anEscapedBackslashEndsTheStringAfterIt() {
        // "\\" is a complete string, so what follows is real nesting.
        assertFalse(JsonShape.withinDepth("""{"a":"\\","b":${"[".repeat(20)}""" + "}"))
    }

    @Test fun aStrayCloseIsRefused() {
        assertFalse(JsonShape.withinDepth("]]]]"))
        assertFalse(JsonShape.withinDepth("""{"a":1}}"""))
    }

    @Test fun hugeNestingIsRefusedWithoutRecursing() {
        assertFalse(JsonShape.withinDepth("[".repeat(500_000)))
    }
}
