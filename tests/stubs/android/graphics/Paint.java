package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1, FILTER_BITMAP_FLAG=2;
 public enum Style{FILL,STROKE} public enum Cap{ROUND,BUTT} public enum Align{LEFT,CENTER,RIGHT}
 int color=0xFF000000; Style style=Style.FILL; float sw=1; float ts=12; Align align=Align.LEFT;
 public Paint(){} public Paint(int f){}
 public int alpha=255; public void setAlpha(int a){alpha=a;} public int getAlpha(){return alpha;} public void setColor(int c){color=c;} public void setStyle(Style s){style=s;} public void setStrokeWidth(float w){sw=w;}
 public void setStrokeCap(Cap c){} public void setAntiAlias(boolean b){} public boolean filter; public void setFilterBitmap(boolean b){filter=b;}
 public void setTypeface(Typeface t){} public void setFakeBoldText(boolean b){} public void setTextSize(float s){ts=s;} public float getTextSize(){return ts;} public void setTextAlign(Align a){align=a;}
 public float measureText(String s){ return Canvas.metrics(ts).stringWidth(s);} 
}
