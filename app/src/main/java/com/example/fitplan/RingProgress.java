package com.example.fitplan;

/** Only known, positive targets produce a progress arc. */
final class RingProgress {
    static float fraction(double actual, double target) {
        if (!Double.isFinite(actual) || !Double.isFinite(target) || actual < 0 || target <= 0)
            return 0;
        return (float) Math.min(1, actual / target);
    }
}
