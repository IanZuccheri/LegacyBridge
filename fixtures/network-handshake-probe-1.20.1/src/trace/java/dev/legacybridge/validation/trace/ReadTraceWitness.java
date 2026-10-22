package dev.legacybridge.validation.trace;

/** Finite observation classifier, not a repair or normal-mode acceptance rule. */
public final class ReadTraceWitness {
    private boolean staleClear;
    private boolean witnessed;
    public void invalidateCandidate() { staleClear = false; }
    public void observe(String kind, int argument, boolean autoRead, int interestOps, boolean clearCaller) {
        if (!kind.equals("CONFIG_SET") && !kind.equals("SELECTOR_WRITE"))
            throw new IllegalArgumentException("Unknown trace kind");
        if (interestOps < 0 || interestOps > 31 || argument < 0 || argument > 31
                || (kind.equals("CONFIG_SET") && argument > 1))
            throw new IllegalArgumentException("Invalid trace scalar");
        if (kind.equals("SELECTOR_WRITE")) {
            if ((argument & 1) != 0) staleClear = false;
            else if ((interestOps & 1) != 0) staleClear = autoRead && clearCaller;
        } else if (argument == 0) {
            staleClear = false;
        } else if (staleClear && autoRead && interestOps == 0) {
            witnessed = true;
        }
    }
    public boolean witnessed() { return witnessed; }
}
