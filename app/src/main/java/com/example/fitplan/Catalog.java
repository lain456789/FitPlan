package com.example.fitplan;
import java.util.Set;

final class Catalog {
    static final String[] PARTS={"chest","shoulders","back","legs","arms","core"};
    static final String[] LEGACY={"胸","肩","背","腿","手臂","核心"};
    static final String[] EQUIPMENT={"barbell","dumbbell","bench","cable","pulldown","row_machine","leg_press","leg_curl","band","pullup_bar"};
    // Curated candidates, not interchangeable loads or a claim of equal training effect.
    static final String[][] EXERCISES={
        {"杠铃卧推","chest","horizontal_press","barbell,bench"},
        {"哑铃上斜卧推","chest","incline_press","dumbbell,bench"},
        {"绳索夹胸","chest","chest_fly","cable"},
        {"哑铃推举","shoulders","vertical_press","dumbbell"},
        {"哑铃侧平举","shoulders","lateral_raise","dumbbell"},
        {"反向飞鸟","shoulders","rear_fly",""},
        {"高位下拉","back","vertical_pull","pulldown"},
        {"坐姿划船","back","horizontal_pull","row_machine"},
        {"单臂哑铃划船","back","horizontal_pull","dumbbell,bench"},
        {"深蹲","legs","knee_dominant",""},
        {"腿举","legs","knee_dominant","leg_press"},
        {"腿弯举","legs","knee_flexion","leg_curl"},
        {"哑铃弯举","arms","elbow_flexion","dumbbell"},
        {"绳索下压","arms","elbow_extension","cable"},
        {"卷腹","core","crunch",""},{"死虫式","core","dead_bug",""},
        {"哑铃平板卧推","chest","horizontal_press","dumbbell,bench"},
        {"俯卧撑","chest","horizontal_press",""},
        {"弹力带划船","back","horizontal_pull","band"},
        {"弹力带腿弯举","legs","knee_flexion","band"},
        {"绳索弯举","arms","elbow_flexion","cable"},
        {"哑铃臂屈伸","arms","elbow_extension","dumbbell"},
        {"杠铃肩上推举","shoulders","vertical_press","barbell"},
        {"绳索侧平举","shoulders","lateral_raise","cable"},
        {"绳索反向飞鸟","shoulders","rear_fly","cable"},
        {"引体向上","back","vertical_pull","pullup_bar"},
        {"哑铃高脚杯深蹲","legs","knee_dominant","dumbbell"},
        {"徒手深蹲","legs","knee_dominant",""},
        {"哑铃飞鸟","chest","chest_fly","dumbbell,bench"},
        {"杠铃上斜卧推","chest","incline_press","barbell,bench"}
    };
    static String partCode(String value) {
        for(int i=0;i<PARTS.length;i++)if(PARTS[i].equals(value)||LEGACY[i].equals(value))return PARTS[i];
        return "unknown";
    }
    static int builtIn(String id) {
        if(!id.startsWith("builtin-"))return -1;
        try{int n=Integer.parseInt(id.substring(8));return n>=0&&n<EXERCISES.length?n:-1;}catch(NumberFormatException e){return -1;}
    }
    static String exercisePart(String id,String stored) {
        String part=partCode(stored);if(!part.equals("unknown"))return part;
        int i=builtIn(id);if(i<0)return "unknown";
        return EXERCISES[i][1];
    }
    static String movement(String id){int i=builtIn(id);return i<0?"":EXERCISES[i][2];}
    static String[] equipment(String id){int i=builtIn(id);return i<0||EXERCISES[i][3].isEmpty()?new String[0]:EXERCISES[i][3].split(",");}
    static boolean equipmentKnown(String id){int i=builtIn(id);return i>=0&&i!=5&&i!=9;}
    static boolean canReplace(String source,String target,Set<String> unavailable){
        if(source.equals(target)||builtIn(source)<0||!equipmentKnown(target)||!movement(source).equals(movement(target)))return false;
        for(String item:equipment(target))if(unavailable.contains(item))return false;
        return true;
    }
}
