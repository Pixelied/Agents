package dev.renderprobe.ci;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Optional;

public final class NativeApiSmoke implements ClientModInitializer {
 private static final Identifier TILE=Identifier.fromNamespaceAndPath("native_api_probe","dynamic/tile");
 private static final String TAG="[NativeApiSmoke] ";
 private RenderPipeline pipeline;
 private DynamicTexture texture;
 private int ticks,uploads,frames;
 private String backend;
 private static int abgr(int argb){return (argb&0xff00ff00)|((argb&255)<<16)|((argb>>>16)&255);}
 @Override public void onInitializeClient(){
  HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("native_api_probe","panel"),(graphics,delta)->draw(graphics));
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{if(texture!=null&&mc.getTextureManager().getTexture(TILE)==texture){mc.getTextureManager().release(TILE);texture=null;}System.out.println(TAG+"STOP uploads="+uploads+" frames="+frames);});
  System.out.println(TAG+"REGISTERED");
 }
 private void tick(Minecraft mc){
  if(mc.level==null)return;
  if(ticks++%20!=0)return;
  if(!RenderSystem.isOnRenderThread())throw new IllegalStateException("native texture tick not on render thread");
  if(backend==null){backend=RenderSystem.getDevice().getDeviceInfo().backendName();System.out.println(TAG+"BACKEND "+backend);}
  if(texture==null||mc.getTextureManager().getTexture(TILE)!=texture){
   texture=new DynamicTexture(()->"Native smoke texture",new NativeImage(64,64,false));
   mc.getTextureManager().register(TILE,texture);
  }
  int argb=(uploads&1)==0?0xff38a5f1:0xffed962a;
  var image=texture.getPixels();
  for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setPixelABGR(x,y,abgr(argb));
  texture.upload();uploads++;
  if(uploads<=5)System.out.println(TAG+"UPLOAD "+uploads);
 }
 private RenderPipeline makePipeline(){
  var base=RenderPipelines.GUI;
  var b=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("native_api_probe","pipeline/channel_swap"))
   .withVertexShader(base.getVertexShader()).withFragmentShader(Identifier.fromNamespaceAndPath("native_api_probe","core/channel_swap"))
   .withVertexBinding(0,base.getVertexFormatBinding(0)).withPrimitiveTopology(base.getPrimitiveTopology())
   .withCull(base.isCull()).withPolygonMode(base.getPolygonMode()).withColorTargetState(base.getColorTargetState())
   .withDepthStencilState(Optional.ofNullable(base.getDepthStencilState()));
  for(var layout:base.getBindGroupLayouts())b.withBindGroupLayout(layout);
  return b.build();
 }
 private void draw(GuiGraphicsExtractor g){
  Minecraft mc=Minecraft.getInstance();if(mc.level==null||texture==null)return;
  if(pipeline==null)pipeline=makePipeline();
  g.fill(10,10,42,30,0xff17d9ad);
  // Input color e23419, custom shader must output 1934e2.
  g.fill(pipeline,50,10,82,30,0xffe23419);
  g.fill(10,40,42,60,0xff223344);
  g.enableScissor(12,42,40,58);g.enableScissor(18,46,34,54);
  g.fill(6,36,46,64,0xffb251e0);g.disableScissor();g.disableScissor();
  g.blit(RenderPipelines.GUI_TEXTURED,TILE,10,70,0f,0f,64,64,64,64);
  g.text(mc.font,"Native render smoke",90,10,-1,false);
  if(frames++==0)System.out.println(TAG+"DRAW "+g.guiWidth()+" "+g.guiHeight());
 }
}
