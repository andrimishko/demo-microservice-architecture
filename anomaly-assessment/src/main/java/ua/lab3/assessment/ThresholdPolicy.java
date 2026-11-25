package ua.lab3.assessment;

/** score below `warning` is NORMAL, below `anomaly` is WARNING, otherwise ANOMALY. */
public final class ThresholdPolicy {
    private ThresholdPolicy() {}

    public static State classify(double score, double warning, double anomaly) {
        if (score < warning) return State.NORMAL;
        return score < anomaly ? State.WARNING : State.ANOMALY;
    }
}
