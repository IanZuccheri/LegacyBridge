package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeDefinitionIndex;
import dev.legacybridge.converter.report.ReferenceSummary;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Streaming exact platform-prefix counts with bounded raw-reference samples. */
public final class PlatformReferenceCollector {
    public static final int SAMPLE_LIMIT = 256;
    public static final int SAMPLE_ATTEMPT_LIMIT = 4_096;
    private final Mutable minecraft = new Mutable("net/minecraft/", "com/mojang/");
    private final Mutable forge = new Mutable("net/minecraftforge/");

    public void accept(BytecodeReference reference) {
        Objects.requireNonNull(reference, "reference");
        minecraft.accept(reference);
        forge.accept(reference);
    }

    /** Commits another collector after its class scan completed successfully. */
    public void merge(PlatformReferenceCollector other) {
        Objects.requireNonNull(other, "other");
        minecraft.merge(other.minecraft);
        forge.merge(other.forge);
    }

    public ReferenceSummary minecraft(BytecodeDefinitionIndex definitions) {
        return minecraft.snapshot(definitions);
    }

    public ReferenceSummary forge(BytecodeDefinitionIndex definitions) {
        return forge.snapshot(definitions);
    }

    private static final class Mutable {
        private final List<String> prefixes;
        private long occurrences;
        private int samplingAttempts;
        private final TreeSet<BytecodeReference> samples = new TreeSet<>();
        private boolean truncated;

        private Mutable(String... prefixes) {
            this.prefixes = List.of(prefixes);
        }

        private void accept(BytecodeReference reference) {
            if (!matches(reference.owner())) return;
            occurrences = Math.addExact(occurrences, 1);
            if (samplingAttempts == SAMPLE_ATTEMPT_LIMIT) {
                truncated = true;
                return;
            }
            samplingAttempts++;
            BytecodeReference bounded = reference.boundedForReport();
            truncated |= reference.requiresReportTruncation();
            if (samples.add(bounded) && samples.size() > SAMPLE_LIMIT) {
                samples.pollLast();
                truncated = true;
            }
        }

        private ReferenceSummary snapshot(BytecodeDefinitionIndex definitions) {
            Set<String> owners = definitions.owners();
            boolean shadowedPlatformOwner = owners.stream().anyMatch(this::matches);
            List<BytecodeReference> externalSamples = samples.stream()
                    .filter(reference -> !owners.contains(reference.owner()))
                    .toList();
            return new ReferenceSummary(
                    occurrences,
                    externalSamples,
                    truncated || shadowedPlatformOwner,
                    !shadowedPlatformOwner && definitions.complete()
            );
        }

        private boolean matches(String owner) {
            for (String prefix : prefixes) {
                if (owner.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private void merge(Mutable other) {
            occurrences = Math.addExact(occurrences, other.occurrences);
            truncated |= other.truncated;
            int availableAttempts = SAMPLE_ATTEMPT_LIMIT - samplingAttempts;
            if (other.samplingAttempts > availableAttempts) {
                truncated = true;
            }
            int retainedAttempts = Math.min(availableAttempts, other.samplingAttempts);
            samplingAttempts += retainedAttempts;
            int retainedSamples = 0;
            for (BytecodeReference sample : other.samples) {
                if (retainedSamples == retainedAttempts) {
                    break;
                }
                retainedSamples++;
                if (samples.add(sample) && samples.size() > SAMPLE_LIMIT) {
                    samples.pollLast();
                    truncated = true;
                }
            }
        }
    }
}
