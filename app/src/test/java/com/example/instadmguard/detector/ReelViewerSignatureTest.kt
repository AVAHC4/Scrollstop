package com.example.instadmguard.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelViewerSignatureTest {

    @Test
    fun `matching signatures tolerate caption variation on the same reel`() {
        val reference = setOf("friend_creator", "shared reel caption", "funny skit part 1")
        val candidate = setOf("friend_creator", "shared reel caption extended")

        assertTrue(ReelViewerSignature.matches(reference, candidate))
    }

    @Test
    fun `different reel signatures do not match`() {
        val reference = setOf("friend_creator", "shared reel caption")
        val candidate = setOf("another_creator", "different reel topic")

        assertFalse(ReelViewerSignature.matches(reference, candidate))
    }

    @Test
    fun `same creator alone does not prove the same reel`() {
        val reference = setOf("friend_creator", "shared reel caption")
        val candidate = setOf("friend_creator", "different reel topic")

        assertFalse(ReelViewerSignature.matches(reference, candidate))
    }

    @Test
    fun `empty signatures do not prove the same reel`() {
        assertFalse(ReelViewerSignature.matches(emptySet(), setOf("friend_creator")))
        assertFalse(ReelViewerSignature.matches(setOf("friend_creator"), emptySet()))
    }
}
