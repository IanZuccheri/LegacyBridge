package dev.legacybridge.converter.mapping;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable, namespace-specific mapping index. Missing and conflicting symbols fail closed. */
public final class MappingDatabase {
    private final Map<LegacySymbol, MappingRule> rules;

    private MappingDatabase(Map<LegacySymbol, MappingRule> rules) {
        this.rules = Collections.unmodifiableMap(new TreeMap<>(rules));
    }

    public static Builder builder() {
        return new Builder();
    }

    public int size() {
        return rules.size();
    }

    public Collection<MappingRule> rules() {
        return rules.values();
    }

    public MappingRule resolve(LegacySymbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        MappingRule explicit = rules.get(symbol);
        if (explicit != null) {
            return explicit;
        }
        if (symbol.kind() == SymbolKind.DESCRIPTOR) {
            return resolveDescriptor(symbol.descriptor());
        }
        return unresolved(symbol, List.of("No mapping exists for the exact source symbol"));
    }

    /**
     * Maps all class components or returns no target. This checks descriptor syntax, not method semantics.
     * JDK reference types pass through only here; they do not create general API mapping rules.
     */
    public MappingRule resolveDescriptor(String descriptor) {
        LegacySymbol symbol = LegacySymbol.descriptor(descriptor);
        MappingRule explicit = rules.get(symbol);
        if (explicit != null) {
            return explicit;
        }
        var state = new DescriptorState();
        Type input = Type.getType(descriptor);
        Type output = mapType(input, state);
        if (state.status != MappingStatus.RESOLVED) {
            MappingCategory category = switch (state.status) {
                case ADAPTER_REQUIRED -> MappingCategory.ADAPTER_REQUIRED;
                case UNSUPPORTED -> MappingCategory.REMOVED_API;
                default -> MappingCategory.NO_AUTOMATIC_EQUIVALENT;
            };
            return new MappingRule(symbol, null, category, state.confidence, state.status,
                    MappingRule.combineEvidence(state.evidence, List.of(),
                            "Descriptor resolution is all-or-nothing; no partial target is exposed"));
        }
        String mapped = output.getDescriptor();
        var evidence = MappingRule.combineEvidence(state.evidence, List.of(),
                "Descriptor class components resolved; this does not establish call semantics");
        return new MappingRule(symbol, TargetSymbol.descriptor(mapped),
                mapped.equals(descriptor) ? MappingCategory.IDENTITY : MappingCategory.DESCRIPTOR_CHANGE,
                state.confidence, MappingStatus.RESOLVED, evidence);
    }

    private Type mapType(Type type, DescriptorState state) {
        if (type.getSort() == Type.OBJECT) {
            String owner = type.getInternalName();
            MappingRule rule = rules.get(LegacySymbol.classSymbol(owner));
            if (rule == null && (owner.startsWith("java/") || owner.startsWith("javax/"))) {
                state.addEvidence("JDK descriptor type retained: " + owner);
                return type;
            }
            if (rule == null) {
                rule = unresolved(LegacySymbol.classSymbol(owner), List.of("Unresolved descriptor type: " + owner));
            }
            state.accept(rule);
            return rule.status() == MappingStatus.RESOLVED ? Type.getObjectType(rule.target().owner()) : type;
        }
        if (type.getSort() == Type.ARRAY) {
            Type element = mapType(type.getElementType(), state);
            return Type.getType("[".repeat(type.getDimensions()) + element.getDescriptor());
        }
        if (type.getSort() == Type.METHOD) {
            Type[] arguments = type.getArgumentTypes();
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = mapType(arguments[i], state);
            }
            return Type.getMethodType(mapType(type.getReturnType(), state), arguments);
        }
        return type;
    }

    private static MappingRule unresolved(LegacySymbol symbol, List<String> evidence) {
        return new MappingRule(symbol, null, MappingCategory.NO_AUTOMATIC_EQUIVALENT,
                MappingConfidence.NONE, MappingStatus.MANUAL, evidence);
    }

    private static int unresolvedPriority(MappingStatus status) {
        return switch (status) {
            case RESOLVED -> 0;
            case ADAPTER_REQUIRED -> 1;
            case MANUAL -> 2;
            case UNSUPPORTED -> 3;
        };
    }

    private static final class DescriptorState {
        private MappingStatus status = MappingStatus.RESOLVED;
        private MappingConfidence confidence = MappingConfidence.HIGH;
        private List<String> evidence = List.of();

        void accept(MappingRule rule) {
            if (unresolvedPriority(rule.status()) > unresolvedPriority(status)) {
                status = rule.status();
            }
            confidence = MappingConfidence.weakest(confidence, rule.confidence());
            evidence = MappingRule.combineEvidence(evidence, rule.evidence(), null);
        }

        void addEvidence(String entry) {
            evidence = MappingRule.combineEvidence(evidence, List.of(entry), null);
        }
    }

    public static final class Builder {
        private final Map<LegacySymbol, List<MappingRule>> candidates = new TreeMap<>();

        private Builder() {}

        public Builder add(MappingRule rule) {
            Objects.requireNonNull(rule, "rule");
            candidates.computeIfAbsent(rule.legacy(), ignored -> new ArrayList<>()).add(rule);
            return this;
        }

        public MappingDatabase build() {
            Map<LegacySymbol, MappingRule> resolved = new TreeMap<>();
            candidates.forEach((symbol, choices) -> resolved.put(symbol, merge(symbol, choices)));
            return new MappingDatabase(resolved);
        }

        private static MappingRule merge(LegacySymbol symbol, List<MappingRule> choices) {
            List<MappingRule> blocking = choices.stream()
                    .filter(rule -> rule.status() != MappingStatus.RESOLVED).toList();
            List<MappingRule> effective = blocking.isEmpty() ? choices : blocking;
            var targets = new TreeSet<TargetSymbol>();
            var allTargets = new TreeSet<TargetSymbol>();
            var statuses = new TreeSet<MappingStatus>();
            var categories = new TreeSet<MappingCategory>();
            MappingConfidence confidence = MappingConfidence.VERIFIED;
            List<String> evidence = List.of();
            for (MappingRule rule : choices) {
                evidence = MappingRule.combineEvidence(evidence, rule.evidence(), null);
                if (rule.target() != null) {
                    allTargets.add(rule.target());
                }
            }
            for (MappingRule rule : effective) {
                if (rule.target() != null) {
                    targets.add(rule.target());
                }
                statuses.add(rule.status());
                categories.add(rule.category());
                confidence = MappingConfidence.weakest(confidence, rule.confidence());
            }
            if (allTargets.size() > 1 || statuses.size() > 1) {
                var conflict = new ArrayList<String>();
                for (TargetSymbol target : allTargets) {
                    conflict.add("Conflicting target: " + target.kind() + " " + target.owner()
                            + " " + target.name() + " " + target.descriptor());
                }
                conflict.add("Conflicting mapping candidates; automatic choice is forbidden");
                return unresolved(symbol, MappingRule.combineEvidence(evidence, conflict, null));
            }
            if (!blocking.isEmpty() && blocking.size() != choices.size()) {
                evidence = MappingRule.combineEvidence(evidence, List.of(),
                        "Explicit blocking rule overrides inferred resolved candidate");
            }
            MappingStatus status = statuses.first();
            MappingCategory category = categories.last();
            TargetSymbol target = targets.isEmpty() ? null : targets.first();
            if (status == MappingStatus.MANUAL
                    && effective.stream().anyMatch(rule -> rule.target() == null)) {
                target = null;
                category = MappingCategory.NO_AUTOMATIC_EQUIVALENT;
            }
            return new MappingRule(symbol, target,
                    category, confidence, status, evidence);
        }
    }
}
