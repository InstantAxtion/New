package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
  public enum Config { ARGB_8888, RGB_565 }
  public final BufferedImage img;
  Bitmap(int w,int h){img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
  public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);} public static Bitmap createBitmap(int[] colors,int w,int h,Config c){Bitmap b=new Bitmap(w,h); b.img.setRGB(0,0,w,h,colors,0,w); return b;} public int getWidth(){return img.getWidth();} public int getHeight(){return img.getHeight();}
}
