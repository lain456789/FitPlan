package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.put;

import org.json.JSONObject;

/** Additive JSON metadata: existing SQLite records require no schema migration. */
final class PlanEdits {
    private PlanEdits() {}

    static void confirm(JSONObject row, double weight, int sets, int reps, long now) {
        if (!Double.isFinite(weight)
                || weight < 0
                || weight > 1000
                || sets < 1
                || sets > 20
                || reps < 1
                || reps > 100) throw new IllegalArgumentException("Invalid prescription");
        if (!row.has("originalPrescription") && (row.has("reasonCode") || row.has("reason"))) {
            put(row, "originalPrescription", copy(row));
            // v0.2 may already have been edited; its true generation values are unknowable.
            put(
                    row,
                    "originalPrescriptionSource",
                    row.optInt("referencePolicyVersion") >= 2 ? "generated" : "before_edit");
        }
        put(row, "weight", weight);
        put(row, "sets", sets);
        put(row, "reps", reps);
        put(row, "userConfirmed", true);
        put(row, "needsWeightConfirmation", false);
        put(row, "prescriptionUpdatedAt", now);
        // Generation reasons belong to the original snapshot, not the user's new values.
        row.remove("reason");
        row.remove("reasonCode");
        row.remove("reduced");
        row.remove("timeLimited");
    }

    static boolean needsConfirmation(JSONObject plan, JSONObject row) {
        if (row.optBoolean("userConfirmed")) return false;
        if (row.optBoolean("needsWeightConfirmation")) return true;
        // Previously generated v0.2 plans may contain the old highest-weight heuristic.
        return plan.has("checkin") && !row.has("referencePolicyVersion");
    }
}
