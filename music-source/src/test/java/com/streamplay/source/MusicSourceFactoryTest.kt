package com.streamplay.source

import com.streamplay.source.model.RemotePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceFactoryTest {
    @Test
    fun `empty sdk can be enabled after remote credentials arrive`() {
        val sdk = MusicSourceFactory.create(MusicSourceConfig())

        assertEquals(2, sdk.health().size)
        assertTrue(sdk.health().none { it.isAvailable })

        sdk.updateCredentials(
            MusicSourceCredentials(
                jamendoClientIds = listOf("jamendo"),
                audiusCredentials = listOf(AudiusCredential("audius", "bearer")),
            ),
        )

        assertTrue(sdk.health().all { it.isAvailable })
        assertEquals(1, sdk.health().first { it.platform == RemotePlatform.JAMENDO }.availableKeys)
        assertFalse(sdk.health().any { it.disabledKeys > 0 })
    }
}
