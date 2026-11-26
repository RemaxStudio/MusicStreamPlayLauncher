package com.streamplay.music.core.remote

import com.streamplay.source.AudiusCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigParsingTest {

    @Test
    fun `patch reads both platforms and strips bearer prefix`() {
        val patch = SourceKeyPatch.decode(
            """{"jamendoClientIds":[" a ","b","a"],"audiusCredentials":[{"apiKey":"k1","bearerToken":"Bearer t1"},{"apiKey":"k2"}]}""",
        )
        assertEquals(listOf("a", "b"), patch.jamendoClientIds)
        assertEquals(listOf(AudiusCredential("k1", "t1"), AudiusCredential("k2", null)), patch.audiusCredentials)
    }

    @Test
    fun `missing or malformed fields keep the current credentials`() {
        assertTrue(SourceKeyPatch.decode(null).isEmpty)
        assertTrue(SourceKeyPatch.decode("not json").isEmpty)
        val patch = SourceKeyPatch.decode("""{"jamendoClientIds":"oops","audiusCredentials":[{"bearerToken":"x"}]}""")
        assertNull(patch.jamendoClientIds)
        assertNull(patch.audiusCredentials)
    }

    @Test
    fun `explicit empty arrays disable a platform`() {
        val patch = SourceKeyPatch.decode("""{"jamendoClientIds":[],"audiusCredentials":[]}""")
        assertEquals(emptyList<String>(), patch.jamendoClientIds)
        assertEquals(emptyList<AudiusCredential>(), patch.audiusCredentials)
        assertFalse(patch.isEmpty)
    }

    @Test
    fun `home experience follows the audience branch`() {
        val raw = """{"natural":"a","paid":"b"}"""
        assertEquals(HomeExperience.DEVICE, resolveHomeExperience(raw, Audience.ORGANIC))
        assertEquals(HomeExperience.STREAMING, resolveHomeExperience(raw, Audience.PAID))
        assertEquals(HomeExperience.STREAMING, resolveHomeExperience("""{"PAID":"online"}""", Audience.PAID))
    }

    @Test
    fun `unexpected home values fall back to the device experience`() {
        assertEquals(HomeExperience.DEVICE, resolveHomeExperience(null, Audience.PAID))
        assertEquals(HomeExperience.DEVICE, resolveHomeExperience("""{"paid":"c"}""", Audience.PAID))
        assertEquals(HomeExperience.DEVICE, resolveHomeExperience("""{"paid":1}""", Audience.PAID))
        assertEquals(HomeExperience.DEVICE, resolveHomeExperience("[]", Audience.PAID))
    }

    @Test
    fun `store reports a change only after the startup decision`() {
        val store = HomeExperienceStore()
        assertFalse(store.reconcile("""{"paid":"b"}""", Audience.PAID))
        assertEquals(HomeExperience.STREAMING, store.mode.value)
        store.settle("""{"paid":"a"}""", Audience.PAID)
        assertEquals(HomeExperience.STREAMING, store.mode.value)
        assertTrue(store.reconcile("""{"paid":"a"}""", Audience.PAID))
        assertEquals(HomeExperience.DEVICE, store.mode.value)
    }
}
