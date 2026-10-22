package dev.legacybridge.runtime.compat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Build-only deterministic catalog generation from the exact shared descriptors. No Forge or mod loading. */
public final class AdapterCatalogWriter {
    private AdapterCatalogWriter(){}
    public static void main(String[] args) throws Exception {
        if(args.length!=2||!AdapterCatalog.RUNTIME_VERSION.equals(args[1]))throw new IllegalArgumentException("Adapter/runtime version drift");
        Path output=Path.of(args[0]);Files.createDirectories(output.getParent());
        Map<String,Object> root=Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,"runtimeVersion",AdapterCatalog.RUNTIME_VERSION,
                "adapters",AdapterCatalog.all().stream().map(AdapterDescriptor::documentationTree).toList(),
                "unsupportedAreas",AdapterCatalog.unsupportedAreas());
        Files.writeString(output,json(root)+"\n",StandardCharsets.UTF_8);
    }
    private static String json(Object value){
        if(value instanceof String s)return quote(s);
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){
            Map<String,Object> sorted=new TreeMap<>();map.forEach((k,v)->sorted.put((String)k,v));
            return "{"+sorted.entrySet().stream().map(e->quote(e.getKey())+":"+json(e.getValue())).collect(java.util.stream.Collectors.joining(","))+"}";
        }
        if(value instanceof Iterable<?> list){java.util.List<String> entries=new java.util.ArrayList<>();list.forEach(e->entries.add(json(e)));return "["+String.join(",",entries)+"]";}
        throw new IllegalArgumentException("Unsupported catalog JSON value");
    }
    private static String quote(String s){StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray()){switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString();}
}
