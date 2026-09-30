package android.view;
public class MotionEvent { public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
 public int action, index; public float[] xs, ys;
 public MotionEvent(int a,int idx,float[] xs,float[] ys){action=a;index=idx;this.xs=xs;this.ys=ys;}
 public int getActionMasked(){return action;} public int getActionIndex(){return index;} public int getPointerCount(){return xs.length;}
 public int getPointerId(int i){return i;} public float getX(){return xs[0];} public float getY(){return ys[0];} public float getX(int i){return xs[i];} public float getY(int i){return ys[i];} }
