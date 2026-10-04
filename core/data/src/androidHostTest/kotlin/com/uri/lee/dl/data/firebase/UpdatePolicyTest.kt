package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.data.firebase.DefaultAppStatusRepository.Companion.updatePolicy
import com.uri.lee.dl.domain.model.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun `unset versions never prompt`() {
        assertEquals(UpdatePolicy.NONE, updatePolicy(current = 9, minimum = 0, recommended = 0))
    }

    @Test
    fun `below the minimum requires an update`() {
        assertEquals(UpdatePolicy.REQUIRED, updatePolicy(current = 9, minimum = 10, recommended = 12))
    }

    @Test
    fun `between minimum and recommended suggests one`() {
        assertEquals(UpdatePolicy.RECOMMENDED, updatePolicy(current = 10, minimum = 10, recommended = 12))
    }

    @Test
    fun `at or above recommended is fine`() {
        assertEquals(UpdatePolicy.NONE, updatePolicy(current = 12, minimum = 10, recommended = 12))
    }
}
