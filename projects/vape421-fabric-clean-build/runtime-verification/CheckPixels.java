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
  System.out.println("PASS PIXELS: solid, custom fragment shader, nested scissors, dynamic texture PHASE="+phase);
 }
}
