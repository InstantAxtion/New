package android.content;
import java.util.HashMap;
public class Context { public static final int MODE_PRIVATE=0; final android.content.res.Resources r=new android.content.res.Resources();
 public android.content.res.Resources getResources(){return r;} public java.io.File getFilesDir(){ java.io.File d=new java.io.File("savetest"); d.mkdirs(); return d;}
 final HashMap<String,Integer> map=new HashMap<>();
 public SharedPreferences getSharedPreferences(String n,int m){ return new SharedPreferences(){
   public int getInt(String k,int d){Integer v=map.get(k);return v==null?d:v;}
   public Editor edit(){ return new Editor(){ public Editor putInt(String k,int v){map.put(k,v);return this;} public void apply(){} }; } }; } }
