package com.arcanevault.mobile;

/**
 * Hypergeometric natural-land-drop estimate for a verified 99-card
 * Commander mainboard, matching the Windows EDH Lab model assumptions.
 * Seven cards opening, no mulligans, one natural draw each turn from T2.
 * Ignores ramp, tutors, tapped lands and replacement effects.
 */
final class NativeDeckLandOdds {
    private static final int MAINBOARD=99;
    private static final double[] LOG_FACTORIAL=new double[MAINBOARD+1];
    static {
        for(int n=2;n<=MAINBOARD;n++)
            LOG_FACTORIAL[n]=LOG_FACTORIAL[n-1]+Math.log(n);
    }
    private NativeDeckLandOdds(){}

    private static double logChoose(int n,int k){
        if(k<0||n<0||k>n||n>MAINBOARD)
            return Double.NEGATIVE_INFINITY;
        return LOG_FACTORIAL[n]-LOG_FACTORIAL[k]-LOG_FACTORIAL[n-k];
    }

    static double probability(int lands,int turn){
        if(lands<0||lands>MAINBOARD||turn<1||turn>7)
            throw new IllegalArgumentException("Invalid land count or turn");
        int drawn=7+turn-1;
        double logTotal=logChoose(MAINBOARD,drawn);
        double probability=0;
        for(int landed=turn;landed<=Math.min(drawn,lands);landed++){
            double logWays=logChoose(lands,landed)+
                logChoose(MAINBOARD-lands,drawn-landed);
            if(Double.isFinite(logWays))
                probability+=Math.exp(logWays-logTotal);
        }
        return Math.max(0,Math.min(1,probability));
    }
}
