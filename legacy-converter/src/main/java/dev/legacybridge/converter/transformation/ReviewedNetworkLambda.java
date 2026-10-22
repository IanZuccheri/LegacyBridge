package dev.legacybridge.converter.transformation;

import java.util.Arrays;
import java.util.Set;
import org.objectweb.asm.Type;

/**
 * Finite networking SAM shapes only, not protocol, ownership-effects or scheduling proof.
 * The caller must independently prove exact owned static implementation linkage, captures,
 * registration roles, packet bounds, direction and session lifetime. No earlier profile uses this policy.
 */
public final class ReviewedNetworkLambda {
    private static final Type STRING = Type.getObjectType("java/lang/String");
    private static final Type SUPPLIER = Type.getObjectType("java/util/function/Supplier");
    private static final Set<String> BUFFERS = Set.of("net/minecraft/network/PacketBuffer", "net/minecraft/network/FriendlyByteBuf");
    private static final Set<String> CONTEXTS = Set.of("net/minecraftforge/fml/network/NetworkEvent$Context", "net/minecraftforge/network/NetworkEvent$Context");
    private static final Set<String> SERVER_PLAYERS = Set.of("net/minecraft/entity/player/ServerPlayerEntity", "net/minecraft/server/level/ServerPlayer");

    private ReviewedNetworkLambda() { }

    public static boolean accepts(String contract, String name, Type sam, Type instantiated,
                                  Type factory, Set<String> ownedNames) {
        if (contract == null || name == null || sam == null || instantiated == null || factory == null || ownedNames == null
                || sam.getSort() != Type.METHOD || instantiated.getSort() != Type.METHOD || factory.getSort() != Type.METHOD
                || !factory.getReturnType().equals(Type.getObjectType(contract))) return false;
        Type[] captured = factory.getArgumentTypes();
        Type[] arguments = instantiated.getArgumentTypes();
        Type result = instantiated.getReturnType();
        if (contract.equals("java/lang/Runnable")) {
            if (!name.equals("run") || !sam.getDescriptor().equals("()V") || !sam.equals(instantiated) || captured.length != 2) return false;
            return message(captured[0], ownedNames) && context(captured[1]);
        }
        // PacketDistributor.PLAYER.with(() -> player): exactly one captured player,
        // identity return type, not arbitrary context/connection/owned Supplier adaptation.
        if (contract.equals("java/util/function/Supplier") && captured.length == 1)
            return name.equals("get") && sam.getDescriptor().equals("()Ljava/lang/Object;")
                    && arguments.length == 0 && result.equals(captured[0])
                    && result.getSort() == Type.OBJECT && SERVER_PLAYERS.contains(result.getInternalName());
        if (captured.length != 0) return false;
        return switch (contract) {
            case "java/util/function/Supplier" -> name.equals("get") && sam.getDescriptor().equals("()Ljava/lang/Object;")
                    && arguments.length == 0 && result.equals(STRING);
            case "java/util/function/Predicate" -> name.equals("test") && sam.getDescriptor().equals("(Ljava/lang/Object;)Z")
                    && Arrays.equals(arguments, new Type[]{STRING}) && result.equals(Type.BOOLEAN_TYPE);
            case "java/util/function/BiConsumer" -> name.equals("accept") && sam.getDescriptor().equals("(Ljava/lang/Object;Ljava/lang/Object;)V")
                    && arguments.length == 2 && message(arguments[0], ownedNames)
                    && (buffer(arguments[1]) || arguments[1].equals(SUPPLIER)) && result.equals(Type.VOID_TYPE);
            case "java/util/function/Function" -> name.equals("apply") && sam.getDescriptor().equals("(Ljava/lang/Object;)Ljava/lang/Object;")
                    && arguments.length == 1 && buffer(arguments[0]) && message(result, ownedNames);
            default -> false;
        };
    }

    /** A role candidate only; the complete profile must prove which owned type is the registered immutable message. */
    private static boolean message(Type type, Set<String> ownedNames) {
        return type.getSort() == Type.OBJECT && ownedNames.contains(type.getInternalName());
    }
    private static boolean buffer(Type type) { return type.getSort() == Type.OBJECT && BUFFERS.contains(type.getInternalName()); }
    private static boolean context(Type type) { return type.getSort() == Type.OBJECT && CONTEXTS.contains(type.getInternalName()); }
}
