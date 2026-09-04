package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheBookPendingReadRequestTest {

    @Test
    fun firstRelayoutRequestKeepsResetPageOffset() {
        val request = mergePendingReadRequest(
            previous = null,
            resetPageOffset = false,
        )

        assertEquals(
            PendingReadRequest(
                resetPageOffset = false,
            ),
            request,
        )
    }

    @Test
    fun resetPageOffsetIsRetainedAcrossPendingRequests() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = true,
            ),
            resetPageOffset = false,
        )

        assertTrue(request.resetPageOffset)
    }

    @Test
    fun anyRelayoutRequestSetsResetPageOffset() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = false,
            ),
            resetPageOffset = true,
        )

        assertTrue(request.resetPageOffset)
    }

    @Test
    fun laterRelayoutCannotOverrideResetRequest() {
        val request = mergePendingReadRequest(
            previous = PendingReadRequest(
                resetPageOffset = true,
            ),
            resetPageOffset = false,
        )

        assertTrue(request.resetPageOffset)
    }

    @Test
    fun noPreviousAndNoResetKeepsDefaults() {
        val request = mergePendingReadRequest(
            previous = null,
            resetPageOffset = false,
        )

        assertFalse(request.resetPageOffset)
    }
}
