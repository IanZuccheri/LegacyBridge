package dev.legacybridge.fixture.logic;

/** Exercises mod-owned inheritance, super dispatch, caught exceptions, a loop and a switch. */
public final class ParityRule extends BaseRule implements ScoreRule {
    public ParityRule(int bias) {
        super(bias);
    }

    @Override
    public int score(int input) {
        int base;
        try {
            base = super.score(input);
        } catch (IllegalArgumentException expected) {
            return -1000 - bias;
        }
        int adjustment = 0;
        for (int index = 0; index < 3; index++) {
            adjustment += index;
        }
        switch (input & 3) {
            case 0:
                adjustment += 1;
                break;
            case 1:
                adjustment += 4;
                break;
            case 2:
                adjustment += 7;
                break;
            default:
                adjustment += 10;
        }
        return LogicMath.shift(bias).applyAsInt(LogicMath.applyMethodReference(base) + adjustment);
    }
}
