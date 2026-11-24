package dev.renderprobe.preview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

final class PreviewScreen extends Screen {
 private final PreviewClient client;
 private EditBox search;
 private List<String> filtered=List.of();
 private int x,y,panelWidth,panelHeight,page;
 private boolean drawn;
 private String query="";
 PreviewScreen(PreviewClient c){super(Component.literal("Vape - Vulkan preview"));client=c;}
 private void filter(String s){
  query=s;if(!s.isBlank())client.state.lastSearch=s;
  String normalized=PreviewState.normalize(s);
  filtered=client.catalog.stream().filter(n->PreviewState.normalize(n).contains(normalized)).toList();
  page=0;System.out.println("[VapeVulkanPreview] SEARCH results="+filtered.size());
 }
 private static Component label(String s){return Component.literal(s);}
 private Button button(String text,int x,int y,int w,java.util.function.Consumer<Button> press){return addRenderableWidget(Button.builder(label(text),press::accept).bounds(x,y,w,20).build());}
 private String coords(){return "Coords: "+(client.state.coordinates?"ON":"OFF");}
 private String fps(){return "FPS: "+(client.state.fps?"ON":"OFF");}
 private String shader(){return "Shader: "+(client.state.shader?"ON":"OFF");}
 @Override protected void init(){
  panelWidth=Math.min(420,width-16);panelHeight=Math.min(244,height-16);
  x=(width-panelWidth)/2;y=(height-panelHeight)/2;
  int inner=Math.max(20,panelWidth-20),a=Math.max(20,(inner-8)/3);
  button(coords(),x+10,y+34,a,b->{client.state.coordinates=!client.state.coordinates;client.state.save();b.setMessage(label(coords()));System.out.println("[VapeVulkanPreview] COORDS "+client.state.coordinates);});
  button(fps(),x+14+a,y+34,a,b->{client.state.fps=!client.state.fps;client.state.save();b.setMessage(label(fps()));});
  button(shader(),x+18+2*a,y+34,a,b->{client.state.shader=!client.state.shader;client.state.save();b.setMessage(label(shader()));});
  int recall=Math.min(68,inner/3);
  search=new EditBox(font,x+10,y+62,inner-recall-5,20,label("Search module catalog"));
  search.setMaxLength(120);search.setValue(query);search.setResponder(this::filter);addRenderableWidget(search);
  button("Recall",x+15+inner-recall-5,y+62,recall,b->{search.setValue(client.state.lastSearch);setFocused(search);search.setFocused(true);});
  button("Prev",x+10,y+panelHeight-28,46,b->{page=Math.max(0,page-1);});
  button("Next",x+60,y+panelHeight-28,46,b->{page=Math.min(maxPage(),page+1);});
  button("Close",x+panelWidth-60,y+panelHeight-28,50,b->onClose());
  filter(query);
 }
 private int rows(){return Math.max(1,(panelHeight-120)/12);}
 private int maxPage(){return Math.max(0,(filtered.size()-1)/rows());}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
  g.fill(0,0,width,height,0x99000000);
  g.fill(x,y,x+panelWidth,y+panelHeight,0xff171b22);
  g.fill(x,y,x+panelWidth,y+2,0xff49b99e);
  g.text(font,"Vape 4.4.3 | Vulkan preview",x+10,y+9,0xffedf3ff,false);
  g.text(font,"Native display controls / read-only module catalog",x+10,y+22,0xffa9b2c2,false);
  int top=y+90,bottom=y+panelHeight-34;
  g.enableScissor(x+10,top,x+panelWidth-10,bottom);
  int begin=Math.min(filtered.size(),page*rows());
  for(int i=begin;i<Math.min(filtered.size(),begin+rows());i++){
   String n=filtered.get(i);String name=n.substring(n.lastIndexOf('.')+1);
   int yy=top+(i-begin)*12;
   if((i&1)==0)g.fill(x+10,yy,x+panelWidth-10,yy+12,0xff212630);
   g.text(font,font.plainSubstrByWidth(name,Math.max(0,panelWidth-100)),x+15,yy+2,0xffdfe6f2,false);
   g.text(font,"Legacy",x+panelWidth-53,yy+2,0xff9aa4b4,false);
  }
  if(filtered.isEmpty())g.text(font,"No matching entries",x+15,top+3,0xffa9b2c2,false);
  g.disableScissor();
  g.text(font,(page+1)+"/"+(maxPage()+1)+" | "+filtered.size(),x+112,y+panelHeight-22,0xffa9b2c2,false);
  super.extractRenderState(g,mouseX,mouseY,delta);
  if(!drawn){drawn=true;System.out.println("[VapeVulkanPreview] MENU_DRAW "+width+" "+height+" "+x+" "+y+" "+panelWidth+" "+panelHeight);}
 }
 @Override public boolean isPauseScreen(){return false;}
}
