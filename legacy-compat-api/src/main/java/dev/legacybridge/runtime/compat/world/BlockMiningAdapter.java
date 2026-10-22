package dev.legacybridge.runtime.compat.world;

import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Legacy API: PickaxeItem.getDestroySpeed tests Material.STONE and uses the tool tier's speed.
 * Target API: PickaxeItem/DiggerItem test BlockTags.MINEABLE_WITH_PICKAXE.
 * Transformation: create an additive mineable/pickaxe tag for statically proven local STONE blocks.
 * Behavior differences: material-based eligibility becomes target data-driven eligibility.
 * Known limitations: plain STONE only; caller must reject custom materials, harvest rules and vanilla-tag overrides.
 */
public final class BlockMiningAdapter {
    public static final String TARGET_PATH="data/minecraft/tags/blocks/mineable/pickaxe.json";
    private BlockMiningAdapter(){}
    public static Map<String,Object> targetTag(String modId,List<String> stoneBlocks){
        if(modId==null||!modId.matches("[a-z][a-z0-9_]{1,63}")||java.util.Set.of("minecraft","forge","legacybridge_runtime").contains(modId))
            throw new UnsupportedCompatibilityException("Invalid mining namespace");
        if(stoneBlocks==null||stoneBlocks.size()>1024) throw new UnsupportedCompatibilityException("Mining declaration bound exceeded");
        TreeSet<String> ids=new TreeSet<>();
        for(String name:stoneBlocks){
            if(name==null||name.length()>256||!name.matches("[a-z0-9_./-]+")||name.contains("..")
                    ||name.startsWith("/")||name.endsWith("/")||name.contains("//")
                    ||java.util.Arrays.asList(name.split("/",-1)).contains(".")||!ids.add(modId+":"+name))
                throw new UnsupportedCompatibilityException("Invalid or duplicate local STONE block");
        }
        return Map.of("replace",false,"values",List.copyOf(ids));
    }
}
