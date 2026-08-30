package com.example.nightscreenguard;

import java.util.Arrays;

/** Standalone smoke test for environments without the Android Gradle toolchain. */
public final class GuardPolicySmokeTest {
    private GuardPolicySmokeTest() {
    }

    public static void main(String[] args) {
        GuardConfig config = GuardConfig.defaults();
        check(config.reminderPoints.size() == 3, "default points");
        check(config.reminderPoints.equals(Arrays.asList(0, 1_380, 1_410)), "sorted points");
        check(GuardPolicy.isInWindow(23 * 60 + 30, 22 * 60 + 30, 7 * 60), "late window");
        check(GuardPolicy.isInWindow(30, 22 * 60 + 30, 7 * 60), "after-midnight window");
        check(!GuardPolicy.isStrongPhase(59, config), "before strong phase");
        check(GuardPolicy.isStrongPhase(60, config), "at strong phase");
        check(GuardPolicy.nextRepeatMinutes(true, 0, config) == 5, "first strong repeat");
        check(GuardPolicy.nextRepeatMinutes(true, 1, config) == 3, "second strong repeat");
        check(GuardPolicy.nextRepeatMinutes(true, 5, config) == 1, "last strong repeat");
        check(!GuardPolicy.canClose(159_999L, 100_000L, config), "cooldown locked");
        check(GuardPolicy.canClose(160_000L, 100_000L, config), "cooldown unlocked");
        System.out.println("PASS GuardPolicySmokeTest");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
    }
}
