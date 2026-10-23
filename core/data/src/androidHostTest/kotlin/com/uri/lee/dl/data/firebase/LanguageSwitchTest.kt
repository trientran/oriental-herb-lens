package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.data.firebase.LanguageSwitchSync.Companion.isOn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSwitchTest {

    @Test
    fun `unset means every language`() {
        assertTrue(isOn(""))
    }

    @Test
    fun `only false switches the other languages off`() {
        assertFalse(isOn("false"))
        assertFalse(isOn(" FALSE "))
        assertTrue(isOn("true"))
    }
}
