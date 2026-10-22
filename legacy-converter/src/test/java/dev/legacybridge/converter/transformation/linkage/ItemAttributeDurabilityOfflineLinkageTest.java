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

/** Linkage declarations are mutated after compilation: compiler approval cannot substitute this gate. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class ItemAttributeDurabilityOfflineLinkageTest {
    private static final String OWN="dev/legacybridge/fixture/attributedurable/AttributeDurableItem";
    private static final String ENTRY="dev/legacybridge/fixture/attributedurable/ItemAttributeDurableContent";
    private static final String IMMUTABLE="com/google/common/collect/ImmutableMultimap";
    private static final OfflineLinkageVerifier.CallbackPolicy POLICY=OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTE_DURABILITY;
    private static Map<Boolean,Context> contexts;
    @AfterAll static void release(){contexts=null;}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void genuineCombinedFixtureRequiresNewFinitePolicyAndReportsCapacityNotWear(boolean old)throws Exception{
        var context=context(old);var result=verify(context,context.api());
        assertEquals("PASSED_SCOPED",result.status());assertEquals(2,result.ownedClassCount());
        assertEquals(7,result.checkedMethodCount());assertEquals(1,result.lambdaSiteCount());
        assertEquals(false,result.toTree().get("classesExecuted"));
        assertTrue(result.limitations().stream().anyMatch(s->s.contains("Capacity does not implement wear")));
        assertTrue(result.limitations().stream().anyMatch(s->s.contains("second-argument ordinary-stack iron-ingot")));
        assertTrue(result.limitations().stream().anyMatch(s->s.contains("native game execution")));
        for(var policy:List.of(OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES,
                OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK,OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK))
            assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(context.bytes(),context.api(),policy));
    }

    static Stream<Arguments> callbackDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("attribute","repair").flatMap(role->
            Stream.of("missing","private","protected","static","final","abstract","native","synchronized")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("callbackDrifts")
    void bothNativeCallbacksMustRemainDeclaredPublicVirtualMethods(boolean old,String role,String change)throws Exception{
        var context=context(old);var method=callback(context,role);
        var mutated=alter(context.api(),new Member(context.item().superName,method.name,method.desc,false),change);
        assertThrows(TransformationException.class,()->verify(context,mutated),old+" "+role+" "+change);
    }

    static Stream<Arguments> nativeCallDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("propertyConstructor","durability","getItem","builder","of","put","build")
            .flatMap(role->Stream.of("missing","private","static","final","native","synchronized")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("nativeCallDrifts")
    void propertyGetterAndGuavaMethodsAreIndependentlyChecked(boolean old,String role,String change)throws Exception{
        var context=context(old);var call=call(context,role);
        var mutated=alter(context.api(),new Member(call.owner,call.name,call.desc,false),change);
        assertThrows(TransformationException.class,()->verify(context,mutated),old+" "+role+" "+change);
    }

    static Stream<Arguments> uuidDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of(0,1).flatMap(index->
            Stream.of("missing","public","private","static","final","volatile")
                .map(change->Arguments.of(old,index,change))));
    }
    @ParameterizedTest @MethodSource("uuidDrifts")
    void inheritedUuidIdentityRequiresProtectedStaticFinalNativeItemDeclaration(boolean old,int index,String change)throws Exception{
        var context=context(old);var uuid=fields(context).stream().filter(f->f.desc.equals("Ljava/util/UUID;")).toList().get(index);
        var mutated=alter(context.api(),new Member(context.item().superName,uuid.name,uuid.desc,true),change);
        assertThrows(TransformationException.class,()->verify(context,mutated),old+" uuid"+index+" "+change);
    }

    static Stream<Arguments> ironDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("missing","private","protected","static","final","volatile")
            .map(change->Arguments.of(old,change)));
    }
    @ParameterizedTest @MethodSource("ironDrifts")
    void ironIngotCannotDisappearBecomeMutableOrLoseItsRealAccess(boolean old,String change)throws Exception{
        var context=context(old);var iron=fields(context).stream().filter(f->f.owner.endsWith("/Items")).findFirst().orElseThrow();
        var mutated=alter(context.api(),new Member(iron.owner,iron.name,iron.desc,true),change);
        assertThrows(TransformationException.class,()->verify(context,mutated),old+" iron "+change);
    }

    static Stream<Arguments> constantDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("damage","speed","slot","operation").map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("constantDrifts")
    void compositionStillRequiresImmutableAttributeAndEnumConstants(boolean old,String role)throws Exception{
        var context=context(old);var candidates=fields(context);
        var field=switch(role){
            case "damage","speed"->candidates.stream().filter(f->f.owner.endsWith("/Attributes")).toList().get(role.equals("damage")?0:1);
            case "slot"->candidates.stream().filter(f->f.name.equals("MAINHAND")).findFirst().orElseThrow();
            case "operation"->candidates.stream().filter(f->f.name.equals("ADDITION")).findFirst().orElseThrow();
            default->throw new AssertionError(role);
        };
        assertThrows(TransformationException.class,()->verify(context,alter(context.api(),new Member(field.owner,field.name,field.desc,true),"final")));
    }

    static Stream<Arguments> classDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("properties","items","stack","immutable","builder","multimap")
            .map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("classDrifts")
    void compositionRequiresActualFinalStackAndClassInterfaceKinds(boolean old,String role)throws Exception{
        var context=context(old);String owner=switch(role){
            case "properties"->context.item().superName+"$Properties";
            case "items"->old?"net/minecraft/item/Items":"net/minecraft/world/item/Items";
            case "stack"->old?"net/minecraft/item/ItemStack":"net/minecraft/world/item/ItemStack";
            case "immutable"->IMMUTABLE;
            case "builder"->IMMUTABLE+"$Builder";
            case "multimap"->"com/google/common/collect/Multimap";
            default->throw new AssertionError(role);
        };
        var index=new TreeMap<>(context.api().classes());var type=index.get(owner);assertNotNull(type,owner);
        int access=type.access()^(role.equals("stack")?ACC_FINAL:ACC_INTERFACE);
        index.put(owner,new ApiIndex.ApiClass(type.name(),access,type.superName(),type.interfaces(),type.methods(),type.fields()));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role);
    }

    static Stream<Arguments> declaringOwnerDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("attribute","repair","uuid","iron","getItem","durability")
            .map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("declaringOwnerDrifts")
    void legalInheritedResolutionCannotReplaceThePinnedNativeDeclaringOwner(boolean old,String role)throws Exception{
        var context=context(old);Member member;
        if(role.equals("attribute")||role.equals("repair")){
            var method=callback(context,role);member=new Member(context.item().superName,method.name,method.desc,false);
        }else if(role.equals("uuid")||role.equals("iron")){
            var field=fields(context).stream().filter(f->role.equals("uuid")?f.desc.equals("Ljava/util/UUID;"):f.owner.endsWith("/Items"))
                .findFirst().orElseThrow();member=new Member(role.equals("uuid")?context.item().superName:field.owner,field.name,field.desc,true);
        }else{
            var call=call(context,role);member=new Member(call.owner,call.name,call.desc,false);
        }
        var index=new TreeMap<>(context.api().classes());var type=index.get(member.owner());
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var selected=member.field()?fields:methods;
        var moved=selected.stream().filter(m->m.name().equals(member.name())&&m.descriptor().equals(member.descriptor())).findFirst().orElseThrow();
        assertTrue(selected.remove(moved));String parent="fixture/external/ChangedDeclaringOwner";assertFalse(index.containsKey(parent));
        index.put(parent,new ApiIndex.ApiClass(parent,ACC_PUBLIC,type.superName(),List.of(),member.field()?List.of():List.of(moved),member.field()?List.of(moved):List.of()));
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),parent,type.interfaces(),methods,fields));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role);
    }

    static Stream<Arguments> instructionDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("getItem","durability","builder","put").map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("instructionDrifts")
    void executableInvocationKindsCannotBeGuessedFromDescriptors(boolean old,String role)throws Exception{
        var context=context(old);var expected=call(context,role);boolean inEntry=role.equals("durability");
        String owner=inEntry?ENTRY:OWN;var node=read(context.bytes().get(owner+".class"));
        var call=node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i instanceof MethodInsnNode c
            &&c.owner.equals(expected.owner)&&c.name.equals(expected.name)&&c.desc.equals(expected.desc))
            .map(i->(MethodInsnNode)i).findFirst().orElseThrow();
        int before=call.getOpcode();call.setOpcode(before==INVOKESTATIC?INVOKEVIRTUAL:INVOKEINTERFACE);call.itf=call.getOpcode()==INVOKEINTERFACE;
        assertNotEquals(before,call.getOpcode());var bytes=new TreeMap<>(context.bytes());var writer=new ClassWriter(0);node.accept(writer);
        bytes.put(owner+".class",writer.toByteArray());
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,context.api(),POLICY));
    }

    private static Context context(boolean old)throws Exception{
        if(contexts==null){
            var source=new TreeMap<String,byte[]>();Path fixture=Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "item-attribute-durability-1.16.5/build/libs/legacybridge-item-attribute-durability-1.16.5-1.0.0.jar");
            try(var zip=new ZipFile(fixture.toFile())){
                for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))
                    try(var in=zip.getInputStream(entry)){source.put(entry.getName(),in.readAllBytes());}
            }
            var scan=new LegacyReferenceScanner().scan(source);
            var inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
            var mappings=VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,inputs);
            var resolver=new MappingResolver(mappings.database(),mappings.adapterBindings(),scan.classNodes().keySet());
            var target=new TreeMap<String,byte[]>();
            for(var node:scan.classNodes().values()){
                var mapped=LoggedClassRemapper.remap(node,resolver,new TransformationLog());var writer=new ClassWriter(0);
                mapped.accept(writer);target.put(mapped.name+".class",writer.toByteArray());
            }
            contexts=Map.of(true,new Context(Map.copyOf(source),mappings.sourceApi(),read(source.get(OWN+".class")),read(source.get(ENTRY+".class"))),
                false,new Context(Map.copyOf(target),mappings.targetApi(),read(target.get(OWN+".class")),read(target.get(ENTRY+".class"))));
        }
        return contexts.get(old);
    }
    private record Context(Map<String,byte[]> bytes,ApiIndex api,ClassNode item,ClassNode entry){}
    private record Member(String owner,String name,String descriptor,boolean field){}
    private static OfflineLinkageVerifier.Summary verify(Context context,ApiIndex api){return new OfflineLinkageVerifier().verify(context.bytes(),api,POLICY);}
    private static ClassNode read(byte[] bytes){assertNotNull(bytes);var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static MethodNode callback(Context context,String role){
        return context.item().methods.stream().filter(m->!m.name.startsWith("<")&&(role.equals("repair")?m.desc.endsWith(")Z"):m.desc.endsWith("Multimap;")))
            .findFirst().orElseThrow();
    }
    private static List<FieldInsnNode> fields(Context context){
        return context.item().methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(i->i instanceof FieldInsnNode).map(i->(FieldInsnNode)i).toList();
    }
    private static MethodInsnNode call(Context context,String role){
        var node=role.equals("propertyConstructor")||role.equals("durability")?context.entry():context.item();
        return node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i instanceof MethodInsnNode)
            .map(i->(MethodInsnNode)i).filter(c->switch(role){
                case "propertyConstructor"->c.owner.endsWith("/Item$Properties")&&c.name.equals("<init>");
                case "durability"->c.owner.endsWith("/Item$Properties")&&c.desc.startsWith("(I)");
                case "getItem"->c.owner.endsWith("/ItemStack");
                default->c.owner.startsWith(IMMUTABLE)&&c.name.equals(role);
            }).findFirst().orElseThrow();
    }
    private static ApiIndex alter(ApiIndex api,Member spec,String change){
        var index=new TreeMap<>(api.classes());var type=index.get(spec.owner());assertNotNull(type,spec.toString());
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var selected=spec.field()?fields:methods;
        var exact=selected.stream().filter(m->m.name().equals(spec.name())&&m.descriptor().equals(spec.descriptor())).toList();
        assertEquals(1,exact.size(),spec.toString());var member=exact.get(0);assertTrue(selected.remove(member));
        int flags=switch(change){
            case "private"->(member.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            case "protected"->(member.access()&~(ACC_PUBLIC|ACC_PRIVATE))|ACC_PROTECTED;
            case "public"->(member.access()&~(ACC_PRIVATE|ACC_PROTECTED))|ACC_PUBLIC;
            case "static"->member.access()^ACC_STATIC;case "final"->member.access()^ACC_FINAL;
            case "abstract"->member.access()^ACC_ABSTRACT;case "native"->member.access()^ACC_NATIVE;
            case "synchronized"->member.access()^ACC_SYNCHRONIZED;case "volatile"->member.access()^ACC_VOLATILE;
            case "missing"->member.access();default->throw new AssertionError(change);
        };
        if(!change.equals("missing")){assertNotEquals(member.access(),flags,change);selected.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),flags));}
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));return new ApiIndex(index);
    }
}
