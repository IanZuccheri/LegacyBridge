package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.*;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.conversion.logic.IronSwordApiAudit;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded, read-only inspection of the exact authored native iron-sword graph.
 * No remapper/writer/adapters, candidate serialization, publication, class loading or game execution.
 * Resource checks are diagnostic-only and cannot broaden ContentResourceValidator's public profiles.
 */
public final class IronSwordInspector {
    private static final int CAPTURE_BYTES=2<<20, CLASS_BYTES=128<<10, RESOURCE_BYTES=64<<10;

    public IronSwordInspection inspect(IronSwordInspectionRequest request, MappingInputs inputs) throws IOException, InputRejectedException {
        var checks = new TreeMap<String,Object>();
        IronSwordInspection.Identity identity = null;
        boolean recognized = false;
        var blockers = new ArrayList<EngineError>();
        safePath(request.input());
        Map<String,byte[]> entries;
        try { entries = capture(request.input(),request.inputSha256()); }
        catch (Refused refused) { throw new InputRejectedException(refused.code,refused.getMessage(),refused); }
        try {
            require(inputs.complete() && inputs.scope()==MappingInputs.Scope.FULL_ENVIRONMENT,"SWORD_DATA_SCOPE",
                    "Complete explicitly selected source and target API/mapping data is required");
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION,"Inspecting source identity and dependencies, not converting");
            byte[] metadata = required(entries,"META-INF/mods.toml");
            var parsed = new ForgeMetadataAnalyzer().analyze(metadata);
            require(parsed.valid() && parsed.summary().mods().size()==1 && "javafml".equals(parsed.summary().modLoader())
                    && NumericVersionRange.contains(parsed.summary().loaderVersion(),"36"),"SWORD_METADATA","Expected exactly one source Forge36 JavaFML mod");
            var mod = parsed.summary().mods().get(0);
            require(!Set.of("minecraft","forge","legacybridge_runtime").contains(mod.modId()),"SWORD_IDENTITY","Reserved mod identity");
            var manifest = SimpleContentConverter.validateManifest(required(entries,"META-INF/MANIFEST.MF"));
            String version = mod.version().equals("${file.jarVersion}") ? manifest.getMainAttributes().getValue("Implementation-Version") : mod.version();
            LifecycleMetadata.verify(metadata,version,false,checks);
            // Existing strict unknown-key validation only. Discard these in-memory bytes; no rewrite is authorized or emitted.
            SimpleContentConverter.targetMetadata(metadata,mod.modId(),version);
            var classes = new TreeMap<String,byte[]>();
            entries.forEach((p,b)->{if(p.endsWith(".class")) classes.put(p,b);});
            require(classes.size()==1,"SWORD_CLASS_COUNT","Exactly one owned entry is required; no subclasses or unrelated classes");
            OperationControl.stage(OperationStage.ARCHIVE_SCAN,"Scanning the one owned class as data");
            var nodes = new LegacyReferenceScanner().scan(classes).classNodes();
            var proof = IronSwordContract.validate(nodes,mod.modId(),version);
            // This operation is source-only even though the structural contract can independently inspect target shapes.
            require(nodes.get(proof.entry()).methods.stream().anyMatch(m->m.name.equals(proof.factory()) && m.desc.equals("()Lnet/minecraft/item/Item;")),
                    "SWORD_SOURCE_NAMESPACE","Only the selected Forge 1.16.5 source namespace is admitted");
            identity = new IronSwordInspection.Identity(mod.modId(),version,proof.registryName());
            recognized = true;
            checks.put("construction",proof.toTree());
            checks.put("classCount",nodes.size()); checks.put("methodCount",3);
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION,"Checking original bytecode and source linkage without executing it");
            for(byte[] bytes:classes.values()) BytecodeValidator.validate(bytes);
            checks.put("sourceBytecode","ASM_AND_BASIC_VERIFIER_PASSED");
            checks.put("sourceLinkage",new OfflineLinkageVerifier().verify(classes,IronSwordApiAudit.sourceLinkageApi(inputs)).toTree());
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION,"Auditing real declaration contracts, without emitting mapping rules");
            checks.put("apiAudit",IronSwordApiAudit.inspect(inputs).toTree());
            OperationControl.stage(OperationStage.RESOURCE_VALIDATION,"Checking the finite original recipe, model references and translations");
            checks.put("resources",resources(entries,proof));
            blockers.add(error("SWORD_MINING_CONTRACT_CHANGED","Legacy material predicates became reloadable sword-efficient tags; no equivalent migration recipe is implemented"));
            blockers.add(error("SWORD_TOOL_ACTION_CONTRACT_CHANGED","Target sword ToolActions and sweep dispatch differ; constructor correspondence does not prove gameplay equivalence"));
            blockers.add(error("SWORD_INHERITED_BEHAVIOR_UNQUALIFIED","Inherited tier/item contracts, combat, reload and multiplayer are not qualified"));
        } catch (Refused refused) {
            blockers.add(error(refused.code,refused.getMessage()));
        } catch (SubsetRejectionException | TransformationException refused) {
            blockers.add(error("SWORD_CONTRACT_REJECTED",refused.getMessage()));
        }
        // Even a rejected diagnostic must not describe stale selected bytes as a stable snapshot.
        rehash(request.input(),request.inputSha256());
        OperationControl.checkpoint();
        return new IronSwordInspection(request.inputSha256(),recognized,Optional.ofNullable(identity),blockers,checks);
    }

    private static Map<String,Object> resources(Map<String,byte[]> entries,IronSwordContract.Proof proof) {
        String assets="assets/"+proof.modId()+"/", data="data/"+proof.modId()+"/recipes/", name=proof.registryName();
        var expected=Set.of(proof.entry()+".class","META-INF/mods.toml","META-INF/MANIFEST.MF","pack.mcmeta",
                assets+"models/item/"+name+".json",assets+"lang/en_us.json",assets+"lang/pt_br.json",data+name+".json");
        require(entries.keySet().equals(expected),"SWORD_RESOURCE_SET","Only the exact original recipe, model and two languages are inspected; unknown files cannot pass through");
        String model=assets+"models/item/"+name+".json";
        require(json(entries,model).equals(Map.of("parent","minecraft:item/handheld","textures",Map.of("layer0","minecraft:item/iron_sword"))),
                "SWORD_MODEL","Only the audited native handheld parent and iron-sword texture reference are recognized");
        require(json(entries,data+name+".json").equals(Map.of("type","minecraft:crafting_shapeless",
                "ingredients",List.of(Map.of("item","minecraft:iron_ingot")),"result",Map.of("item",proof.modId()+":"+name,"count",BigDecimal.ONE))),
                "SWORD_RECIPE","Only one exact shapeless iron-ingot ingredient and the declared sword result/count are recognized");
        String key="item."+proof.modId()+"."+name;
        for(String lang:List.of("en_us","pt_br")) {
            var translations=json(entries,assets+"lang/"+lang+".json");
            require(translations.keySet().equals(Set.of(key)) && translations.get(key) instanceof String value && text(value),
                    "SWORD_TRANSLATIONS","Each language must contain one bounded printable translation for the declared item");
        }
        var pack=json(entries,"pack.mcmeta");
        require(pack.keySet().equals(Set.of("pack")) && pack.get("pack") instanceof Map<?,?> details
                && details.keySet().equals(Set.of("pack_format","description")) && new BigDecimal(6).equals(details.get("pack_format"))
                && details.get("description") instanceof String value && text(value),"SWORD_PACK","Exact source pack format 6 and a bounded text description required");
        var hashes=new TreeMap<String,String>(); entries.forEach((p,b)->{if(!p.endsWith(".class"))hashes.put(p,SimpleContentConverter.hash(b));});
        return Map.of("status","FINITE_SOURCE_DATA_CHECKED_NOT_GAME_LOADED","filesSha256",hashes,"bytesChanged",0,
                "nativeReferenceBasis","Pinned source/target client-resource review; no native resources are bundled",
                "nativeTextureSha256","ed1fa2f83955583e70a19791455d13989e8bd93b1d7240e775a57141022bed6b",
                "nativeHandheldModelSha256","8fe990d1c011429e27df2d49d35cf8571946370eac8d0fb7497aacede4ff20f5",
                "nativeGeneratedModelSha256","63190611202e75ad7b812bc9a4b31bd53f3e186895d7b53e5e280b0d1e30a67a",
                "limitation","A schema/reference check is not recipe execution, asset loading, resource reload or conversion approval");
    }
    private static boolean text(String value) {
        return !value.isBlank() && value.length()<=512 && value.codePoints().noneMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT);
    }
    private static Map<?,?> json(Map<String,byte[]> entries,String path) {
        try {
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            Object value=BoundedJsonParser.parse(decoder.decode(ByteBuffer.wrap(required(entries,path))).toString());
            require(value instanceof Map<?,?>,"SWORD_JSON","Expected JSON object: "+path);
            return (Map<?,?>)value;
        } catch (CharacterCodingException | BoundedJsonParser.JsonSyntaxException invalid) {
            throw new Refused("SWORD_JSON","Malformed bounded UTF-8 JSON: "+path);
        }
    }
    private static Map<String,byte[]> capture(Path path,String sha) throws InputRejectedException {
        OperationControl.stage(OperationStage.INPUT_SNAPSHOT,"Capturing bounded selected original bytes");
        var entries=new TreeMap<String,byte[]>();
        var scan=new SafeJarReader().scan(path,new JarEntryVisitor(){
            int total;
            public boolean requiresBytes(String name){return true;}
            public long maxCapturedBytes(String name){return name.endsWith(".class")?CLASS_BYTES:RESOURCE_BYTES;}
            public void visit(String name,byte[] bytes){
                if((total+=bytes.length)>CAPTURE_BYTES || entries.size()>=16)throw new Refused("SWORD_CAPTURE_LIMIT","Finite inspection permits at most 16 files and 2 MiB captured bytes");
                entries.put(name,bytes);
            }
        });
        if(!scan.input().sha256().equals(sha))throw new InputRejectedException("INPUT_CHANGED","Original no longer matches the selected fingerprint");
        if(scan.archive().signed() || scan.archive().multiRelease() || scan.archive().specialEntryCount()!=0 || scan.archive().specialEntriesTruncated())
            throw new InputRejectedException("SWORD_ARCHIVE_UNSUPPORTED","Special loader entries, signatures or multi-release archive are unsupported");
        return entries;
    }
    private static void safePath(Path path) throws IOException,InputRejectedException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.equals(path.toRealPath()))
            throw new InputRejectedException("SWORD_INPUT_PATH","Select a canonical regular file, without observed symlink/junction aliases");
    }
    private static void rehash(Path path,String sha) throws IOException,InputRejectedException {
        safePath(path);
        var scan=new SafeJarReader().scan(path,new JarEntryVisitor(){public boolean requiresBytes(String name){return false;}public void visit(String name,byte[] bytes){}});
        if(!scan.input().sha256().equals(sha))throw new InputRejectedException("INPUT_CHANGED","Original changed during inspection");
    }
    private static byte[] required(Map<String,byte[]> entries,String path){byte[] bytes=entries.get(path);require(bytes!=null,"SWORD_ENTRY_MISSING","Missing entry: "+path);return bytes;}
    private static EngineError error(String code,String message){return new EngineError(code,message,
            "Keep the original unchanged. An exact semantic recipe and separate runtime qualification are required; this diagnostic never enables Convert or Apply.");}
    private static void require(boolean condition,String code,String message){if(!condition)throw new Refused(code,message);}
    private static final class Refused extends RuntimeException {
        final String code;
        Refused(String code,String message){super(message);this.code=code;}
    }
}
