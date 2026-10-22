package dev.legacybridge.runtime.compat;

import java.util.List;
import java.util.TreeSet;

/** Complete dependency set for the finite fixture language, shared by emitter and runtime verifier. */
public final class AdapterSelection {
    private AdapterSelection(){}
    public static List<String> required(boolean items,boolean blocks,boolean blockItems,boolean sounds,boolean tabs){
        TreeSet<String> result=new TreeSet<>(List.of(AdapterCatalog.BOOTSTRAP,AdapterCatalog.EVENTS,
                AdapterCatalog.METADATA,AdapterCatalog.MANIFEST,AdapterCatalog.PACK));
        if(items)result.add(AdapterCatalog.ITEMS);
        if(blocks){result.add(AdapterCatalog.BLOCKS);result.add(AdapterCatalog.MINING);}
        if(blockItems)result.add(AdapterCatalog.BLOCK_ITEMS);
        if(sounds)result.add(AdapterCatalog.SOUNDS);
        if(tabs)result.add(AdapterCatalog.TABS);
        result.forEach(AdapterCatalog::require);
        return List.copyOf(result);
    }
}
