package android.graphics;
public class RectF { public float left,top,right,bottom;
 public RectF(){} public RectF(float l,float t,float r,float b){set(l,t,r,b);}
 public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;} public void set(RectF o){set(o.left,o.top,o.right,o.bottom);} public void setEmpty(){set(0,0,0,0);} public boolean isEmpty(){return left>=right||top>=bottom;}
 public void offset(float dx,float dy){left+=dx;right+=dx;top+=dy;bottom+=dy;}
 public void offsetTo(float x,float y){float w=width(),h=height();left=x;top=y;right=x+w;bottom=y+h;}
 public void inset(float dx,float dy){left+=dx;top+=dy;right-=dx;bottom-=dy;}
 public float centerX(){return (left+right)/2;} public float centerY(){return (top+bottom)/2;}
 public float width(){return right-left;} public float height(){return bottom-top;}
 public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
 public static boolean intersects(RectF a,RectF b){return a.left<b.right&&b.left<a.right&&a.top<b.bottom&&b.top<a.bottom;}
}
