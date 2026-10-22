package dev.legacybridge.fixture.suite;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
/** Observable only after genuine Forge dispatch; never executed by the converter. */
public final class EventFlow {
    private static int trace;
    private static boolean cancelled;
    private EventFlow() { }
    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, DecisionEvent.class, EventFlow::cancel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, true, DecisionEvent.class, EventFlow::observe);
    }
    private static void cancel(DecisionEvent event) {
        trace = trace * 10 + 1;
        ((Event) event).setCanceled(true);
    }
    private static void observe(DecisionEvent event) { trace = trace * 10 + 2; }
    public static void run() { cancelled = MinecraftForge.EVENT_BUS.post(new DecisionEvent()); }
    public static int trace() { return trace; }
    public static boolean cancelled() { return cancelled; }
}
