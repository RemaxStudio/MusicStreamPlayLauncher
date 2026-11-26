package com.amberwave.catalog

import com.amberwave.catalog.model.CatalogPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSdkFactoryTest {
    @Test
    fun `empty sdk can be enabled after remote credentials arrive`() {
        val sdk = CatalogSdkFactory.create(CatalogSdkConfig())

        assertEquals(2, sdk.health().size)
        assertTrue(sdk.health().none { it.isAvailable })

        sdk.updateCredentials(
            CatalogSdkCredentials(
                jamendoClientIds = listOf("jamendo"),
                audiusCredentials = listOf(AudiusCredential("audius", "bearer")),
            ),
        )

        assertTrue(sdk.health().all { it.isAvailable })
        assertEquals(1, sdk.health().first { it.platform == CatalogPlatform.JAMENDO }.availableKeys)
        assertFalse(sdk.health().any { it.disabledKeys > 0 })
    }
}
