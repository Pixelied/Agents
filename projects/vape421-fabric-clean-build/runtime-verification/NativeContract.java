import java.io.InputStream;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
/** Compile-only contract for the standalone probe, never invokes GPU commands. */
public final class NativeContract {
 static final Identifier ID=Identifier.fromNamespaceAndPath("vulkan_probe","test");
 static void gui(GuiGraphicsExtractor g){
  Minecraft mc=Minecraft.getInstance();
  boolean inWorld=mc.level!=null;
  int w=g.guiWidth(),h=g.guiHeight();
  g.fill(1,2,3,4,0xff112233);
  g.fillGradient(1,2,3,4,0xff112233,0xff445566);
  g.enableScissor(1,2,3,4);
  g.text(mc.font,"Native probe",1,2,-1,false);
  g.blit(RenderPipelines.GUI_TEXTURED,ID,0,0,0f,0f,64,64,64,64);
  g.disableScissor();
 }
 static void texture(InputStream input)throws Exception{
  NativeImage image=NativeImage.read(input);
  image.setPixelABGR(0,0,0xff123456);
  DynamicTexture texture=new DynamicTexture((Supplier<String>)()->"Native probe",image);
  TextureManager manager=Minecraft.getInstance().getTextureManager();
  manager.register(ID,texture);
  if(manager.getTexture(ID)==texture)texture.upload();
  manager.release(ID);
 }
 static void callbacks(){
  HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,ID,(graphics,ticks)->gui(graphics));
  ClientTickEvents.END_CLIENT_TICK.register(client->{});
  ClientLifecycleEvents.CLIENT_STOPPING.register(client->{});
 }
 static String backend(){
  boolean renderThread=RenderSystem.isOnRenderThread();
  return RenderSystem.getDevice().getDeviceInfo().backendName();
 }
}
