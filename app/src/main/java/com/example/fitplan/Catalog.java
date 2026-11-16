package com.example.fitplan;

final class Catalog {
    static final String[] PARTS={"chest","shoulders","back","legs","arms","core"};
    static final String[] LEGACY={"胸","肩","背","腿","手臂","核心"};
    static String partCode(String value) {
        for(int i=0;i<PARTS.length;i++)if(PARTS[i].equals(value)||LEGACY[i].equals(value))return PARTS[i];
        return "unknown";
    }
    static int builtIn(String id) {
        if(!id.startsWith("builtin-"))return -1;
        try{int n=Integer.parseInt(id.substring(8));return n>=0&&n<16?n:-1;}catch(NumberFormatException e){return -1;}
    }
    static String exercisePart(String id,String stored) {
        String part=partCode(stored);if(!part.equals("unknown"))return part;
        int i=builtIn(id);if(i<0)return "unknown";
        return PARTS[i<12?i/3:i<14?4:5];
    }
}
