import net.runelite.cache.definitions.InterfaceDefinition; import net.runelite.cache.definitions.loaders.InterfaceLoader; import java.io.File; import java.nio.file.Files; import java.util.*;
public class Dump { public static void main(String[] a) throws Exception { File d = new File(a[0]); int g = Integer.parseInt(a[1]); int from = a.length > 2 ? Integer.parseInt(a[2]) : 0, to = a.length > 3 ? Integer.parseInt(a[3]) : 99999;
 TreeMap<Integer, InterfaceDefinition> m = new TreeMap<>(); for (File f : d.listFiles()) if (f.getName().endsWith(".dat")) { int id = Integer.parseInt(f.getName().replace(".dat","")); m.put(id, new InterfaceLoader().load(g<<16|id, Files.readAllBytes(f.toPath()))); }
 String[] T = {"LAY","?1","?2","RECT","TEXT","SPR","MDL","?7","?8","LINE"};
 for (var e : m.entrySet()) { int id = e.getKey(); if (id < from || id > to) continue; InterfaceDefinition c = e.getValue(); StringBuilder s = new StringBuilder();
  s.append(String.format("%3d %-4s p%-3d %3d,%3d %3dx%-3d", id, c.type < T.length ? T[c.type] : "?"+c.type, c.parentId == -1 ? -1 : c.parentId & 0xffff, c.originalX, c.originalY, c.originalWidth, c.originalHeight));
  if (c.widthMode!=0||c.heightMode!=0||c.xPositionMode!=0||c.yPositionMode!=0) s.append(" modes"+c.widthMode+c.heightMode+c.xPositionMode+c.yPositionMode);
  if (c.isHidden) s.append(" HID"); if (c.type==5) s.append(" spr"+c.spriteId+(c.spriteTiling?" tile":"")); if (c.type==3||c.type==4||c.type==9) s.append(String.format(" col%06x", c.textColor)); if (c.type==3&&c.filled) s.append(" fill"); if (c.opacity!=0) s.append(" op"+c.opacity);
  if (c.type==4) s.append(" f"+c.fontId+" '"+c.text+"'"+(c.textShadowed?" sh":"")+" a"+c.xTextAlignment+c.yTextAlignment);
  if (c.type==0&&(c.scrollHeight>0)) s.append(" scrollH"+c.scrollHeight);
  if (c.onMouseOverListener!=null) s.append(" over"+Arrays.toString(c.onMouseOverListener)); if (c.onMouseLeaveListener!=null) s.append(" leave"+Arrays.toString(c.onMouseLeaveListener));
  if (c.actions!=null && c.actions.length>0) s.append(" ops"+Arrays.toString(c.actions)); if (c.onLoadListener!=null) s.append(" load"+Arrays.toString(c.onLoadListener));
  System.out.println(s); } } }
