package com.amberwave.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogCredentialsParserTest {
    @Test
    fun `parses unified sdk config and keeps api bearer pairing`() {
        val patch = CatalogCredentialsParser.parse(
            """
                {
                  "jamendoClientIds": ["jamendo-a", "jamendo-b"],
                  "audiusCredentials": [
                    {"apiKey":"api-a","bearerToken":"Bearer token-a"},
                    {"apiKey":"api-b","bearerToken":"token-b"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(listOf("jamendo-a", "jamendo-b"), patch.jamendoClientIds)
        assertEquals(listOf("api-a", "api-b"), patch.audiusCredentials?.map { it.apiKey })
        assertEquals(listOf("token-a", "token-b"), patch.audiusCredentials?.map { it.bearerToken })
    }

    @Test
    fun `missing platform field keeps its current credentials`() {
        val patch = CatalogCredentialsParser.parse(
            """{"jamendoClientIds":["jamendo-a"]}""",
        )

        assertEquals(listOf("jamendo-a"), patch.jamendoClientIds)
        assertNull(patch.audiusCredentials)
    }

    @Test
    fun `malformed config does not erase current credentials`() {
        val malformedRoot = CatalogCredentialsParser.parse("[invalid")
        val malformedFields = CatalogCredentialsParser.parse(
            """
                {
                  "jamendoClientIds": "not-an-array",
                  "audiusCredentials": [{"bearerToken":"missing-api"}]
                }
            """.trimIndent(),
        )

        assertNull(malformedRoot.jamendoClientIds)
        assertNull(malformedRoot.audiusCredentials)
        assertNull(malformedFields.jamendoClientIds)
        assertNull(malformedFields.audiusCredentials)
    }

    @Test
    fun `explicit empty arrays disable both platforms`() {
        val patch = CatalogCredentialsParser.parse(
            """{"jamendoClientIds":[],"audiusCredentials":[]}""",
        )

        assertEquals(emptyList<String>(), patch.jamendoClientIds)
        assertEquals(emptyList<String>(), patch.audiusCredentials?.map { it.apiKey })
    }
}
