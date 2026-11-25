package ua.lab3.common;

public final class Subjects {
    public static final String STREAM = "DETECTION";
    public static final String NODE_CHANGED = "node.changed";
    public static final String FEATURES_BUILT = "features.built";
    public static final String SCORE_COMPUTED = "score.computed";
    public static final String STATE_CLASSIFIED = "state.classified";
    public static final String FEEDBACK_SUBMITTED = "feedback.submitted";
    public static final String MODEL_ACTIVATED = "model.activated";

    public static final String[] ALL = {NODE_CHANGED, FEATURES_BUILT, SCORE_COMPUTED,
            STATE_CLASSIFIED, FEEDBACK_SUBMITTED, MODEL_ACTIVATED};

    private Subjects() {}
}
