package dev.renderprobe.preview;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class PreviewState {
 private final Path file;
 boolean coordinates=true, fps=true, shader=false;
 String lastSearch="";
 PreviewState(Path directory){
  file=directory.resolve("vulkan-preview.properties");
  Properties p=new Properties();
  try{if(Files.isRegularFile(file)&&Files.size(file)<16384){try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}}}catch(IOException|IllegalArgumentException e){System.err.println("[VapeVulkanPreview] Cannot read preview settings: "+e.getClass().getSimpleName());}
  coordinates=value(p,"coordinates",true);fps=value(p,"fps",true);shader=value(p,"shader",false);
 }
 private static boolean value(Properties p,String key,boolean fallback){String v=p.getProperty(key,"");return v.equals("true")?true:v.equals("false")?false:fallback;}
 void save(){
  Properties p=new Properties();p.setProperty("coordinates",Boolean.toString(coordinates));p.setProperty("fps",Boolean.toString(fps));p.setProperty("shader",Boolean.toString(shader));
  Path tmp=null;
  try{
   Files.createDirectories(file.getParent());tmp=Files.createTempFile(file.getParent(),"vulkan-preview-",".tmp");
   try(var out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"Experimental Vulkan display settings; separate from settings.json");}
   try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
  }catch(IOException e){System.err.println("[VapeVulkanPreview] Cannot save preview settings: "+e.getClass().getSimpleName());}
  finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}
 }
 static String normalize(String s){
  StringBuilder b=new StringBuilder();s.toLowerCase(Locale.ROOT).codePoints().filter(c->!Character.isWhitespace(c)&&!Character.isSpaceChar(c)).forEach(b::appendCodePoint);return b.toString();
 }
 static List<String> catalog(){
  try(var in=PreviewState.class.getResourceAsStream("/vape421-module-classes.txt")){
   if(in==null)return List.of();
   try(var r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){return r.lines().map(String::strip).filter(s->!s.isEmpty()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
  }catch(IOException e){return List.of();}
 }
}
