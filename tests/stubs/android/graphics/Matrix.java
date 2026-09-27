package android.graphics;
public class Matrix { public double[] m={1,0,0,0,1,0,0,0,1};
 public boolean setPolyToPoly(float[] s,int si,float[] d,int di,int n){
  double[][] A=new double[8][9];
  for(int i=0;i<4;i++){double x=s[si+2*i],y=s[si+2*i+1],u=d[di+2*i],v=d[di+2*i+1];
   A[2*i]=new double[]{x,y,1,0,0,0,-u*x,-u*y,u}; A[2*i+1]=new double[]{0,0,0,x,y,1,-v*x,-v*y,v};}
  for(int c=0;c<8;c++){int p=c;for(int r=c+1;r<8;r++)if(Math.abs(A[r][c])>Math.abs(A[p][c]))p=r; double[] t=A[c];A[c]=A[p];A[p]=t;
   if(Math.abs(A[c][c])<1e-9)return false; for(int r=0;r<8;r++){if(r==c)continue;double f=A[r][c]/A[c][c];for(int k=c;k<9;k++)A[r][k]-=f*A[c][k];}}
  for(int i=0;i<8;i++)m[i]=A[i][8]/A[i][i]; m[8]=1; return true;}
 double[] map(double x,double y){double w=m[6]*x+m[7]*y+m[8];return new double[]{(m[0]*x+m[1]*y+m[2])/w,(m[3]*x+m[4]*y+m[5])/w};}
}
