package dev.legacybridge.fixture.suite;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Fresh tree per actual Forge dispatcher event; no dispatcher retained across reloads. */
@Mod.EventBusSubscriber(modid=SuiteContent.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SuiteCommands {
    private static int registrations;
    private static int executions;
    private static int lastResult;
    private SuiteCommands() {}
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        registrations++;
        event.getDispatcher().register(Commands.literal("bridgesuite")
            .requires(SuiteCommands::permission)
            .then(Commands.literal("scale").then(Commands.argument("value",IntegerArgumentType.integer(0,100)).executes(SuiteCommands::scale)))
            .then(Commands.literal("flag").then(Commands.argument("enabled",BoolArgumentType.bool()).executes(SuiteCommands::flag))));
    }
    private static boolean permission(CommandSource source) { return source.hasPermission(2); }
    private static int scale(CommandContext<CommandSource> context) {
        int value = IntegerArgumentType.getInteger(context,"value");
        executions++; lastResult=SuiteConfig.isEnabled() ? SuiteConfig.factor()*value : 0; return lastResult;
    }
    private static int flag(CommandContext<CommandSource> context) {
        boolean value=BoolArgumentType.getBool(context,"enabled");
        executions++; lastResult=value ? 1 : 0; return lastResult;
    }
    public static int registrations() { return registrations; }
    public static int executions() { return executions; }
    public static int lastResult() { return lastResult; }
}
