package org.telegram.messenger;

/**
 * Simple offline virtual pet mechanics. This does not claim to be a generative AI
 * system and does not have access to Telegram messages or contacts.
 */
public final class TelebotsPetState {
    public static final int FEED = 0;
    public static final int PLAY = 1;
    public static final int REST = 2;
    public static final int ENCOURAGE = 3;

    public final int mood;
    public final int energy;
    public final int affection;
    public final int interactions;

    public TelebotsPetState(int mood, int energy, int affection, int interactions) {
        this.mood = cap(mood);
        this.energy = cap(energy);
        this.affection = cap(affection);
        this.interactions = Math.max(0, interactions);
    }

    public static TelebotsPetState initial() {
        return new TelebotsPetState(70, 80, 50, 0);
    }

    public static int cap(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public TelebotsPetState interact(int action) {
        switch (action) {
            case FEED:
                return next(mood + 5, energy + 9, affection + 4);
            case PLAY:
                if (energy < 12) {
                    return next(mood + 2, energy, affection + 1);
                }
                return next(mood + 13, energy - 12, affection + 9);
            case REST:
                return next(mood + 2, energy + 22, affection);
            case ENCOURAGE:
                return next(mood + 7, energy, affection + 7);
            default:
                throw new IllegalArgumentException("Unknown pet action");
        }
    }

    private TelebotsPetState next(int nextMood, int nextEnergy, int nextAffection) {
        return new TelebotsPetState(nextMood, nextEnergy, nextAffection, interactions + 1);
    }
}
