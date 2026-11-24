package dev.renderprobe.preview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.renderprobe.ci.UniversalMesh;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.*;
import java.util.*;

public final class PreviewClient implements ClientModInitializer {
 private static final String TAG="[VapeVulkanPreview] ";
 private boolean checked,active,failed,keyDown,hudLogged,reloading;
 private int tick,shaderPhase;
 PreviewState state;
 UniversalMesh mesh;
 final List<String> catalog=PreviewState.catalog();
 private Method screenMethod;
 private Field screenField;
 private Object screenOwner;
 @Override public void onInitializeClient(){
  HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("vape421_preview","status"),(g,ticks)->drawHud(g));
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{closeMesh();if(active)System.out.println(TAG+"STOP");});
  System.out.println(TAG+"REGISTERED");
 }
 private Screen screen(Minecraft mc)throws ReflectiveOperationException{
  if(screenMethod==null&&screenField==null){
   for(Object o:new Object[]{mc.gui,mc}){
    for(String n:new String[]{"getScreen","screen","getCurrentScreen","currentScreen"}){
     try{Method m=o.getClass().getMethod(n);if(Screen.class.isAssignableFrom(m.getReturnType())){screenMethod=m;screenOwner=o;break;}}catch(NoSuchMethodException ignored){}
    }
    if(screenMethod!=null)break;
    for(String n:new String[]{"screen","currentScreen"}){
     try{Field f=o.getClass().getDeclaredField(n);if(Screen.class.isAssignableFrom(f.getType())){f.setAccessible(true);screenField=f;screenOwner=o;break;}}catch(NoSuchFieldException ignored){}
    }
    if(screenField!=null)break;
   }
   if(screenMethod==null&&screenField==null)throw new NoSuchFieldException("Minecraft current screen");
  }
  return (Screen)(screenMethod!=null?screenMethod.invoke(screenOwner):screenField.get(screenOwner));
 }
 private void tick(Minecraft mc){
  if(failed)return;
  try{
   if(!checked){
    if(mc.level==null||mc.player==null)return;
    String backend=RenderSystem.getDevice().getDeviceInfo().backendName();checked=true;
    active=backend.equalsIgnoreCase("Vulkan");
    if(!active)return;
    state=new PreviewState(mc.gameDirectory.toPath().resolve("config/vape421"));System.out.println(TAG+"BACKEND "+backend+" CATALOG "+catalog.size());
   }
   if(!active)return;
   if(mc.level==null||mc.player==null){keyDown=false;closeMesh();return;}
   boolean pressed=GLFW.glfwGetKey(mc.getWindow().handle(),GLFW.GLFW_KEY_RIGHT_SHIFT)==GLFW.GLFW_PRESS;
   Screen screen=screen(mc);
   if(pressed&&!keyDown){
    if(screen==null)mc.setScreenAndShow(new PreviewScreen(this));
    else if(screen instanceof PreviewScreen menu)menu.onClose();
   }
   keyDown=pressed;
   if(Boolean.getBoolean("vape421.preview.ci")){
    var stop=new java.io.File(mc.gameDirectory,"preview.stop");
    var reload=new java.io.File(mc.gameDirectory,"preview.reload");
    if(!reloading&&stop.delete()){mc.stop();return;}
    if(!reloading&&reload.delete()){
     reloading=true;closeMesh();mc.reloadResourcePacks().whenComplete((v,e)->{reloading=false;System.out.println(TAG+(e==null?"RELOAD_OK":"RELOAD_FAILED"));});return;
    }
   }
   if(reloading)return;
   if(state.shader){
    if(mesh==null)mesh=new UniversalMesh();
    if(tick++%20==0)mesh.render(shaderPhase++&1);
   }else closeMesh();
  }catch(Exception|LinkageError e){failed=true;active=false;closeMesh();System.err.println(TAG+"DISABLED "+e);e.printStackTrace();}
 }
 private void drawHud(GuiGraphicsExtractor g){
  Minecraft mc=Minecraft.getInstance();
  if(!active||failed||reloading||state==null||mc.level==null||mc.player==null)return;
  int y=8;
  if(state.coordinates){
   String line=String.format(Locale.ROOT,"XYZ: %.1f / %.1f / %.1f",mc.player.getX(),mc.player.getY(),mc.player.getZ());
   g.fill(7,y-2,13+mc.font.width(line),y+11,0xe01c2028);g.text(mc.font,line,10,y,0xffedf3ff,false);y+=17;
  }
  if(state.fps){
   String line="FPS: "+mc.getFps();g.fill(7,y-2,13+mc.font.width(line),y+11,0xe01c2028);g.text(mc.font,line,10,y,0xffedf3ff,false);
  }
  if(state.shader&&mesh!=null)mesh.composite(g);
  if(!hudLogged){hudLogged=true;System.out.println(TAG+"HUD_DRAW "+g.guiWidth()+" "+g.guiHeight());}
 }
 void closeMesh(){if(mesh!=null){try{mesh.close();}finally{mesh=null;}}}
}
