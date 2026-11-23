package com.imagine.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayParserTest {

    private val parser = RelayParser()

    @Test
    fun putOnRootWithValidObject() {
        val ev = parser.parse(
            "put",
            """{"path":"/","data":{"power":"fire","seq":1764000000000,"sentAt":1764000000000}}""",
        )
        assertEquals(RelayEvent.State("fire", 1764000000000L), ev)
    }

    @Test
    fun putOnRootWithNone() {
        val ev = parser.parse("put", """{"path":"/","data":{"power":"none","seq":1764000000001}}""")
        assertEquals(RelayEvent.State("none", 1764000000001L), ev)
    }

    @Test
    fun putWithNullData() {
        assertEquals(RelayEvent.Empty, parser.parse("put", """{"path":"/","data":null}"""))
    }

    @Test
    fun putOnChildPathIsIgnored() {
        val ev = parser.parse("put", """{"path":"/power","data":"fire"}""")
        assertTrue(ev is RelayEvent.Ignored)
    }

    @Test
    fun patchIsIgnored() {
        val ev = parser.parse("patch", """{"path":"/","data":{"power":"fire","seq":1764000000000}}""")
        assertTrue(ev is RelayEvent.Ignored)
    }

    @Test
    fun keepAliveIsIgnored() {
        assertNull(parser.parse("keep-alive", "null"))
    }

    @Test
    fun malformedJsonIsIgnoredWithoutCrash() {
        assertTrue(parser.parse("put", """{"path":"/","data":{"power":""") is RelayEvent.Ignored)
        assertTrue(parser.parse("put", "not json") is RelayEvent.Ignored)
        assertTrue(parser.parse("put", "[1,2]") is RelayEvent.Ignored)
        assertTrue(parser.parse("put", null) is RelayEvent.Ignored)
    }

    @Test
    fun objectMissingSeqOrPowerIsIgnored() {
        assertTrue(parser.parse("put", """{"path":"/","data":{"power":"fire"}}""") is RelayEvent.Ignored)
        assertTrue(parser.parse("put", """{"path":"/","data":{"seq":5}}""") is RelayEvent.Ignored)
        assertTrue(parser.parse("put", """{"path":"/","data":{"power":"fire","seq":"5"}}""") is RelayEvent.Ignored)
    }

    @Test
    fun cancelAndAuthRevokedAskForReconnect() {
        assertEquals(RelayEvent.Cancel("cancel"), parser.parse("cancel", "\"Permission denied\""))
        assertEquals(RelayEvent.Cancel("auth_revoked"), parser.parse("auth_revoked", "\"token expired\""))
    }
}
