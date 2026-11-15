package com.nbansal.grid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionsTest {
    @Test fun parsesPlainAndPrefixedTags() {
        assertEquals(100, Versions.versionCodeFromTag("v0.1.0"))
        assertEquals(100, Versions.versionCodeFromTag("0.1.0"))
        assertEquals(10203, Versions.versionCodeFromTag("v1.2.3"))
        assertEquals(1, Versions.versionCodeFromTag("0.0.1"))
    }

    @Test fun ordersNewerHigher() {
        assertEquals(true, Versions.versionCodeFromTag("v0.4.0")!! > Versions.versionCodeFromTag("v0.3.12")!!)
    }

    @Test fun rejectsGarbage() {
        assertNull(Versions.versionCodeFromTag("latest"))
        assertNull(Versions.versionCodeFromTag("v1.2"))
        assertNull(Versions.versionCodeFromTag(""))
    }

    @Test fun stripsVPrefix() {
        assertEquals("0.4.0", Versions.versionFromTag("v0.4.0"))
    }
}
