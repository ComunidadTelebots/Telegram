package org.telegram.messenger;

/** Local authoring controls only. Incoming content and protocol security stay current. */
public final class DesignFeaturePolicy {
    public enum Feature {
        POLL_CREATION(2018), SCHEDULED_SEND(2019), SILENT_SEND(2019),
        STORY_POSTING(2023), MESSAGE_EFFECT_SELECTION(2024);
        public final int introductionYear;
        Feature(int year) { introductionYear = year; }
    }

    private DesignFeaturePolicy() {}

    public static boolean allows(Feature feature, int year, boolean allFeatures) {
        if (feature == null) throw new IllegalArgumentException("feature");
        return allFeatures || year >= feature.introductionYear;
    }
}
