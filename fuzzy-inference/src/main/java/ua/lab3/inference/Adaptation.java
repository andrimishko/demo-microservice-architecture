package ua.lab3.inference;

import java.util.List;
import java.util.Map;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

/**
 * Feedback update rule: for the scored vector compute the normalized firing w̄ of every rule, set the target
 * t = 0 for FALSE_POSITIVE and t = 1 for MISSED / CONFIRMED, and move each consequent towards it:
 *     c  <-  c + eta * w̄ * (t - c)
 * So the rules that were responsible for the score (large w̄) change most, the others barely move.
 * shortcut: only consequents are tuned; no rule creation/merging, no membership-function tuning; add them when
 * one linear update per feedback stops being enough.
 */
final class Adaptation {
    private Adaptation() {}

    static Model adapt(Model m, Map<String, Double> vector, Verdict verdict, double eta) {
        double target = verdict == Verdict.FALSE_POSITIVE ? 0 : 1;
        double[] wbar = m.normalizedFirings(vector);
        List<Model.Rule> updated = new java.util.ArrayList<>();
        for (int i = 0; i < wbar.length; i++) {
            Model.Rule r = m.rules().get(i);
            double c = r.consequent() + eta * wbar[i] * (target - r.consequent());
            updated.add(new Model.Rule(r.id(), r.antecedent(), Math.round(c * 10000) / 10000.0));
        }
        return m.withRules(updated);
    }
}
