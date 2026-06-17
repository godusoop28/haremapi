package com.harems.api.image;

/**
 * Result of analyzing the recent conversation context to build a contextual image prompt.
 */
public record ImageContextAnalysis(
        AdultLevel adultLevel,
        String mood,
        String scene,
        String poseIntent,
        String logSummary
) {
    static ImageContextAnalysis defaults(AdultLevel level, String mood, String scene) {
        return new ImageContextAnalysis(level, mood, scene, "natural", "defaults-applied");
    }
}
