package dev.renderprobe.ci;

import com.mojang.blaze3d.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import org.joml.Vector4fc;
import java.nio.*;
import java.util.*;

/** Native triangle submission core. It accepts only native resources, never GL names.
 * Callers own render-target scheduling, texture lifetime, and matrix/clip conversion.
 * A captured geometry snapshot and per-range uniform buffers may be reused across frames. */
public final class NativeGuiBatchRenderer implements AutoCloseable {
 public record Clip(int x,int y,int width,int height){}
 public record Draw(int range,float[] projection,float[] view,float[] model,Clip clip){}
 private final GpuDevice device;
 private final RenderPipeline pipeline;
 private final BatchGeometry geometry;
 private GpuBuffer vertex,index;
 private final List<GpuBuffer> uniforms=new ArrayList<>();
 private final List<Draw> draws;
 public NativeGuiBatchRenderer(GpuDevice device,VertexFormat format,BatchGeometry geometry,List<Draw> input){
  this.device=Objects.requireNonNull(device);this.geometry=Objects.requireNonNull(geometry);
  this.draws=List.copyOf(input);
  if(draws.isEmpty())throw new IllegalArgumentException("no draws");
  pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("native_api_probe","pipeline/universal_matrices"))
    .withVertexShader(Identifier.fromNamespaceAndPath("native_api_probe","core/universal"))
    .withFragmentShader(Identifier.fromNamespaceAndPath("native_api_probe","core/universal"))
    .withVertexBinding(0,format).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
    .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL))
    .withDepthStencilState(Optional.empty()).withBindGroupLayout(BindGroupLayout.builder().withSampler("imgTexture").withUniform("VapeMatrices",UniformType.UNIFORM_BUFFER).build()).build();
  try{
   vertex=device.createBuffer(()->"Native GUI batch vertices",GpuBuffer.USAGE_VERTEX|GpuBuffer.USAGE_COPY_DST,geometry.vertices());
   index=device.createBuffer(()->"Native GUI batch indices",GpuBuffer.USAGE_INDEX|GpuBuffer.USAGE_COPY_DST,geometry.indices());
   for(Draw draw:draws){
    if(draw.range()<0||draw.range()>=geometry.ranges().size())throw new IllegalArgumentException("invalid draw range");
    uniforms.add(device.createBuffer(()->"Native GUI batch matrices",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,matrixBytes(draw.projection(),draw.view(),draw.model())));
   }
  }catch(RuntimeException|Error failure){close();throw failure;}
 }
 public static ByteBuffer matrixBytes(float[]... matrices){
  if(matrices.length!=3)throw new IllegalArgumentException("expected projection, view, model");
  ByteBuffer b=ByteBuffer.allocateDirect(192).order(ByteOrder.nativeOrder());
  for(float[] m:matrices){if(m==null||m.length!=16)throw new IllegalArgumentException("invalid matrix size");for(float f:m){if(!Float.isFinite(f))throw new IllegalArgumentException("nonfinite matrix");b.putFloat(f);}}
  return b.flip();
 }
 public void updateMatrices(int draw,float[] projection,float[] view,float[] model){
  if(vertex==null)throw new IllegalStateException("renderer closed");
  CommandEncoder upload=device.createCommandEncoder();
  upload.writeToBuffer(uniforms.get(draw).slice(),matrixBytes(projection,view,model));
  upload.submit();
 }
 public void render(GpuTextureView target,GpuTextureView source,GpuSampler sampler,Optional<Vector4fc> clear,int width,int height){
  if(!RenderSystem.isOnRenderThread()||vertex==null)throw new IllegalStateException("native renderer closed or outside render thread");
  for(Draw draw:draws){Clip c=draw.clip();if(c!=null&&(c.x()<0||c.y()<0||c.width()<=0||c.height()<=0||(long)c.x()+c.width()>width||(long)c.y()+c.height()>height))throw new IllegalArgumentException("invalid target-space clip");}
  CommandEncoder encoder=device.createCommandEncoder();
  try(RenderPass pass=encoder.createRenderPass(()->"Native GUI indexed batches",target,clear)){
   pass.setPipeline(pipeline);pass.bindTexture("imgTexture",source,sampler);pass.setVertexBuffer(0,vertex.slice());pass.setIndexBuffer(index,IndexType.INT);
   for(int i=0;i<draws.size();i++){
    Draw draw=draws.get(i);BatchGeometry.Range r=geometry.ranges().get(draw.range());
    pass.setUniform("VapeMatrices",uniforms.get(i));
    Clip c=draw.clip();if(c==null)pass.disableScissor();else pass.enableScissor(c.x(),c.y(),c.width(),c.height());
    pass.drawIndexed(r.indexCount(),1,r.firstIndex(),r.vertexOffset(),0);
   }
  }
  encoder.submit();
 }
 @Override public void close(){
  for(GpuBuffer b:uniforms)b.close();uniforms.clear();
  if(vertex!=null){vertex.close();vertex=null;}if(index!=null){index.close();index=null;}
 }
}
