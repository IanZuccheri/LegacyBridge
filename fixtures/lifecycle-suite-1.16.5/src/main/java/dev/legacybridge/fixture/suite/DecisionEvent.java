package dev.legacybridge.fixture.suite;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
/** Owned event: no constructor effects, payload, external override or hidden state. */
@Cancelable
public final class DecisionEvent extends Event {
    public DecisionEvent() { super(); }
}
