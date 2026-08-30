package com.example.nightscreenguard;

import java.util.Arrays;

/** Pure Java executable coverage for configuration JSON without Gradle/JUnit. */
public final class GuardConfigJsonSmokeTest {
    private static final String VALID = "{\"version\":1,\"enabled\":true,\"monitorWindow\":{\"start\":\"22:30\",\"end\":\"07:00\"},\"reminderPoints\":[\"23:30\",\"00:00\"],\"strongReminderStart\":\"01:00\",\"normalIntervalMinutes\":10,\"strongIntervalsMinutes\":[5,3,1],\"cooldownSeconds\":60}";

    public static void main(String[] args) {
        expectFailure(() -> GuardConfig.parseMinuteList("01:30,01:30"), "01:30");
        GuardConfig config = GuardConfigJson.parse(VALID);
        check(config.enabled, "enabled");
        check(config.reminderPoints.equals(Arrays.asList(23 * 60 + 30, 0)), "points preserved");
        check(GuardConfigJson.stringify(config).equals(VALID), "stable export");
        expectFailure(() -> GuardConfigJson.parse("{}"), "missing");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("\"version\":1", "\"version\":2")), "version");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("\"version\":1", "\"schema\":1")), "missing version");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("22:30", "2:30")), "time");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("[\"23:30\",\"00:00\"]", "[]")), "empty points");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("\"normalIntervalMinutes\":10", "\"normalIntervalMinutes\":0")), "positive");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("\"cooldownSeconds\":60", "\"cooldownSeconds\":30")), "cooldown");
        expectFailure(() -> GuardConfigJson.parse(VALID.replace("[\"23:30\",\"00:00\"]", "[\"23:30\",\"23:30\"]")), "duplicate");
        GuardConfigJson.parse(VALID.replace("}", ",\"ignored\":true}"));
        System.out.println("PASS GuardConfigJsonSmokeTest");
    }

    private static void expectFailure(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) {
            if (label.equals("01:30") && !expected.getMessage().contains(label)) throw new AssertionError(label);
            return;
        }
        throw new AssertionError("accepted " + label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
