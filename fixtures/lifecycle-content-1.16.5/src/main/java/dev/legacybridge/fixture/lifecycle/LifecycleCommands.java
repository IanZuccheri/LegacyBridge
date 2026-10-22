package dev.legacybridge.fixture.lifecycle;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native Forge-bus command registration, deliberately separate from the mod lifecycle bus. */
@Mod.EventBusSubscriber(modid = LifecycleContent.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifecycleCommands {
    private static int registrationEvents;
    private static int commandExecutions;
    private static int lastInput;
    private static int lastResult;

    private LifecycleCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        registrationEvents++;
        event.getDispatcher().register(Commands.literal("bridgeprobe")
                .requires(LifecycleCommands::hasPermission)
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                        .executes(LifecycleCommands::execute)));
    }

    private static boolean hasPermission(CommandSource source) {
        return source.hasPermission(2);
    }

    private static int execute(CommandContext<CommandSource> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        commandExecutions++;
        lastInput = value;
        lastResult = LifecycleConfig.isEnabled() ? LifecycleConfig.factor() * value : 0;
        return lastResult;
    }

    public static int registrationCount() {
        return registrationEvents;
    }

    public static int executionCount() {
        return commandExecutions;
    }

    public static int lastInput() {
        return lastInput;
    }

    public static int lastResult() {
        return lastResult;
    }
}
