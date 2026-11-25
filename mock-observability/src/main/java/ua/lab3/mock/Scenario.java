package ua.lab3.mock;

/** Per-node behaviour: metric baselines plus the log mix (count per request, share of ERROR, share of WARN). */
public enum Scenario {
    //                  cpu   mem   p95   err   logs errShare warnShare
    NORMAL(0.25, 0.45, 0.15, 0.01, 5, 0.0, 0.1),
    ANOMALY(0.92, 0.85, 1.8, 0.25, 25, 0.7, 0.2),
    // false-positive case: metrics are fine, only a burst of benign WARN logs
    NOISY_LOGS(0.25, 0.45, 0.15, 0.01, 30, 0.0, 0.9),
    // false-positive case: cpu is high, everything else is fine
    CPU_SPIKE_BENIGN(0.90, 0.45, 0.15, 0.01, 5, 0.0, 0.1);

    final double cpu, mem, p95, err, errShare, warnShare;
    final int logs;

    Scenario(double cpu, double mem, double p95, double err, int logs, double errShare, double warnShare) {
        this.cpu = cpu;
        this.mem = mem;
        this.p95 = p95;
        this.err = err;
        this.logs = logs;
        this.errShare = errShare;
        this.warnShare = warnShare;
    }
}
