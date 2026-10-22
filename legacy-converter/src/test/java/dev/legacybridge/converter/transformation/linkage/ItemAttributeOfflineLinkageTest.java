package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.transformation.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Independent source/target declaration drift checks on real fixture bytes; no original class executes. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class ItemAttributeOfflineLinkageTest {
    private static final String OWN="dev/legacybridge/fixture/attributes/AttributeContentItem";
    private static final String IMMUTABLE="com/google/common/collect/ImmutableMultimap";
    private static final OfflineLinkageVerifier.CallbackPolicy POLICY=OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES;
    private static Map<Boolean,Context> contexts;

    @AfterAll static void release(){contexts=null;}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void sourceAndTargetRequireExplicitFinitePolicyAndReportNativeLimits(boolean old)throws Exception{
        var context=context(old);
        var linked=verify(context,context.api());
        assertEquals("PASSED_SCOPED",linked.status());assertEquals(2,linked.ownedClassCount());
        assertEquals(6,linked.checkedMethodCount());assertEquals(1,linked.lambdaSiteCount());
        assertEquals(false,linked.toTree().get("classesExecuted"));
        assertTrue(linked.limitations().stream().anyMatch(s->s.contains("inherited native UUID identities")));
        assertTrue(linked.limitations().stream().anyMatch(s->s.contains("No tool actions")));
        assertTrue(linked.limitations().stream().anyMatch(s->s.contains("native game execution")));
        for(var policy:List.of(OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK,
                OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK,OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK))
            assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(context.bytes(),context.api(),policy));
    }

    static Stream<Arguments> callbackDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("missing","private","protected","static","final","abstract","native","synchronized")
            .map(change->Arguments.of(old,change)));
    }
    @ParameterizedTest @MethodSource("callbackDrifts")
    void requiredNativeCallbackCannotDisappearOrChangeItsContract(boolean old,String change)throws Exception{
        var context=context(old);var callback=callback(context);
        var changed=alter(context.api(),new Member(context.item().superName,callback.name,callback.desc,false),change);
        assertThrows(TransformationException.class,()->verify(context,changed),old+" "+change);
    }

    static Stream<Arguments> uuidDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of(0,1).flatMap(index->
            Stream.of("missing","public","private","static","final","volatile")
                .map(change->Arguments.of(old,index,change))));
    }
    @ParameterizedTest @MethodSource("uuidDrifts")
    void eachInheritedUuidRequiresItsActualProtectedStaticFinalItemDeclaration(boolean old,int index,String change)throws Exception{
        var context=context(old);var field=uuidFields(context).get(index);
        var changed=alter(context.api(),new Member(context.item().superName,field.name,field.desc,true),change);
        assertThrows(TransformationException.class,()->verify(context,changed),old+" uuid"+index+" "+change);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void movingUuidToAnotherNativeAncestorIsNotAcceptedAsAnInheritedAlias(boolean old)throws Exception{
        var context=context(old);var field=uuidFields(context).get(0);
        var index=new TreeMap<>(context.api().classes());var item=index.get(context.item().superName);
        var members=new ArrayList<>(item.fields());var uuid=members.stream()
            .filter(m->m.name().equals(field.name)&&m.descriptor().equals(field.desc)).findFirst().orElseThrow();
        assertTrue(members.remove(uuid));
        String parent="fixture/external/AlteredItemBase";assertFalse(index.containsKey(parent));
        index.put(parent,new ApiIndex.ApiClass(parent,ACC_PUBLIC,item.superName(),List.of(),List.of(),List.of(uuid)));
        index.put(item.name(),new ApiIndex.ApiClass(item.name(),item.access(),parent,item.interfaces(),item.methods(),members));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)));
    }

    static Stream<Arguments> fieldDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("attributeDamage","attributeSpeed","mainhand","addition")
            .map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("fieldDrifts")
    void attributeAndEnumConstantsCannotBecomeMutable(boolean old,String role)throws Exception{
        var context=context(old);var field=externalField(context,role);
        assertThrows(TransformationException.class,()->verify(context,
            alter(context.api(),new Member(field.owner,field.name,field.desc,true),"final")));
    }

    static Stream<Arguments> guavaDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("builder","of","put","build")
            .flatMap(method->Stream.of("missing","private","static","native")
                .map(change->Arguments.of(old,method,change))));
    }
    @ParameterizedTest @MethodSource("guavaDrifts")
    void guavaDeclarationsAreNotTrustedMerelyBecauseTheirPackageIsUnchanged(boolean old,String name,String change)throws Exception{
        var context=context(old);var call=guavaCalls(context).stream().filter(c->c.name.equals(name)).findFirst().orElseThrow();
        var changed=alter(context.api(),new Member(call.owner,call.name,call.desc,false),change);
        assertThrows(TransformationException.class,()->verify(context,changed),old+" "+name+" "+change);
    }

    static Stream<Arguments> classDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("multimap","immutable","builder","modifier","attributes","slot","operation")
            .map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("classDrifts")
    void classVersusInterfaceAndEnumKindsStayPinned(boolean old,String role)throws Exception{
        var context=context(old);String owner=switch(role){
            case "multimap"->"com/google/common/collect/Multimap";
            case "immutable"->IMMUTABLE;
            case "builder"->IMMUTABLE+"$Builder";
            case "modifier"->old?"net/minecraft/entity/ai/attributes/AttributeModifier":"net/minecraft/world/entity/ai/attributes/AttributeModifier";
            case "attributes"->externalField(context,"attributeDamage").owner;
            case "slot"->externalField(context,"mainhand").owner;
            case "operation"->externalField(context,"addition").owner;
            default->throw new AssertionError(role);
        };
        var index=new TreeMap<>(context.api().classes());var type=index.get(owner);assertNotNull(type,owner);
        int flags=type.access()^(role.equals("slot")||role.equals("operation")?ACC_ENUM:ACC_INTERFACE);
        index.put(owner,new ApiIndex.ApiClass(type.name(),flags,type.superName(),type.interfaces(),type.methods(),type.fields()));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role);
    }

    static Stream<Arguments> instructionDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("builder","of","put","build").map(name->Arguments.of(old,name)));
    }
    @ParameterizedTest @MethodSource("instructionDrifts")
    void originalInvocationKindsCannotBeChangedToInterfaceOrInstanceGuesses(boolean old,String name)throws Exception{
        var context=context(old);var node=read(context.bytes().get(OWN+".class"));
        var call=node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(i->i instanceof MethodInsnNode m&&m.owner.startsWith(IMMUTABLE)&&m.name.equals(name))
            .map(i->(MethodInsnNode)i).findFirst().orElseThrow();
        int before=call.getOpcode();call.setOpcode(before==INVOKESTATIC?INVOKEVIRTUAL:INVOKEINTERFACE);call.itf=call.getOpcode()==INVOKEINTERFACE;
        assertNotEquals(before,call.getOpcode());
        var bytes=new TreeMap<>(context.bytes());var writer=new ClassWriter(0);node.accept(writer);bytes.put(OWN+".class",writer.toByteArray());
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,context.api(),POLICY));
    }

    private static Context context(boolean old)throws Exception{
        if(contexts==null){
            var bytes=new TreeMap<String,byte[]>();
            Path fixture=Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "item-attributes-1.16.5/build/libs/legacybridge-item-attributes-1.16.5-1.0.0.jar");
            try(var zip=new ZipFile(fixture.toFile())){
                for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))
                    try(var in=zip.getInputStream(entry)){bytes.put(entry.getName(),in.readAllBytes());}
            }
            var scan=new LegacyReferenceScanner().scan(bytes);
            var inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
            var mappings=VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,inputs);
            var resolver=new MappingResolver(mappings.database(),mappings.adapterBindings(),scan.classNodes().keySet());
            var mapped=new TreeMap<String,byte[]>();
            for(var node:scan.classNodes().values()){
                var target=LoggedClassRemapper.remap(node,resolver,new TransformationLog());
                var writer=new ClassWriter(0);target.accept(writer);mapped.put(target.name+".class",writer.toByteArray());
            }
            contexts=Map.of(true,new Context(Map.copyOf(bytes),mappings.sourceApi(),read(bytes.get(OWN+".class"))),
                false,new Context(Map.copyOf(mapped),mappings.targetApi(),read(mapped.get(OWN+".class"))));
        }
        return contexts.get(old);
    }
    private record Context(Map<String,byte[]> bytes,ApiIndex api,ClassNode item){}
    private record Member(String owner,String name,String descriptor,boolean field){}
    private static OfflineLinkageVerifier.Summary verify(Context context,ApiIndex api){
        return new OfflineLinkageVerifier().verify(context.bytes(),api,POLICY);
    }
    private static ClassNode read(byte[] bytes){assertNotNull(bytes);var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static MethodNode callback(Context context){return context.item().methods.stream().filter(m->!m.name.startsWith("<")).findFirst().orElseThrow();}
    private static List<FieldInsnNode> fields(Context context){
        return context.item().methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(i->i instanceof FieldInsnNode).map(i->(FieldInsnNode)i).toList();
    }
    private static List<FieldInsnNode> uuidFields(Context context){
        var fields=fields(context).stream().filter(f->f.desc.equals("Ljava/util/UUID;")).toList();assertEquals(2,fields.size());return fields;
    }
    private static FieldInsnNode externalField(Context context,String role){
        return switch(role){
            case "mainhand"->fields(context).stream().filter(f->f.name.equals("MAINHAND")).findFirst().orElseThrow();
            case "addition"->fields(context).stream().filter(f->f.name.equals("ADDITION")).findFirst().orElseThrow();
            case "attributeDamage","attributeSpeed"->fields(context).stream().filter(f->f.owner.endsWith("/Attributes"))
                .toList().get(role.equals("attributeDamage")?0:1);
            default->throw new AssertionError(role);
        };
    }
    private static List<MethodInsnNode> guavaCalls(Context context){
        return context.item().methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(i->i instanceof MethodInsnNode call&&call.owner.startsWith(IMMUTABLE)).map(i->(MethodInsnNode)i).toList();
    }
    private static ApiIndex alter(ApiIndex api,Member specification,String change){
        var index=new TreeMap<>(api.classes());var type=index.get(specification.owner());assertNotNull(type,specification.toString());
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var selected=specification.field()?fields:methods;
        var exact=selected.stream().filter(m->m.name().equals(specification.name())&&m.descriptor().equals(specification.descriptor())).toList();
        assertEquals(1,exact.size(),specification.toString());var member=exact.get(0);assertTrue(selected.remove(member));
        int flags=switch(change){
            case "private"->(member.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            case "protected"->(member.access()&~(ACC_PUBLIC|ACC_PRIVATE))|ACC_PROTECTED;
            case "public"->(member.access()&~(ACC_PRIVATE|ACC_PROTECTED))|ACC_PUBLIC;
            case "static"->member.access()^ACC_STATIC;
            case "final"->member.access()^ACC_FINAL;
            case "abstract"->member.access()^ACC_ABSTRACT;
            case "native"->member.access()^ACC_NATIVE;
            case "synchronized"->member.access()^ACC_SYNCHRONIZED;
            case "volatile"->member.access()^ACC_VOLATILE;
            case "missing"->member.access();
            default->throw new AssertionError(change);
        };
        if(!change.equals("missing")){assertNotEquals(member.access(),flags,change);selected.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),flags));}
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        return new ApiIndex(index);
    }
}
