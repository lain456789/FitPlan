package com.example.fitplan;

/** Search targets use stable built-in exercise IDs, independent of translated names. */
enum MuscleCatalog {
    CHEST("chest", new int[] {0, 1, 2, 16, 17, 28, 29}, new int[] {}),
    FRONT_DELT("front_delt", new int[] {3, 22}, new int[] {0, 1, 16, 17, 29}),
    SIDE_DELT("side_delt", new int[] {4, 23}, new int[] {3, 22}),
    REAR_DELT("rear_delt", new int[] {5, 24}, new int[] {7, 8, 18}),
    LATS("lats", new int[] {6, 7, 8, 18, 25}, new int[] {}),
    MIDBACK("midback", new int[] {7, 18}, new int[] {5, 8, 24}),
    BICEPS("biceps", new int[] {12, 20}, new int[] {6, 7, 8, 18, 25}),
    TRICEPS("triceps", new int[] {13, 21}, new int[] {0, 1, 3, 16, 17, 22, 29}),
    CORE("core", new int[] {14, 15}, new int[] {}),
    QUADRICEPS("quadriceps", new int[] {9, 10, 26, 27}, new int[] {}),
    GLUTES("glutes", new int[] {9, 26, 27}, new int[] {10}),
    HAMSTRING("hamstring", new int[] {11, 19}, new int[] {}),
    TRAPS("traps", new int[] {}, new int[] {5, 7, 18, 24}),
    CALVES("calves", new int[] {}, new int[] {}),
    ADDUCTORS("adductors", new int[] {}, new int[] {}),
    FOREARM("forearm", new int[] {}, new int[] {}),
    TIBIALIS("tibialis", new int[] {}, new int[] {}),
    SERRATUS("serratus", new int[] {}, new int[] {17}),
    HIP("hip", new int[] {}, new int[] {}),
    LOWER_BACK("lower_back", new int[] {}, new int[] {});

    final String key;
    final int[] primary;
    final int[] assisting;

    MuscleCatalog(String key, int[] primary, int[] assisting) {
        this.key = key;
        this.primary = primary;
        this.assisting = assisting;
    }

    static boolean hasPrimary(String key) {
        for (MuscleCatalog group : values()) {
            if (group.key.equals(key)) return group.primary.length > 0;
        }
        return false;
    }
}
