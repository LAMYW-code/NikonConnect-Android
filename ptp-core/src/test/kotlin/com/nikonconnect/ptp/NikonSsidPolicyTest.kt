package com.nikonconnect.ptp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NikonSsidPolicyTest {
    @Test
    fun acceptsNikonPrefixAfterAndroidNormalization() {
        assertTrue(NikonSsidPolicy.isNikon("NIKON_Z_123456"))
        assertTrue(NikonSsidPolicy.isNikon("\"nikon-camera\""))
        assertTrue(NikonSsidPolicy.isNikon("  NIKON Z  "))
    }

    @Test
    fun rejectsUnknownOrEmbeddedNikonText() {
        assertFalse(NikonSsidPolicy.isNikon(null))
        assertFalse(NikonSsidPolicy.isNikon("<unknown ssid>"))
        assertFalse(NikonSsidPolicy.isNikon("MY_NIKON_CAMERA"))
        assertFalse(NikonSsidPolicy.isNikon("CANON_123"))
    }
}
