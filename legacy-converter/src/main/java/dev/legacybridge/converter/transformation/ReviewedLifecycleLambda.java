package dev.legacybridge.converter.transformation;

import org.objectweb.asm.Type;

/** Exact zero-capture static callback SAMs; no general lambda adaptation or inferred event types. */
public final class ReviewedLifecycleLambda {
    private ReviewedLifecycleLambda() { }
    public static boolean accepts(String contract, String name, Type sam, Type instantiated, Type factory) {
        if (factory.getArgumentTypes().length != 0) return false;
        String descriptor = instantiated.getDescriptor();
        return switch (contract) {
            case "java/lang/Runnable" -> name.equals("run") && sam.getDescriptor().equals("()V") && sam.equals(instantiated);
            case "java/util/function/Consumer" -> name.equals("accept") && sam.getDescriptor().equals("(Ljava/lang/Object;)V")
                    && descriptor.equals("(Lnet/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent;)V");
            case "java/util/function/Predicate" -> name.equals("test") && sam.getDescriptor().equals("(Ljava/lang/Object;)Z")
                    && (descriptor.equals("(Lnet/minecraft/command/CommandSource;)Z")
                    || descriptor.equals("(Lnet/minecraft/commands/CommandSourceStack;)Z"));
            case "com/mojang/brigadier/Command" -> name.equals("run") && sam.getDescriptor().equals("(Lcom/mojang/brigadier/context/CommandContext;)I")
                    && sam.equals(instantiated);
            default -> false;
        };
    }
}
