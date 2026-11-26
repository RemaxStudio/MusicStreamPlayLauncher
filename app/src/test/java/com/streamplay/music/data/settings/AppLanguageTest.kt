package com.streamplay.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `exact and regional tags map to supported languages`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.match("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.match("en-US"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.match("zh-CN"))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.match("zh-Hans-CN"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.match("id"))
    }

    @Test
    fun `traditional chinese and unknown tags are not mapped`() {
        assertNull(AppLanguage.match("zh-TW"))
        assertNull(AppLanguage.match("pt-BR"))
        assertNull(AppLanguage.match(""))
    }

    @Test
    fun `system language reports a stable value`() {
        assertEquals("system", AppLanguage.SYSTEM.reportValue)
        assertEquals("zh-cn", AppLanguage.CHINESE_SIMPLIFIED.reportValue)
    }
}
