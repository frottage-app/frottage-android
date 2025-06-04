package com.frottage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UtcHoursSchedulePrevTimeTest {
    private val scheduleHours = listOf(1, 7, 13, 19) // Standard Frottage schedule
    private val schedule = UtcHoursSchedule(scheduleHours)
    private val utcZone = ZoneId.of("UTC")

    @Test
    fun `test time just before first schedule hour of the day`() {
        // Purpose: Verify that if the current time is just before the first scheduled hour of the day (e.g., 00:59),
        // the previous update time is correctly identified as the last scheduled hour of the *previous* day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 0, 59, 59, 999_000_000, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2023, 12, 31, 19, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time exactly at first schedule hour of the day`() {
        // Purpose: Verify that if the current time is exactly at the first scheduled hour of the day (e.g., 01:00),
        // the previous update time is correctly identified as this same hour on the current day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time just after first schedule hour of the day`() {
        // Purpose: Verify that if the current time is just moments after the first scheduled hour (e.g., 01:00:00.001),
        // the previous update time is correctly identified as that first scheduled hour on the current day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 1_000_000, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time in the middle of a slot`() {
        // Purpose: Verify that if the current time is well within a scheduled slot (e.g., 10:30 for 07:00-13:00 slot),
        // the previous update time is correctly identified as the start of that current slot.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 10, 30, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 7, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time exactly at a later schedule hour`() {
        // Purpose: Verify that if the current time is exactly at a later scheduled hour (e.g., 13:00),
        // the previous update time is correctly identified as this same hour on the current day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time just after a later schedule hour`() {
        // Purpose: Verify that if the current time is just moments after a later scheduled hour (e.g., 13:00:00.001),
        // the previous update time is correctly identified as that same scheduled hour on the current day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 1_000_000, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time just before a later schedule hour`() {
        // Purpose: Verify that if the current time is just moments before a later scheduled hour (e.g., 12:59:59 for 13:00 slot),
        // the previous update time is correctly identified as the start of the current slot (e.g., 07:00).
        val currentTime = ZonedDateTime.of(2024, 1, 1, 12, 59, 59, 999_000_000, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 7, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test time after last schedule hour of the day`() {
        // Purpose: Verify that if the current time is after the last scheduled hour of the day (e.g., 22:00 after 19:00 slot),
        // the previous update time is correctly identified as that last scheduled hour of the current day.
        val currentTime = ZonedDateTime.of(2024, 1, 1, 22, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 19, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, schedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test with single hour schedule - time before`() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is before it,
        // the previous update time is correctly identified as that single hour on the *previous* day.
        val singleHourSchedule = UtcHoursSchedule(listOf(12))
        val currentTime = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2023, 12, 31, 12, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, singleHourSchedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test with single hour schedule - time at`() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is exactly at that hour,
        // the previous update time is correctly identified as that same hour on the current day.
        val singleHourSchedule = UtcHoursSchedule(listOf(12))
        val currentTime = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, singleHourSchedule.prevUpdateTime(currentTime))
    }

    @Test
    fun `test with single hour schedule - time after`() {
        // Purpose: For a schedule with only one update hour, verify that if the current time is after that hour,
        // the previous update time is correctly identified as that same hour on the current day.
        val singleHourSchedule = UtcHoursSchedule(listOf(12))
        val currentTime = ZonedDateTime.of(2024, 1, 1, 15, 0, 0, 0, utcZone)
        val expectedPrevTime = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, utcZone)
        assertEquals(expectedPrevTime, singleHourSchedule.prevUpdateTime(currentTime))
    }
}
