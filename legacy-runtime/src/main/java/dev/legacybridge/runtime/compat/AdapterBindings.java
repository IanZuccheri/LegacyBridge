package dev.legacybridge.runtime.compat;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.legacybridge.runtime.SimpleContentPlan;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Checks inert declared bindings against compiled-in adapters. Never reflects or loads supplied class names. */
public final class AdapterBindings {
    private AdapterBindings(){}
    public static void verify(ClassLoader loader,String modId,SimpleContentPlan plan)throws IOException {
        String resource="META-INF/legacybridge/"+modId+"/adapters.json";
        try(var input=loader.getResourceAsStream(resource)) {
            if(input==null)throw unsupported("Missing adapter bindings; reconvert this mod with the matching converter");
            byte[] bytes=input.readNBytes(65_537);
            if(bytes.length>65_536)throw unsupported("Adapter bindings exceed 64 KiB");
            verify(bytes,plan);
        }
    }
    public static void verify(byte[] bytes,SimpleContentPlan plan)throws IOException {
        if(bytes.length>65_536)throw unsupported("Adapter bindings exceed 64 KiB");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try(var reader=new JsonReader(new StringReader(text))) {
            reader.setLenient(false);reader.beginObject();Set<String> rootKeys=new HashSet<>();
            Map<String,Map<String,Object>> references=new HashMap<>();
            while(reader.hasNext()) {
                String field=reader.nextName();if(!rootKeys.add(field))throw unsupported("Duplicate binding field");
                switch(field){
                    case "schemaVersion"->{if(integer(reader)!=AdapterCatalog.SCHEMA_VERSION)throw unsupported("Adapter schema mismatch");}
                    case "runtimeVersion"->{if(!string(reader).equals(AdapterCatalog.RUNTIME_VERSION))throw unsupported("Runtime adapter version mismatch");}
                    case "adapters"->{
                        reader.beginArray();
                        while(reader.hasNext()) {
                            if(references.size()>=64)throw unsupported("Too many adapter bindings");
                            reader.beginObject();Map<String,Object> ref=new HashMap<>();
                            while(reader.hasNext()) {
                                String name=reader.nextName();
                                if(ref.containsKey(name)||!Set.of("id","revision","implementationClass","method","descriptor").contains(name))throw unsupported("Unknown or duplicate adapter member");
                                Object value=name.equals("revision")?integer(reader):string(reader);ref.put(name,value);
                            }
                            reader.endObject();
                            if(!ref.keySet().equals(Set.of("id","revision","implementationClass","method","descriptor")))throw unsupported("Incomplete adapter binding");
                            String id=(String)ref.get("id");
                            var expected=AdapterCatalog.find(id).orElseThrow(()->unsupported("No implemented adapter: "+id));
                            if(!expected.referenceTree().equals(ref)||references.putIfAbsent(id,ref)!=null)throw unsupported("Adapter entrypoint/revision mismatch or duplicate");
                        }
                        reader.endArray();
                    }
                    default->throw unsupported("Unknown binding field: "+field);
                }
            }
            reader.endObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT||!rootKeys.equals(Set.of("schemaVersion","runtimeVersion","adapters")))throw unsupported("Incomplete or trailing adapter binding data");
            Set<String> required=Set.copyOf(AdapterSelection.required(!plan.items().isEmpty(),!plan.blocks().isEmpty(),
                    plan.items().stream().anyMatch(i->i.block()!=null),!plan.sounds().isEmpty(),plan.items().stream().anyMatch(i->i.properties().creativeTab()!=null)));
            if(!required.equals(references.keySet()))throw unsupported("Bindings do not cover exactly the plan's implemented adapters");
        }catch(IllegalStateException|NumberFormatException malformed){throw unsupported("Malformed adapter binding data");}
    }
    private static String string(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.STRING)throw unsupported("Expected binding string");String value=reader.nextString();
        if(value.isBlank()||value.length()>1024)throw unsupported("Invalid binding string");return value;
    }
    private static int integer(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.NUMBER)throw unsupported("Expected integer revision");String value=reader.nextString();
        if(!value.matches("[0-9]{1,8}"))throw unsupported("Invalid integer revision");return Integer.parseInt(value);
    }
    private static IOException unsupported(String reason){return new IOException("UNSUPPORTED: "+reason);}
}
