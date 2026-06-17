package com.harems.api.image;

/**
 * Result of analyzing the recent conversation context to build a contextual image prompt.
 *
 * highTrust: true when the user has exchanged enough messages with this character
 *            to meet the trust threshold (based on difficulty). Affects credit cost.
 * messageCount: total messages exchanged with this character so far.
 */
public record ImageContextAnalysis(
        AdultLevel adultLevel,
        String mood,
        String scene,
        String poseIntent,
        String logSummary,
        boolean highTrust,
        int messageCount
) {
    static ImageContextAnalysis defaults(AdultLevel level, String mood, String scene) {
        return new ImageContextAnalysis(level, mood, scene, "natural", "defaults-applied", false, 0);
    }
}
