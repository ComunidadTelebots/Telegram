import org.telegram.messenger.DesignFeaturePolicy;
import org.telegram.messenger.DesignFeaturePolicy.Feature;

public final class DesignFeaturePolicyTest {
    public static void main(String[] args) {
        int checked = 0;
        for (Feature feature : Feature.values()) {
            for (int year = 2013; year <= 2030; year++) {
                if (!DesignFeaturePolicy.allows(feature, year, true))
                    throw new AssertionError("Modern functions lost in an old appearance");
                if (DesignFeaturePolicy.allows(feature, year, false) != (year >= feature.introductionYear))
                    throw new AssertionError("Wrong historical boundary: " + feature + " " + year);
                checked += 2;
            }
            if (DesignFeaturePolicy.allows(feature, feature.introductionYear - 1, false))
                throw new AssertionError("Feature exposed before its introduction");
            if (!DesignFeaturePolicy.allows(feature, feature.introductionYear, false))
                throw new AssertionError("Feature hidden in its introduction year");
        }
        System.out.println("Passed " + checked + " combinations and year boundaries");
    }
}
