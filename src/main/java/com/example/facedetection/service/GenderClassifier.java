package com.example.facedetection.service;

/**
 * Converts the gender network's two class scores into a safe display result.
 * Low-confidence output is intentionally represented as Unknown instead of
 * presenting a weak binary prediction as a fact.
 */
public final class GenderClassifier {

    private static final String[] LABELS = {"Male", "Female"};

    private GenderClassifier() {
    }

    public static String[] classify(double maleScore, double femaleScore, double minimumConfidence) {
        if (!Double.isFinite(maleScore) || !Double.isFinite(femaleScore)
                || maleScore < 0.0 || femaleScore < 0.0
                || !Double.isFinite(minimumConfidence) || minimumConfidence <= 0.0) {
            return new String[]{"Unknown", ""};
        }

        double total = maleScore + femaleScore;
        if (total <= 0.0) {
            return new String[]{"Unknown", ""};
        }

        double maleProbability = maleScore / total;
        double femaleProbability = femaleScore / total;
        int classId = maleProbability >= femaleProbability ? 0 : 1;
        double confidence = Math.max(maleProbability, femaleProbability);
        int confidencePct = (int) Math.round(confidence * 100.0);

        if (confidence < minimumConfidence) {
            return new String[]{"Unknown", confidencePct + "%"};
        }

        return new String[]{LABELS[classId], confidencePct + "%"};
    }
}
