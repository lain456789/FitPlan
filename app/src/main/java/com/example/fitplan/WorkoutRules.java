package com.example.fitplan;

/** Deterministic allocation of unfinished sets; 3 minutes per set is a product estimate. */
final class WorkoutRules {
    static final int SECONDS_PER_SET=180;
    private WorkoutRules(){}
    static int[] allocate(int[] pending,int minutes,long currentRestSeconds){
        if(pending==null||minutes<0||minutes>180||currentRestSeconds<0)throw new IllegalArgumentException("Invalid time budget");
        int total=0;for(int n:pending){if(n<0||n>20)throw new IllegalArgumentException("Invalid set count");total+=n;}
        long slots=Math.max(0,minutes*60L-currentRestSeconds)/SECONDS_PER_SET;
        int capacity=(int)Math.min(total,slots),assigned=0;int[] keep=new int[pending.length];
        // Preserve coverage in the existing exercise order, then allocate additional rounds.
        while(assigned<capacity){
            for(int i=0;i<pending.length&&assigned<capacity;i++)if(keep[i]<pending[i]){keep[i]++;assigned++;}
        }
        return keep;
    }
}
