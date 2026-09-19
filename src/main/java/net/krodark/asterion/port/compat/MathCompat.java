package net.krodark.asterion.port.compat;
public final class MathCompat {
 private MathCompat(){}
 public static int clamp(long v,int min,int max){return (int)Math.max(min,Math.min(max,v));}
 public static long clamp(long v,long min,long max){return Math.max(min,Math.min(max,v));}
 public static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
 public static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
