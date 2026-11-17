package com.example.fitplan;

/** Translate only known, explicitly labelled demo data. Never translate user-entered names. */
final class DemoLabels {
    static final String NOTE = "测试数据：用于演示周报，不是真实训练记录。";

    static String titleKey(String dataset, String name) {
        if (!"eightweeks-v1".equals(dataset)) return null;
        switch (name) {
            case "[测试数据] 胸肩训练":
                return "demo_chest";
            case "[测试数据] 背部与手臂":
                return "demo_pull";
            case "[测试数据] 腿部与核心":
                return "demo_legs";
            case "[测试数据] 上肢综合":
                return "demo_upper";
            case "[测试数据] 轻量恢复":
                return "demo_recovery";
            default:
                return null;
        }
    }
}
