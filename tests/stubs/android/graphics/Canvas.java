package android.graphics;
import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import java.util.ArrayDeque;
public class Canvas {
 final Graphics2D g; final ArrayDeque<AffineTransform> stack=new ArrayDeque<>(); final ArrayDeque<Shape> clips=new ArrayDeque<>(); final ArrayDeque<Matrix> pstack=new ArrayDeque<>(); Matrix P;
 static final Graphics2D MG=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
 static FontMetrics metrics(float ts){return MG.getFontMetrics(new Font("SansSerif",Font.BOLD,Math.round(ts)));}
 public Canvas(Bitmap b){this(b.img.createGraphics());}
 public Canvas(Graphics2D g){this.g=g; g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);}
 void ap(Paint p){ g.setColor(new Color(p.color,true)); g.setStroke(new BasicStroke(p.sw,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); }
 void sh(Shape s,Paint p){ap(p); if(p.style==Paint.Style.FILL) g.fill(s); else g.draw(s);}
 public int save(){stack.push(g.getTransform()); clips.push(g.getClip()==null?new Rectangle(0,0,100000,100000):g.getClip());pstack.push(P==null?new Matrix():P);return stack.size();}
 public void concat(Matrix mm){P=mm; Matrix c=new Matrix(); c.m=mm.m.clone(); P=c;}
 public void drawPath(Path p,Paint pa){sh(p.p,pa);}
 public void drawBitmap(Bitmap bm,Rect s,RectF d,Paint p){java.awt.Composite oc=g.getComposite(); if(p!=null&&p.alpha<255) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,p.alpha/255f)); g.drawImage(bm.img,Math.round(d.left),Math.round(d.top),Math.round(d.right),Math.round(d.bottom),s.left,s.top,s.right,s.bottom,null); g.setComposite(oc);}
 public void restore(){g.setTransform(stack.pop()); AffineTransform t=g.getTransform(); g.setTransform(new AffineTransform()); Shape cl=clips.pop(); g.setClip(null); g.setTransform(t); Matrix q=pstack.pop();P=q;}
 public void translate(float x,float y){g.translate(x,y);} public void scale(float x,float y){g.scale(x,y);}
 public void clipRect(float l, float t, float r, float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));}
 public void clipRect(RectF r){g.clip(new Rectangle2D.Float(r.left,r.top,r.width(),r.height()));}
 public void drawRect(RectF r,Paint p){drawRect(r.left,r.top,r.right,r.bottom,p);}
 public void rotate(float d){g.rotate(Math.toRadians(d));}
 public void drawColor(int c){AffineTransform t=g.getTransform(); g.setTransform(new AffineTransform()); g.setColor(new Color(c,true)); g.fillRect(0,0,10000,10000); g.setTransform(t);}
 public void drawRect(float l,float t,float r,float b,Paint p){ if(P!=null){Path2D.Double q=new Path2D.Double(); double[] a=P.map(l,t);q.moveTo(a[0],a[1]); a=P.map(r,t);q.lineTo(a[0],a[1]); a=P.map(r,b);q.lineTo(a[0],a[1]); a=P.map(l,b);q.lineTo(a[0],a[1]); q.closePath(); sh(q,p);} else sh(new Rectangle2D.Float(l,t,r-l,b-t),p);}
 public void drawCircle(float x,float y,float r,Paint p){sh(new Ellipse2D.Float(x-r,y-r,2*r,2*r),p);}
 public void drawOval(RectF o,Paint p){sh(new Ellipse2D.Float(o.left,o.top,o.width(),o.height()),p);}
 public void drawArc(RectF o,float start,float sweep,boolean center,Paint p){ap(p); g.draw(new Arc2D.Float(o.left,o.top,o.width(),o.height(),-start,-sweep,center?Arc2D.PIE:Arc2D.OPEN));}
 public void drawRoundRect(RectF o,float rx,float ry,Paint p){sh(new RoundRectangle2D.Float(o.left,o.top,o.width(),o.height(),rx*2,ry*2),p);}
 public void drawLine(float a,float b,float c,float d,Paint p){ap(p); g.draw(new Line2D.Float(a,b,c,d));}
 public void drawBitmap(Bitmap bm,float x,float y,Paint p){g.drawImage(bm.img,(int)x,(int)y,null);}
 public void drawText(String s,float x,float y,Paint p){ g.setColor(new Color(p.color,true)); Font f=new Font("SansSerif",Font.BOLD,Math.round(p.ts)); g.setFont(f);
   float w=g.getFontMetrics().stringWidth(s); if(p.align==Paint.Align.CENTER)x-=w/2; else if(p.align==Paint.Align.RIGHT)x-=w; g.drawString(s,x,y);}
}
