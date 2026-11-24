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
 * is retained. The shader uses a fixed 128x128 logical projection only for this fixture. */
public final class NativeMesh implements AutoCloseable {
 static final int SIZE=128,STRIDE=128;
 private static final String[] NAMES={"selectedShader","aPos","aTex","aColor","u_Inner","u_Feather","u_CenterPos","u_MiddleAngle","u_SweepAngle","screen_dimensions","origin","u_InnerRect","u_Radius3","u_Spread","u_Corners"};
 private static final int[] WIDTHS={1,3,2,4,1,1,2,1,1,2,2,4,3,1,4};
 private GpuTexture target,source;
 private GpuTextureView targetView,sourceView;
 private GpuSampler sampler;
 private GpuBuffer vertex,index;
 private RenderPipeline pipeline;
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
 private static void color(float[] v,int rgb){v[6]=((rgb>>16)&255)/255f;v[7]=((rgb>>8)&255)/255f;v[8]=(rgb&255)/255f;v[9]=1;}
 private static void quad(ByteBuffer b,int mode,int x,int y,int w,int h,int rgb){
  for(int i=0;i<4;i++){
   float[] v=new float[32];v[0]=mode;v[1]=x+((i&2)==0?0:w);v[2]=y+((i&1)==0?0:h);v[4]=(i&2)==0?0:1;v[5]=(i&1)==0?0:1;color(v,rgb);
   if(mode==5){v[20]=x+12;v[21]=y+12;v[22]=x+w-12;v[23]=y+h-12;v[24]=12;v[27]=1;Arrays.fill(v,28,32,1);}
   if(mode==2){v[12]=x+w/2f;v[13]=y+h/2f;v[24]=23;v[11]=1;}
   if(mode==99){v[3]=3/512f;for(int k=6;k<32;k++)v[k]=k/512f;}
   for(float f:v)b.putFloat(f);
  }
 }
 private static ByteBuffer vertices(){
  ByteBuffer b=ByteBuffer.allocateDirect(20*STRIDE).order(ByteOrder.nativeOrder());
  quad(b,9,8,8,48,48,0x17d9ad);quad(b,5,72,8,48,48,0xe23419);
  quad(b,2,8,72,48,48,0x4278e5);quad(b,11,72,72,48,48,0xf0f0f0);
  quad(b,99,8,60,112,8,0);return b.flip();
 }
 private static ByteBuffer indices(){
  ByteBuffer b=ByteBuffer.allocateDirect(30*Integer.BYTES).order(ByteOrder.nativeOrder());
  for(int q=0;q<5;q++)for(int i:new int[]{0,1,2,1,3,2})b.putInt(q*4+i);
  return b.flip();
 }
 public void render(){
  if(!RenderSystem.isOnRenderThread())throw new IllegalStateException("native mesh outside render thread");
  GpuDevice device=RenderSystem.getDevice();
  if(pipeline==null)init(device);
  CommandEncoder encoder=device.createCommandEncoder();
  try(RenderPass pass=encoder.createRenderPass(()->"Native 128-byte ABI test",targetView,Optional.of(new Vector4f(16/255f,32/255f,48/255f,1)))){
   pass.setPipeline(pipeline);
   pass.bindTexture("imgTexture",sourceView,sampler);
   pass.setVertexBuffer(0,vertex.slice());
   pass.setIndexBuffer(index,IndexType.INT);
   // 26.2: indexCount, instanceCount, firstIndex, vertexOffset, firstInstance.
   pass.drawIndexed(30,1,0,0,0);
  }
  encoder.submit();
  if(++draws==1)System.out.println("[NativeApiSmoke] MESH 15 attributes stride=128 indexed=30");
  if(draws==5)System.out.println("[NativeApiSmoke] MESH_REUSED 5");
 }
 private void init(GpuDevice device){
  try{
   VertexFormat layout=format();
   pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("native_api_probe","pipeline/mesh"))
    .withVertexShader(Identifier.fromNamespaceAndPath("native_api_probe","core/mesh"))
    .withFragmentShader(Identifier.fromNamespaceAndPath("native_api_probe","core/mesh"))
    .withVertexBinding(0,layout).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
    .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL))
    .withDepthStencilState(Optional.empty())
    .withBindGroupLayout(BindGroupLayout.builder().withSampler("imgTexture").build()).build();
   target=device.createTexture("Native mesh target",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,SIZE,SIZE,1,1);
   targetView=device.createTextureView(target);
   source=device.createTexture("Native glyph coverage",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,4,4,1,1);
   sourceView=device.createTextureView(source);
   sampler=device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty());
   try(NativeImage image=new NativeImage(4,4,false)){
    for(int y=0;y<4;y++)for(int x=0;x<4;x++)image.setPixelABGR(x,y,0xff000080);
    CommandEncoder upload=device.createCommandEncoder();upload.writeToTexture(source,image);upload.submit();
   }
   vertex=device.createBuffer(()->"Native mesh vertex buffer",GpuBuffer.USAGE_VERTEX|GpuBuffer.USAGE_COPY_DST,vertices());
   index=device.createBuffer(()->"Native mesh index buffer",GpuBuffer.USAGE_INDEX|GpuBuffer.USAGE_COPY_DST,indices());
  }catch(RuntimeException|Error failure){close();throw failure;}
 }
 public void composite(GuiGraphicsExtractor g){
  if(targetView!=null)g.blit(targetView,sampler,100,40,228,168,0f,1f,0f,1f); // u0,u1,v0,v1
 }
 @Override public void close(){
  if(vertex!=null){vertex.close();vertex=null;}if(index!=null){index.close();index=null;}
  if(targetView!=null){targetView.close();targetView=null;}if(sourceView!=null){sourceView.close();sourceView=null;}
  if(target!=null){target.close();target=null;}if(source!=null){source.close();source=null;}
  if(sampler!=null){sampler.close();sampler=null;}pipeline=null;
 }
}
