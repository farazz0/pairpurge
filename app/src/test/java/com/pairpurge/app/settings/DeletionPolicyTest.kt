package com.pairpurge.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionPolicyTest {
    private val day = DeletionPolicy.DAY_MILLIS

    @Test fun `disabled policy never deletes`() {
        assertFalse(DeletionPolicy().shouldDelete(0, 4000 * day, false, false))
    }

    @Test fun `expires at the configured boundary`() {
        val policy = DeletionPolicy(30)
        assertFalse(policy.isExpired(day, 31 * day - 1))
        assertTrue(policy.isExpired(day, 31 * day))
    }

    @Test fun `a new connection restarts the timer`() {
        val policy = DeletionPolicy(7)
        assertTrue(policy.isExpired(0, 8 * day))
        assertFalse(policy.isExpired(6 * day, 8 * day))
    }

    @Test fun `protected and connected devices are never deleted`() {
        val policy = DeletionPolicy(1)
        assertFalse(policy.shouldDelete(0, 2 * day, true, false))
        assertFalse(policy.shouldDelete(0, 2 * day, false, true))
        assertFalse(policy.shouldDelete(0, 2 * day, false, null))
        assertTrue(policy.shouldDelete(0, 2 * day, false, false))
    }

    @Test fun `clock moving backwards does not expire a device`() {
        assertFalse(DeletionPolicy(1).isExpired(10 * day, day))
    }

    @Test fun `maximum duration does not overflow`() {
        assertFalse(DeletionPolicy(3650).isExpired(0, 3649 * day))
        assertTrue(DeletionPolicy(3650).isExpired(0, 3650 * day))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero days is rejected`() { DeletionPolicy(0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative days is rejected`() { DeletionPolicy(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun `excessive days is rejected`() { DeletionPolicy(3651) }
}
