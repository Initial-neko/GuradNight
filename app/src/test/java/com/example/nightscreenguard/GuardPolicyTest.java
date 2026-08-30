package com.example.nightscreenguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class GuardPolicyTest {

    @Test
    public void defaultConfigContainsMultiplePointsAndConfigurableStrongStart() {
        GuardConfig config = GuardConfig.defaults();

        assertEquals(3, config.reminderPoints.size());
        assertEquals(60, config.strongStartMinute);
        assertEquals(10, config.normalIntervalMinutes);
        assertEquals(60, config.cooldownSeconds);
    }

    @Test
    public void parsesClockAndMultipleMinuteList() {
        assertEquals(90, GuardConfig.parseClock("01:30"));
        assertEquals(Arrays.asList(0, 60, 90), GuardConfig.parseMinuteList("01:30,00:00,01:00"));
    }

    @Test
    public void rejectsDuplicateMinuteWithFormattedTime() {
        try {
            GuardConfig.parseMinuteList("01:30,01:30");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("01:30"));
            return;
        }
        throw new AssertionError("duplicate time accepted");
    }

    @Test
    public void importsAndExportsConfig() {
        String json = "{\"version\":1,\"enabled\":true,\"monitorWindow\":{\"start\":\"22:30\",\"end\":\"07:00\"},\"reminderPoints\":[\"23:30\",\"00:00\"],\"strongReminderStart\":\"01:00\",\"normalIntervalMinutes\":10,\"strongIntervalsMinutes\":[5,3,1],\"cooldownSeconds\":60}";
        GuardConfig config = GuardConfigJson.parse(json);
        assertEquals(json, GuardConfigJson.stringify(config));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateJsonReminderPoint() {
        GuardConfigJson.parse("{\"version\":1,\"enabled\":true,\"monitorWindow\":{\"start\":\"22:30\",\"end\":\"07:00\"},\"reminderPoints\":[\"23:30\",\"23:30\"],\"strongReminderStart\":\"01:00\",\"normalIntervalMinutes\":10,\"strongIntervalsMinutes\":[5,3,1],\"cooldownSeconds\":60}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidJsonConfiguration() {
        GuardConfigJson.parse("{\"schema\":2}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidClock() {
        GuardConfig.parseClock("25:00");
    }

    @Test
    public void handlesMonitoringWindowAcrossMidnight() {
        assertTrue(GuardPolicy.isInWindow(23 * 60 + 30, 22 * 60 + 30, 7 * 60));
        assertTrue(GuardPolicy.isInWindow(30, 22 * 60 + 30, 7 * 60));
        assertFalse(GuardPolicy.isInWindow(12 * 60, 22 * 60 + 30, 7 * 60));
    }

    @Test
    public void strongPhaseStartsAtConfiguredMinute() {
        GuardConfig config = GuardConfig.defaults();

        assertFalse(GuardPolicy.isStrongPhase(59, config));
        assertTrue(GuardPolicy.isStrongPhase(60, config));
        assertTrue(GuardPolicy.isStrongPhase(90, config));
    }

    @Test
    public void strongRepeatUsesConfiguredSequenceThenLastInterval() {
        GuardConfig config = GuardConfig.defaults();

        assertEquals(5, GuardPolicy.nextRepeatMinutes(true, 0, config));
        assertEquals(3, GuardPolicy.nextRepeatMinutes(true, 1, config));
        assertEquals(1, GuardPolicy.nextRepeatMinutes(true, 2, config));
        assertEquals(1, GuardPolicy.nextRepeatMinutes(true, 5, config));
        assertEquals(10, GuardPolicy.nextRepeatMinutes(false, 9, config));
    }

    @Test
    public void closeIsDisabledBeforeSixtySecondsAndEnabledAtDeadline() {
        GuardConfig config = GuardConfig.defaults();

        assertFalse(GuardPolicy.canClose(159_999L, 100_000L, config));
        assertTrue(GuardPolicy.canClose(160_000L, 100_000L, config));
    }
}
