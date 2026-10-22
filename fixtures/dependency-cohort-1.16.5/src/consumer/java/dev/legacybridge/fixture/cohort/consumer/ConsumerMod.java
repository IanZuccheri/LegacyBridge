package dev.legacybridge.fixture.cohort.consumer;

import dev.legacybridge.fixture.cohort.library.Score;
import net.minecraftforge.fml.common.Mod;

@Mod("lb_cohort_consumer")
public final class ConsumerMod {
    public static int observed;
    public ConsumerMod() { observed = evaluate(5); }
    public static int evaluate(int input) { return new Score(2).compute(input); }
}
