package dev.renderprobe.ci;

import com.mojang.blaze3d.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import java.nio.*;
import java.util.*;

/** Independent diagnostic, not a game-client renderer hook. The original float vertex ABI
 * is retained. The original 21 fragment modes run in a 7-by-3 diagnostic atlas, not the production GUI. */
public final class UniversalMesh implements AutoCloseable {
 static final int WIDTH=448,HEIGHT=192,STRIDE=128;
 private static final String[] NAMES={"selectedShader","aPos","aTex","aColor","u_Inner","u_Feather","u_CenterPos","u_MiddleAngle","u_SweepAngle","screen_dimensions","origin","u_InnerRect","u_Radius3","u_Spread","u_Corners"};
 private static final int[] WIDTHS={1,3,2,4,1,1,2,1,1,2,2,4,3,1,4};
 private GpuTexture target,source;
 private GpuTextureView targetView,sourceView;
 private GpuSampler sampler;
 private NativeGuiBatchRenderer renderer;
 private int draws;
 private static GpuFormat floatFormat(int n){return switch(n){case 1->GpuFormat.R32_FLOAT;case 2->GpuFormat.RG32_FLOAT;case 3->GpuFormat.RGB32_FLOAT;case 4->GpuFormat.RGBA32_FLOAT;default->throw new IllegalArgumentException();};}
 static VertexFormat format(){
  var builder=VertexFormat.builder(0);
  int total=0;
  for(int i=0;i<NAMES.length;i++){builder.addAttribute(NAMES[i],floatFormat(WIDTHS[i]));total+=WIDTHS[i]*4;}
  var format=builder.build();
  if(total!=STRIDE||format.getVertexSize()!=STRIDE||format.getStepRate()!=0)throw new IllegalStateException("Wrong vertex stride or rate: "+format);
  int offset=0;for(int i=0;i<NAMES.length;i++){var a=format.getElement(NAMES[i]);if(a==null||a.offset()!=offset||a.format()!=floatFormat(WIDTHS[i]))throw new IllegalStateException("Incorrect ABI attribute "+NAMES[i]);offset+=WIDTHS[i]*4;}
  return format;
 }
 private static BatchGeometry geometry(){
  List<BatchGeometry.Part> parts=new ArrayList<>();
  for(int mode=0;mode<21;mode++){
   ByteBuffer b=ByteBuffer.allocate(4*STRIDE).order(ByteOrder.nativeOrder());
   float[] color={.24f,.64f,.85f,.9f},corners={1,1,1,1},rect={23,23,41,41},radius={18,3,2},center={32,32},origin={50,32},screen={64,64};
   float inner=12,feather=2,middle=0,sweep=180,spread=2;
   if(mode==8||mode==13){rect=new float[]{14,14,36,36};center=new float[]{2,2};spread=6;sweep=0;screen=new float[]{.2f,.6f};origin=new float[]{.8f,.9f};inner=2;}
   if(mode==12)radius=new float[]{9,3,2};
   if(mode==18){center=new float[]{14,32};origin=new float[]{50,32};radius=new float[]{3,7,0};}
   if(mode==19||mode==20){rect=new float[]{14,32,50,32};radius=new float[]{9,0,0};}
   if(mode==20){color=new float[]{0,1,1,.9f};corners=new float[]{.6f,1,1,.9f};}
   for(int i=0;i<4;i++){
    float x=(i&2)==0?0:64,y=(i&1)==0?0:64;
    b.putFloat(mode).putFloat(x).putFloat(y).putFloat(0).putFloat(x/64).putFloat(y/64);
    for(float f:color)b.putFloat(f);
    b.putFloat(inner).putFloat(feather);
    for(float f:center)b.putFloat(f);
    b.putFloat(middle).putFloat(sweep);
    for(float[] a:new float[][]{screen,origin,rect,radius})for(float f:a)b.putFloat(f);
    b.putFloat(spread);for(float f:corners)b.putFloat(f);
   }
   if(b.position()!=4*STRIDE)throw new IllegalStateException("wrong fixture ABI");
   float[] data=new float[4*32];b.flip();b.asFloatBuffer().get(data);
   int n=mode*4;parts.add(new BatchGeometry.Part(data,4,new int[]{n,n+1,n+2,n+1,n+3,n+2},n));
  }
  return BatchGeometry.pack(parts);
 }
 private static float[] transform(float sx,float sy,float tx,float ty){
  return new float[]{sx,0,0,0,0,sy,0,0,0,0,1,0,tx,ty,0,1};
 }
 private static float[] projection(){return transform(1f/224,-1f/96,-1,1);}
 private static float[] view(){return transform(2,2,4,6);}
 private static float[] model(int m,float xOffset){return transform(.5f,.5f,(m%7*64+xOffset)/2-2,(m/7*64)/2f-3);}
 private static List<NativeGuiBatchRenderer.Draw> draws(){
  List<NativeGuiBatchRenderer.Draw> out=new ArrayList<>();
  for(int m=0;m<21;m++){
   // Native target-space clipping. Mode 9 is a solid quad so its clip is independently observable.
   NativeGuiBatchRenderer.Clip clip=m==9?new NativeGuiBatchRenderer.Clip(136,88,48,32):null;
   out.add(new NativeGuiBatchRenderer.Draw(m,projection(),view(),model(m,0),clip));
  }
  return out;
 }
 public void render(){
  if(!RenderSystem.isOnRenderThread())throw new IllegalStateException("native mesh outside render thread");
  GpuDevice device=RenderSystem.getDevice();
  if(renderer==null)init(device);
  // Exercise the native buffer update path, then restore the independently known transform.
  renderer.updateMatrices(20,projection(),view(),model(20,1));
  renderer.updateMatrices(20,projection(),view(),model(20,0));
  renderer.render(targetView,sourceView,sampler,Optional.of(new Vector4f(16/255f,32/255f,48/255f,1)),WIDTH,HEIGHT);
  if(++draws==1)System.out.println("[NativeApiSmoke] UNIVERSAL 21 modes 15 attributes stride=128 indexed=126 matrix draws=21");
  if(draws==5)System.out.println("[NativeApiSmoke] UNIVERSAL_REUSED 5");
 }
 private void init(GpuDevice device){
  try{
   renderer=new NativeGuiBatchRenderer(device,format(),geometry(),draws());
   target=device.createTexture("Native mesh target",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,WIDTH,HEIGHT,1,1);
   targetView=device.createTextureView(target);
   source=device.createTexture("Native glyph coverage",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,2,2,1,1);
   sourceView=device.createTextureView(source);
   sampler=device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty());
   try(NativeImage image=new NativeImage(2,2,false)){
    // Exact 2x2 RGBA fixture used by the independently rendered GL reference.
    image.setPixelABGR(0,0,0xff285ab4);image.setPixelABGR(1,0,0xb45ab428);
    image.setPixelABGR(0,1,0x96ff7846);image.setPixelABGR(1,1,0xffffffff);
    CommandEncoder upload=device.createCommandEncoder();upload.writeToTexture(source,image);upload.submit();
   }
  }catch(RuntimeException|Error failure){close();throw failure;}
 }
 public void composite(GuiGraphicsExtractor g){
  if(targetView!=null)g.blit(targetView,sampler,100,40,324,136,0f,1f,1f,0f); // Render-target V origin is bottom-left; GUI is top-left on both backends.
 }
 @Override public void close(){
  if(renderer!=null){renderer.close();renderer=null;}
  if(targetView!=null){targetView.close();targetView=null;}if(sourceView!=null){sourceView.close();sourceView=null;}
  if(target!=null){target.close();target=null;}if(source!=null){source.close();source=null;}
  if(sampler!=null){sampler.close();sampler=null;}
 }
}
