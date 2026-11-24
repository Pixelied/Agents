import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class CheckPixels {
 static BufferedImage img;static int wx,wy,ww,wh,gw,gh;
 static int sample(int x,int y){return img.getRGB(wx+(int)((x+.5)*ww/gw),wy+(int)((y+.5)*wh/gh))&0xffffff;}
 static boolean near(int a,int b){for(int s=0;s<=16;s+=8)if(Math.abs(((a>>s)&255)-((b>>s)&255))>7)return false;return true;}
 static void check(String name,int x,int y,int expected){int actual=sample(x,y);if(!near(actual,expected))throw new AssertionError(String.format("%s expected %06x got %06x",name,expected,actual));}
 public static void main(String[] a)throws Exception{
  img=ImageIO.read(new File(a[0]));wx=Integer.parseInt(a[1]);wy=Integer.parseInt(a[2]);ww=Integer.parseInt(a[3]);wh=Integer.parseInt(a[4]);gw=Integer.parseInt(a[5]);gh=Integer.parseInt(a[6]);
  check("native solid rectangle",24,20,0x17d9ad);
  check("custom channel swap shader",65,20,0x1934e2);
  check("outside nested clip",14,50,0x223344);
  check("inside nested clip",24,50,0xb251e0);
  int color=sample(20,80);int phase=near(color,0x38a5f1)?0:near(color,0xed962a)?1:-1;
  if(phase<0)throw new AssertionError(String.format("native texture color %06x",color));
  // 63 independent color samples from the supplied original shader's software OpenGL reference.
  int[][][] refs={
  {{35,11,0x3996c8},{35,47,0x3996c8},{41,19,0x3996c8}},
  {{33,36,0xffffff},{33,46,0xffffff},{33,56,0xffffff}},
  {{17,29,0x3996c8},{19,39,0x3996c8},{23,20,0x3996c8}},
  {{17,28,0x3996c8},{28,17,0x3996c8},{28,46,0x3996c8}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{42,38,0x3996c8}},
  {{3,24,0x3996c8},{3,34,0x3996c8},{7,44,0x3996c8}},
  {{7,15,0x1f4b67},{7,48,0x1f4b67},{15,7,0x1f4b67}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{46,46,0x3996c8},{22,49,0x3894c5},{49,22,0x3894c5}},
  {{12,12,0x3996c8},{40,24,0x3996c8},{12,36,0x3996c8}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{3,3,0x3996c8},{3,13,0x3996c8},{3,42,0x3996c8}},
  {{15,29,0x308dbd},{17,39,0x308dbd},{20,20,0x308dbd}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{33,33,0x3da3d9},{33,43,0x3da3d9},{33,53,0x3da3d9}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{33,33,0x3996c8},{33,43,0x3996c8},{33,53,0x3996c8}},
  {{14,30,0x235676},{27,30,0x245b7c},{41,30,0x245b7c}},
  {{7,29,0x3996c8},{12,38,0x3996c8},{16,24,0x3996c8}},
  {{7,29,0xe80305},{12,38,0xe80305},{51,25,0x025feb}}
  };
  for(int m=0;m<refs.length;m++)for(int[] p:refs[m]){
   int px=wx+(int)((100+(m%7*64+p[0]+.5)/2)*ww/gw);
   int py=wy+(int)((40+(m/7*64+p[1]+.5)/2)*wh/gh);
   int actual=img.getRGB(px,py)&0xffffff;
   int expected=(m==9&&p[0]<32&&phase==1)?0x102030:p[2];
   if(!near(actual,expected))throw new AssertionError(String.format("original shader mode %d (%d,%d) expected %06x got %06x",m,p[0],p[1],expected,actual));
  }
  // Three points outside the asymmetric native scissor must be the clear color.
  for(int[] p:new int[][]{{3,12},{12,3},{12,45}}){
   int px=wx+(int)((100+(2*64+p[0]+.5)/2)*ww/gw),py=wy+(int)((40+(64+p[1]+.5)/2)*wh/gh);
   if(!near(img.getRGB(px,py)&0xffffff,0x102030))throw new AssertionError("native scissor leaked at "+p[0]+","+p[1]);
  }
  System.out.println("PASS PIXELS: standard HUD + 63 original-shader samples, 21 matrix draws, dynamic transform and native scissor; PHASE="+phase);
 }
}
