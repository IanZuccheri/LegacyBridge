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

/** Mutations occur after successful mapping compilation, independently exercising readback linkage. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class OwnedStackRepairUseOfflineLinkageTest {
    private static final String OWN="dev/legacybridge/fixture/stackuse/StackRepairUseItem";
    private static final String FORGE="net/minecraftforge/common/extensions/IForgeItem";
    private static final OfflineLinkageVerifier.CallbackPolicy POLICY=OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_REPAIR_USE;
    private static Map<Boolean,Context> contexts;
    @AfterAll static void release(){contexts=null;}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void independentPolicyProvesSixMethodsWithoutClaimingGameplayOrSynchronization(boolean old)throws Exception{
        var context=context(old);var result=verify(context,context.api());
        assertEquals("PASSED_SCOPED",result.status());assertEquals(2,result.ownedClassCount());
        assertEquals(6,result.checkedMethodCount());assertEquals(1,result.lambdaSiteCount());
        assertEquals(false,result.toTree().get("classesExecuted"));
        for(String limit:List.of("six-method", "received stack", "default-hook dispatch", "logical-client SUCCESS", "native creative use restores damage", "inventory synchronization", "multiplayer qualification"))
            assertTrue(result.limitations().stream().anyMatch(s->s.contains(limit)),limit);
        for(var policy:List.of(OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES,
                OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTE_DURABILITY,OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK,
                OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK,OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK,
                OfflineLinkageVerifier.CallbackPolicy.OWNED_EVENT_PRIORITY,OfflineLinkageVerifier.CallbackPolicy.CLIENT_CONFIG,
                OfflineLinkageVerifier.CallbackPolicy.SERVER_CONFIG,OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE_SUITE,
                OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_DAMAGE))
            assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(context.bytes(),context.api(),policy),policy.name());
    }

    static Stream<Arguments> directDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("propertyConstructor","durability","itemConstructor","getItem","getDamage","setDamage","getMax","isDamaged")
            .flatMap(role->Stream.of("missing","private","protected","static","final","abstract","native","synchronized","duplicate","descriptor")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("directDrifts")
    void everyCalledNativeMemberRetainsExactDeclarationAccessAndSignature(boolean old,String role,String change)throws Exception{
        var context=context(old);assertThrows(TransformationException.class,
            ()->verify(context,alter(context.api(),called(context,role),change)),old+" "+role+" "+change);
    }

    static Stream<Arguments> hookDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("getDamage","setDamage","getMaxDamage","isDamaged","isDamageable","self","capacity","depletable","damageableStack")
            .flatMap(role->Stream.of("missing","access","static","abstract","native","synchronized","duplicate","descriptor")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("hookDrifts")
    void indirectDamageSemanticsRequireReviewedNativeHooksAndInternalMembers(boolean old,String role,String change)throws Exception{
        var context=context(old);assertThrows(TransformationException.class,
            ()->verify(context,alter(context.api(),hook(context,role),change)),old+" "+role+" "+change);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void maximumCapacityMustRemainFinalEvenThoughHelperDoesNotCallItDirectly(boolean old)throws Exception{
        var context=context(old);assertThrows(TransformationException.class,
            ()->verify(context,alter(context.api(),hook(context,"capacity"),"final")));
    }

    static Stream<Arguments> shadowDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("getDamage","setDamage","getMaxDamage","isDamaged","isDamageable")
            .flatMap(role->Stream.of("item","siblingInterface").map(where->Arguments.of(old,role,where))));
    }
    @ParameterizedTest @MethodSource("shadowDrifts")
    void nativeHookCannotBeShadowedByAClassOrCompetingInterface(boolean old,String role,String where)throws Exception{
        var context=context(old);var hook=hook(context,role);var index=new TreeMap<>(context.api().classes());
        var item=index.get(context.item().superName);var member=new ApiIndex.ApiMember(hook.name(),hook.descriptor(),ACC_PUBLIC);
        if(where.equals("item")){
            var methods=new ArrayList<>(item.methods());methods.add(member);
            index.put(item.name(),type(item,item.superName(),item.interfaces(),methods));
        }else{
            String competing="fixture/external/CompetingDamageDefault";
            index.put(competing,new ApiIndex.ApiClass(competing,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",List.of(),List.of(member),List.of()));
            var interfaces=new ArrayList<>(item.interfaces());interfaces.add(competing);
            index.put(item.name(),type(item,item.superName(),interfaces,item.methods()));
        }
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role+" "+where);
    }

    static Stream<Arguments> helperCollisions(){
        return Stream.of(true,false).flatMap(old->Stream.of("item","interface").flatMap(where->
            Stream.of(ACC_PUBLIC,ACC_PROTECTED,ACC_PRIVATE,ACC_PUBLIC|ACC_STATIC,ACC_PUBLIC|ACC_FINAL)
                .map(access->Arguments.of(old,where,access))));
    }
    @ParameterizedTest @MethodSource("helperCollisions")
    void ownedHelperMustNeverBecomeAnAccidentalNativeMethod(boolean old,String where,int access)throws Exception{
        var context=context(old);var helper=context.item().methods.stream().filter(m->m.name.equals("repairOne")).findFirst().orElseThrow();
        var index=new TreeMap<>(context.api().classes());var owner=index.get(where.equals("item")?context.item().superName:FORGE);
        var methods=new ArrayList<>(owner.methods());methods.add(new ApiIndex.ApiMember(helper.name,helper.desc,access));
        index.put(owner.name(),type(owner,owner.superName(),owner.interfaces(),methods));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+where+" "+access);
    }

    static Stream<Arguments> classDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("item","properties","stack","forge")
            .flatMap(role->Stream.of("missing","kind","access","finality").map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("classDrifts")
    void finalNativeStackAndConcretePublicTypesCannotBeInferredFromNames(boolean old,String role,String change)throws Exception{
        var context=context(old);String owner=switch(role){case "item"->context.item().superName;case "properties"->context.item().superName+"$Properties";case "stack"->stack(old);case "forge"->FORGE;default->throw new AssertionError(role);};
        var index=new TreeMap<>(context.api().classes());var type=index.remove(owner);assertNotNull(type,owner);
        if(!change.equals("missing")){
            int flag=switch(change){case "kind"->ACC_INTERFACE;case "access"->ACC_PUBLIC;case "finality"->ACC_FINAL;default->throw new AssertionError(change);};
            index.put(owner,new ApiIndex.ApiClass(owner,type.access()^flag,type.superName(),type.interfaces(),type.methods(),type.fields()));
        }
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role+" "+change);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativeItemMustActuallyInheritTheReviewedForgeDefaults(boolean old)throws Exception{
        var context=context(old);var index=new TreeMap<>(context.api().classes());var item=index.get(context.item().superName);
        assertTrue(item.interfaces().contains(FORGE));var interfaces=new ArrayList<>(item.interfaces());interfaces.remove(FORGE);
        index.put(item.name(),type(item,item.superName(),interfaces,item.methods()));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)));
    }

    static Stream<Arguments> nativeHierarchyDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("item","properties","stack","forge")
            .flatMap(role->Stream.of("parent","emptyInterface").map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("nativeHierarchyDrifts")
    void harmlessLookingNewAncestorsStillChangeThePinnedNativeContract(boolean old,String role,String change)throws Exception{
        var context=context(old);String owner=switch(role){case "item"->context.item().superName;
            case "properties"->context.item().superName+"$Properties";case "stack"->stack(old);case "forge"->FORGE;
            default->throw new AssertionError(role);};
        var index=new TreeMap<>(context.api().classes());var original=index.get(owner);assertNotNull(original);
        String added="fixture/external/NoMethodsOrFields";String parent=original.superName();
        var interfaces=new ArrayList<>(original.interfaces());
        if(change.equals("parent")){
            index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC,parent,List.of(),List.of(),List.of()));parent=added;
        }else{
            index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",List.of(),List.of(),List.of()));
            interfaces.add(added);
        }
        index.put(owner,type(original,parent,interfaces,original.methods()));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role+" "+change);
    }

    static Stream<Arguments> nativeInterfaces(){
        return Stream.of(true,false).flatMap(old->Stream.of("item","stack").flatMap(role->
            Stream.of("remove","duplicate").map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("nativeInterfaces")
    void nativeInterfaceSetMustRemainCompleteAndUnique(boolean old,String role,String change)throws Exception{
        var context=context(old);String owner=role.equals("item")?context.item().superName:stack(old);
        var index=new TreeMap<>(context.api().classes());var original=index.get(owner);
        var interfaces=new ArrayList<>(original.interfaces());assertFalse(interfaces.isEmpty());
        if(change.equals("remove"))interfaces.remove(0);else interfaces.add(interfaces.get(0));
        index.put(owner,type(original,original.superName(),interfaces,original.methods()));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role+" "+change);
    }

    static Stream<Arguments> ownerDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("getItem","getDamage","setDamage","getMax","isDamaged","durability","capacity")
            .map(role->Arguments.of(old,role)));
    }
    @ParameterizedTest @MethodSource("ownerDrifts")
    void legallyInheritedMemberDoesNotReplacePinnedNativeDeclarationOwner(boolean old,String role)throws Exception{
        var context=context(old);var spec=role.equals("capacity")?hook(context,role):called(context,role);
        var index=new TreeMap<>(context.api().classes());var owner=index.get(spec.owner());var methods=new ArrayList<>(owner.methods());
        var member=methods.stream().filter(m->m.name().equals(spec.name())&&m.descriptor().equals(spec.descriptor())).findFirst().orElseThrow();
        assertTrue(methods.remove(member));String parent="fixture/external/MovedStackDamageDeclaration";
        index.put(parent,new ApiIndex.ApiClass(parent,ACC_PUBLIC,owner.superName(),List.of(),List.of(member),List.of()));
        index.put(owner.name(),type(owner,parent,owner.interfaces(),methods));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role);
    }

    static Stream<Arguments> bodyDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("increment","wrongReceiver","skipIdentity","duplicateWrite","falseAfterWrite","nativeOverride","opcode","interfaceFlag","hugeFrame")
            .map(change->Arguments.of(old,change)));
    }
    @ParameterizedTest @MethodSource("bodyDrifts")
    void bytecodeIsReprovedAtReadbackInsteadOfTrustingAPriorProof(boolean old,String change)throws Exception{
        var context=context(old);var item=read(context.bytes().get(OWN+".class"));
        var helper=item.methods.stream().filter(m->m.name.equals("repairOne")).findFirst().orElseThrow();
        var code=Arrays.stream(helper.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();
        switch(change){
            case "increment"->helper.instructions.set(code.get(25),new InsnNode(IADD));
            case "wrongReceiver"->((VarInsnNode)code.get(22)).var=0;
            case "skipIdentity"->((JumpInsnNode)code.get(5)).setOpcode(IF_ACMPEQ);
            case "duplicateWrite"->helper.instructions.insert(code.get(26),new MethodInsnNode(INVOKEVIRTUAL,stack(old),old?"func_196085_b":"m_41721_","(I)V",false));
            case "falseAfterWrite"->helper.instructions.set(code.get(27),new InsnNode(ICONST_0));
            case "nativeOverride"->helper.name="isDamaged";
            case "opcode"->((MethodInsnNode)code.get(26)).setOpcode(INVOKESTATIC);
            case "interfaceFlag"->((MethodInsnNode)code.get(26)).itf=true;
            case "hugeFrame"->helper.maxLocals=65535;
            default->throw new AssertionError(change);
        }
        var bytes=new TreeMap<>(context.bytes());var writer=new ClassWriter(0);item.accept(writer);bytes.put(OWN+".class",writer.toByteArray());
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,context.api(),POLICY),old+" "+change);
    }

    static Stream<Arguments> useMemberDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("use","handLookup","success","consume","pass")
            .flatMap(role->Stream.of("missing","private","protected","static","final","abstract","native","synchronized","duplicate","descriptor")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("useMemberDrifts")
    void exactNativeUseLookupAndFactoriesAreCheckedAfterMappingCompilation(boolean old,String role,String change)throws Exception{
        var context=context(old);assertThrows(TransformationException.class,
            ()->verify(context,alter(context.api(),useMember(context,role),change)),old+" "+role+" "+change);
    }

    static Stream<Arguments> useTypeDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("world","player","living","hand","holder")
            .flatMap(role->Stream.of("missing","kind","access","finality","abstract","parent","extraInterface","duplicateInterface")
                .map(change->Arguments.of(old,role,change))));
    }
    @ParameterizedTest @MethodSource("useTypeDrifts")
    void callbackTypeHierarchyAndInterfaceSetRemainPinned(boolean old,String role,String change)throws Exception{
        var context=context(old);String owner=useType(old,role);var index=new TreeMap<>(context.api().classes());
        var original=index.remove(owner);assertNotNull(original,owner);
        if(!change.equals("missing")){
            int access=original.access();String parent=original.superName();var interfaces=new ArrayList<>(original.interfaces());
            switch(change){
                case "kind"->access^=ACC_INTERFACE;case "access"->access^=ACC_PUBLIC;
                case "finality"->access^=ACC_FINAL;case "abstract"->access^=ACC_ABSTRACT;
                case "parent"->{String added="fixture/external/InsertedUseAncestor";
                    index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC,parent,List.of(),List.of(),List.of()));parent=added;}
                case "extraInterface"->{String added="fixture/external/InsertedUseInterface";
                    index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",List.of(),List.of(),List.of()));interfaces.add(added);}
                case "duplicateInterface"->{
                    if(interfaces.isEmpty())interfaces.add("java/lang/AutoCloseable");
                    interfaces.add(interfaces.get(0));}
                default->throw new AssertionError(change);
            }
            index.put(owner,new ApiIndex.ApiClass(owner,access,parent,interfaces,original.methods(),original.fields()));
        }
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+role+" "+change);
    }

    static Stream<Arguments> sideDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("missing","private","protected","static","final","volatile","transient","enum","synthetic","duplicate","descriptor","moved")
            .map(change->Arguments.of(old,change)));
    }
    @ParameterizedTest @MethodSource("sideDrifts")
    void logicalSideFieldCannotMoveOrChangeContract(boolean old,String change)throws Exception{
        var context=context(old);var index=new TreeMap<>(context.api().classes());String owner=useType(old,"world");
        var original=index.get(owner);String name=old?"field_72995_K":"f_46443_";
        var fields=new ArrayList<>(original.fields());var exact=fields.stream().filter(f->f.name().equals(name)&&f.descriptor().equals("Z")).toList();
        assertEquals(1,exact.size());var member=exact.get(0);assertTrue(fields.remove(member));
        int access=switch(change){case "private"->ACC_PRIVATE|ACC_FINAL;case "protected"->ACC_PROTECTED|ACC_FINAL;
            case "static"->member.access()^ACC_STATIC;case "final"->member.access()^ACC_FINAL;
            case "volatile"->member.access()^ACC_VOLATILE;case "transient"->member.access()^ACC_TRANSIENT;
            case "enum"->member.access()^ACC_ENUM;case "synthetic"->member.access()^ACC_SYNTHETIC;
            default->member.access();};
        if(!change.equals("missing")&&!change.equals("moved"))fields.add(new ApiIndex.ApiMember(name,change.equals("descriptor")?"I":"Z",access));
        if(change.equals("duplicate"))fields.add(member);
        String parent=original.superName();
        if(change.equals("moved")){String added="fixture/external/MovedSideField";
            index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC,parent,List.of(),List.of(),List.of(member)));parent=added;}
        index.put(owner,new ApiIndex.ApiClass(owner,original.access(),parent,original.interfaces(),original.methods(),fields));
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+change);
    }

    static Stream<Arguments> lookupShadows(){
        return Stream.of(true,false).flatMap(old->Stream.of("player","entity","playerInterface")
            .flatMap(where->Stream.of(ACC_PUBLIC,ACC_PRIVATE,ACC_PROTECTED,ACC_PUBLIC|ACC_STATIC,ACC_PUBLIC|ACC_FINAL)
                .map(access->Arguments.of(old,where,access))));
    }
    @ParameterizedTest @MethodSource("lookupShadows")
    void playerOwnedLookupMustResolveOnlyToLivingEntityWithoutCompetingDeclarations(boolean old,String where,int access)throws Exception{
        var context=context(old);var index=new TreeMap<>(context.api().classes());var spec=useMember(context,"handLookup");
        var member=new ApiIndex.ApiMember(spec.name(),spec.descriptor(),access);
        if(where.equals("playerInterface")){
            String added="fixture/external/CompetingHandLookup";
            index.put(added,new ApiIndex.ApiClass(added,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",List.of(),List.of(member),List.of()));
            var player=index.get(useType(old,"player"));var interfaces=new ArrayList<>(player.interfaces());interfaces.add(added);
            index.put(player.name(),type(player,player.superName(),interfaces,player.methods()));
        }else{
            String owner=where.equals("player")?useType(old,"player"):(old?"net/minecraft/entity/Entity":"net/minecraft/world/entity/Entity");
            var original=index.get(owner);assertNotNull(original);var methods=new ArrayList<>(original.methods());methods.add(member);
            index.put(owner,type(original,original.superName(),original.interfaces(),methods));
        }
        assertThrows(TransformationException.class,()->verify(context,new ApiIndex(index)),old+" "+where+" "+access);
    }

    static Stream<Arguments> useBodyDrifts(){
        return Stream.of(true,false).flatMap(old->Stream.of("wrongHand","wrongPlayer","secondLookup","invertedSide","clientPass","clientHelper",
                "sameStaticHelper","helperInterface","superUse","discardedHelper","unconditionalConsume","copyStack","fieldWrite","hugeFrame","unknownExternal")
            .map(change->Arguments.of(old,change)));
    }
    @ParameterizedTest @MethodSource("useBodyDrifts")
    void reopensAndRejectsAlteredUseBodiesDespitePreviouslyValidMapping(boolean old,String change)throws Exception{
        var context=context(old);var item=read(context.bytes().get(OWN+".class"));
        var use=item.methods.stream().filter(m->m.name.equals(old?"func_77659_a":"m_7203_")).findFirst().orElseThrow();
        var code=Arrays.stream(use.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();
        var getter=Arrays.stream(use.instructions.toArray()).filter(i->i instanceof MethodInsnNode m&&m.owner.equals(useType(old,"player"))).map(i->(MethodInsnNode)i).findFirst().orElseThrow();
        var helper=Arrays.stream(use.instructions.toArray()).filter(i->i instanceof MethodInsnNode m&&m.owner.equals(OWN)).map(i->(MethodInsnNode)i).findFirst().orElseThrow();
        var factories=Arrays.stream(use.instructions.toArray()).filter(i->i instanceof MethodInsnNode m&&m.owner.equals(useType(old,"holder"))).map(i->(MethodInsnNode)i).toList();
        var jumps=Arrays.stream(use.instructions.toArray()).filter(i->i instanceof JumpInsnNode).map(i->(JumpInsnNode)i).toList();
        switch(change){
            case "wrongHand"->((VarInsnNode)code.get(1)).var=0;
            case "wrongPlayer"->((VarInsnNode)code.get(0)).var=1;
            case "secondLookup"->use.instructions.insert(getter,new MethodInsnNode(getter.getOpcode(),getter.owner,getter.name,getter.desc,getter.itf));
            case "invertedSide"->jumps.get(0).setOpcode(IFNE);
            case "clientPass"->factories.get(0).name=old?"func_226250_c_":"m_19098_";
            case "clientHelper"->use.instructions.insertBefore(factories.get(0),new MethodInsnNode(INVOKEVIRTUAL,helper.owner,helper.name,helper.desc,false));
            case "sameStaticHelper"->helper.setOpcode(INVOKESTATIC);
            case "helperInterface"->helper.itf=true;
            case "superUse"->{helper.owner=item.superName;helper.name=use.name;helper.desc=use.desc;helper.setOpcode(INVOKESPECIAL);}
            case "discardedHelper"->use.instructions.insert(helper,new InsnNode(POP));
            case "unconditionalConsume"->factories.get(2).name=factories.get(1).name;
            case "copyStack"->use.instructions.insert(getter,new MethodInsnNode(INVOKEVIRTUAL,stack(old),old?"func_77946_l":"m_41777_","()L"+stack(old)+";",false));
            case "fieldWrite"->{var field=Arrays.stream(use.instructions.toArray()).filter(i->i instanceof FieldInsnNode).map(i->(FieldInsnNode)i).findFirst().orElseThrow();field.setOpcode(PUTFIELD);}
            case "hugeFrame"->use.maxStack=65535;
            case "unknownExternal"->{getter.owner="fixture/external/UnknownPlayer";}
            default->throw new AssertionError(change);
        }
        var bytes=new TreeMap<>(context.bytes());var writer=new ClassWriter(0);item.accept(writer);bytes.put(OWN+".class",writer.toByteArray());
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,context.api(),POLICY),old+" "+change);
    }

    private static String useType(boolean old,String role){return switch(role){
        case "world"->old?"net/minecraft/world/World":"net/minecraft/world/level/Level";
        case "player"->old?"net/minecraft/entity/player/PlayerEntity":"net/minecraft/world/entity/player/Player";
        case "living"->old?"net/minecraft/entity/LivingEntity":"net/minecraft/world/entity/LivingEntity";
        case "hand"->old?"net/minecraft/util/Hand":"net/minecraft/world/InteractionHand";
        case "holder"->old?"net/minecraft/util/ActionResult":"net/minecraft/world/InteractionResultHolder";
        default->throw new AssertionError(role);
    };}
    private static Member useMember(Context context,String role){boolean old=context.old();return switch(role){
        case "use"->new Member(context.item().superName,old?"func_77659_a":"m_7203_","(L"+useType(old,"world")+";L"+useType(old,"player")+";L"+useType(old,"hand")+";)L"+useType(old,"holder")+";");
        case "handLookup"->new Member(useType(old,"living"),old?"func_184586_b":"m_21120_","(L"+useType(old,"hand")+";)L"+stack(old)+";");
        case "success"->new Member(useType(old,"holder"),old?"func_226248_a_":"m_19090_","(Ljava/lang/Object;)L"+useType(old,"holder")+";");
        case "consume"->new Member(useType(old,"holder"),old?"func_226249_b_":"m_19096_","(Ljava/lang/Object;)L"+useType(old,"holder")+";");
        case "pass"->new Member(useType(old,"holder"),old?"func_226250_c_":"m_19098_","(Ljava/lang/Object;)L"+useType(old,"holder")+";");
        default->throw new AssertionError(role);
    };}

    private static Context context(boolean old)throws Exception{
        if(contexts==null){
            var source=new TreeMap<String,byte[]>();Path fixture=Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "owned-stack-repair-use-1.16.5/build/libs/legacybridge-owned-stack-repair-use-1.16.5-1.0.0.jar");
            try(var zip=new ZipFile(fixture.toFile())){
                for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))
                    try(var in=zip.getInputStream(entry)){source.put(entry.getName(),in.readAllBytes());}
            }
            var scan=new LegacyReferenceScanner().scan(source);
            var inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
            var mappings=VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,inputs);
            var resolver=new MappingResolver(mappings.database(),mappings.adapterBindings(),scan.classNodes().keySet());
            var target=new TreeMap<String,byte[]>();
            for(var node:scan.classNodes().values()){
                var mapped=LoggedClassRemapper.remap(node,resolver,new TransformationLog());var writer=new ClassWriter(0);
                mapped.accept(writer);target.put(mapped.name+".class",writer.toByteArray());
            }
            contexts=Map.of(true,new Context(Map.copyOf(source),mappings.sourceApi(),read(source.get(OWN+".class")),true),
                false,new Context(Map.copyOf(target),mappings.targetApi(),read(target.get(OWN+".class")),false));
        }
        return contexts.get(old);
    }
    private record Context(Map<String,byte[]> bytes,ApiIndex api,ClassNode item,boolean old){}
    private record Member(String owner,String name,String descriptor){}
    private static OfflineLinkageVerifier.Summary verify(Context context,ApiIndex api){return new OfflineLinkageVerifier().verify(context.bytes(),api,POLICY);}
    private static ClassNode read(byte[] bytes){assertNotNull(bytes);var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static String stack(boolean old){return old?"net/minecraft/item/ItemStack":"net/minecraft/world/item/ItemStack";}
    private static ApiIndex.ApiClass type(ApiIndex.ApiClass from,String parent,List<String> interfaces,List<ApiIndex.ApiMember> methods){
        return new ApiIndex.ApiClass(from.name(),from.access(),parent,interfaces,methods,from.fields());
    }
    private static Member called(Context context,String role){
        var call=context.item().methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i instanceof MethodInsnNode)
            .map(i->(MethodInsnNode)i).filter(c->switch(role){
                case "propertyConstructor"->c.owner.endsWith("/Item$Properties")&&c.name.equals("<init>");
                case "durability"->c.owner.endsWith("/Item$Properties")&&c.desc.startsWith("(I)");
                case "itemConstructor"->c.owner.equals(context.item().superName)&&c.name.equals("<init>");
                case "getItem"->c.owner.equals(stack(context.old()))&&c.desc.endsWith("/Item;");
                case "getDamage"->c.name.equals(context.old()?"func_77952_i":"m_41773_");
                case "setDamage"->c.name.equals(context.old()?"func_196085_b":"m_41721_");
                case "getMax"->c.name.equals(context.old()?"func_77958_k":"m_41776_");
                case "isDamaged"->c.name.equals(context.old()?"func_77951_h":"m_41768_");
                default->throw new AssertionError(role);
            }).findFirst().orElseThrow();
        return new Member(call.owner,call.name,call.desc);
    }
    private static Member hook(Context context,String role){
        boolean old=context.old();String nativeItem=context.item().superName,stack=stack(old);
        return switch(role){
            case "self"->new Member(FORGE,old?"getItem":"self","()L"+nativeItem+";");
            case "capacity"->new Member(nativeItem,old?"func_77612_l":"m_41462_","()I");
            case "depletable"->new Member(nativeItem,old?"func_77645_m":"m_41465_","()Z");
            case "damageableStack"->new Member(stack,old?"func_77984_f":"m_41763_","()Z");
            default->new Member(FORGE,role,"(L"+stack+";"+(role.equals("setDamage")?"I)V":")"+(role.startsWith("is")?"Z":"I")));
        };
    }
    private static ApiIndex alter(ApiIndex api,Member spec,String change){
        var index=new TreeMap<>(api.classes());var type=index.get(spec.owner());assertNotNull(type,spec.toString());
        var methods=new ArrayList<>(type.methods());var exact=methods.stream().filter(m->m.name().equals(spec.name())&&m.descriptor().equals(spec.descriptor())).toList();
        assertEquals(1,exact.size(),spec.toString());var member=exact.get(0);assertTrue(methods.remove(member));
        int flags=switch(change){
            case "private"->(member.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            case "protected"->(member.access()&~(ACC_PUBLIC|ACC_PRIVATE))|ACC_PROTECTED;
            case "access"->member.access()^ACC_PUBLIC;case "static"->member.access()^ACC_STATIC;
            case "final"->member.access()^ACC_FINAL;case "abstract"->member.access()^ACC_ABSTRACT;
            case "native"->member.access()^ACC_NATIVE;case "synchronized"->member.access()^ACC_SYNCHRONIZED;
            case "missing","duplicate","descriptor"->member.access();default->throw new AssertionError(change);
        };
        if(!change.equals("missing"))methods.add(new ApiIndex.ApiMember(member.name(),change.equals("descriptor")?"()Ljava/lang/Object;":member.descriptor(),flags));
        if(change.equals("duplicate"))methods.add(member);
        index.put(type.name(),type(type,type.superName(),type.interfaces(),methods));return new ApiIndex(index);
    }
}
