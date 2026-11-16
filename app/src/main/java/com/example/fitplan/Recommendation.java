package com.example.fitplan;
public final class Recommendation {
    public static final class Result {
        public final double weight;public final int sets;public final String code;
        Result(double weight,int sets,String code){this.weight=weight;this.sets=sets;this.code=code;}
    }
    public static Result suggest(double baseline,double previous,double latest,int sets,boolean reduced){
        double weight=latest>=0?latest:baseline;
        String code=latest<0?"first":previous>=0&&latest>previous?"up":previous>=0&&latest<previous?"down":"same";
        return new Result(weight,reduced?Math.max(1,sets-1):sets,code);
    }
}
