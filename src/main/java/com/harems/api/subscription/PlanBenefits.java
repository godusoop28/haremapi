package com.harems.api.subscription;

/**
 * Centralized definition of what each plan grants: image credits and duration.
 * Kept here so subscription simulation, admin plan changes and registration
 * all stay consistent.
 */
public final class PlanBenefits {

    private PlanBenefits() {
    }

    public static int imageCreditsFor(PlanType plan) {
        return switch (plan) {
            case FREE -> 0;
            case TRIAL_3_DAYS -> 10;
            case PREMIUM -> 30;
            case VIP -> 100;
        };
    }

    /**
     * @return number of days the plan lasts, or {@code null} if the plan never expires (FREE).
     */
    public static Integer durationDaysFor(PlanType plan) {
        return switch (plan) {
            case FREE -> null;
            case TRIAL_3_DAYS -> 3;
            case PREMIUM, VIP -> 30;
        };
    }
}
