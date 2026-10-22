package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.MappingCategory;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.mapping.MappingDatabase;
import dev.legacybridge.converter.mapping.MappingRule;
import dev.legacybridge.converter.mapping.MappingStatus;
import dev.legacybridge.converter.mapping.SymbolKind;
import dev.legacybridge.runtime.compat.AdapterCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exact, verified rewrite policy. A mapping candidate is not permission to transform bytecode.
 * Unlisted references pass through; explicitly requested unresolved rules fail closed. Callers must
 * separately establish that all retained APIs are supported by the selected conversion profile.
 * A catalog binding supplies traceability, not proof that an arbitrary API has equivalent behavior.
 */
public final class MappingResolver {
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "net/minecraft/", "net/minecraftforge/", "cpw/mods/", "com/mojang/", "java/", "javax/",
            "jdk/", "sun/", "dev/legacybridge/runtime/");

    private final Map<LegacySymbol, MappingRule> explicitRules;
    private final Map<LegacySymbol, String> adapterBindings;
    private final Set<String> modClasses;

    public MappingResolver(MappingDatabase database, Map<LegacySymbol, String> adapterBindings,
                           Set<String> modClasses) {
        Objects.requireNonNull(database, "database");
        this.adapterBindings = Map.copyOf(adapterBindings);
        this.modClasses = Set.copyOf(modClasses);
        var rules = new LinkedHashMap<LegacySymbol, MappingRule>();
        database.rules().forEach(rule -> rules.put(rule.legacy(), rule));
        explicitRules = Collections.unmodifiableMap(rules);
        this.modClasses.forEach(MappingResolver::validateInternalName);
        this.adapterBindings.forEach((symbol, adapter) -> {
            if (!explicitRules.containsKey(symbol)) {
                throw failure("Adapter binding has no exact mapping rule: " + readable(symbol));
            }
            requireExistingAdapter(adapter);
        });
        rejectChainedAndCollidingClassRules();
    }

    public static boolean isProtectedClass(String internalName) {
        return PROTECTED_PREFIXES.stream().anyMatch(internalName::startsWith);
    }

    public void requireTransformableClass(String internalName) {
        validateInternalName(internalName);
        if (isProtectedClass(internalName) || !modClasses.contains(internalName)) {
            throw failure("Only classes owned by the converted mod may be transformed: " + internalName);
        }
        String target = mapClassName(internalName);
        if (isProtectedClass(target)) {
            throw failure("A mod class declaration cannot target a Minecraft/Forge/platform namespace: " + target);
        }
        if (!target.equals(internalName) && modClasses.contains(target)) {
            throw failure("Class mapping collides with another mod class declaration: " + target);
        }
    }

    /** Later stages may receive the already-remapped name, but never a foreign/platform declaration. */
    public void requireOwnedDeclaration(String internalName) {
        validateInternalName(internalName);
        boolean owned = isOwnedClassName(internalName);
        if (isProtectedClass(internalName) || !owned) {
            throw failure("Only original or verified-remapped mod declarations may enter a later stage: " + internalName);
        }
    }

    /** Does not synthesize descriptor candidates with the database's inferred HIGH confidence. */
    public Optional<MappingRule> findRule(LegacySymbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (!explicitRules.containsKey(symbol)) {
            return Optional.empty();
        }
        return Optional.of(requireResolved(symbol));
    }

    public MappingRule requireResolved(LegacySymbol symbol) {
        MappingRule rule = explicitRules.get(symbol);
        if (rule == null || rule.status() != MappingStatus.RESOLVED
                || rule.confidence() != MappingConfidence.VERIFIED || rule.target() == null) {
            throw failure("Exact VERIFIED and RESOLVED mapping required: " + readable(symbol));
        }
        if (rule.category() != MappingCategory.IDENTITY && rule.category() != MappingCategory.PURE_RENAME
                && rule.category() != MappingCategory.PACKAGE_MOVE
                && rule.category() != MappingCategory.DESCRIPTOR_CHANGE) {
            throw failure("Mapping category has no structural transformation recipe: " + readable(symbol));
        }
        adapterId(rule);
        return rule;
    }

    public String adapterId(MappingRule rule) {
        String id = adapterBindings.get(rule.legacy());
        if (id == null) {
            throw failure("Mapping has no implemented adapter binding: " + readable(rule.legacy()));
        }
        requireExistingAdapter(id);
        return id;
    }

    public String mapClassName(String internalName) {
        validateInternalName(internalName);
        return findRule(LegacySymbol.classSymbol(internalName))
                .map(rule -> rule.target().owner()).orElse(internalName);
    }

    public Optional<MappingRule> memberRule(String owner, String name, String descriptor, boolean method) {
        if (name.equals("<clinit>")) {
            return Optional.empty();
        }
        LegacySymbol symbol = method
                ? name.equals("<init>") ? LegacySymbol.constructor(owner, descriptor)
                : LegacySymbol.method(owner, name, descriptor)
                : LegacySymbol.field(owner, name, descriptor);
        Optional<MappingRule> selected = findRule(symbol);
        String structuralOwner = mapClassName(owner);
        String structuralDescriptor = new DescriptorTransformer(this).remap(descriptor).after();
        if (selected.isEmpty() && !isOwnedClassName(owner)
                && (!owner.equals(structuralOwner) || !descriptor.equals(structuralDescriptor))) {
            throw failure("Unresolved external member: class/descriptor mappings do not verify invocation semantics: "
                    + readable(symbol));
        }
        if (selected.isPresent()) {
            MappingRule rule = selected.get();
            if (!rule.target().owner().equals(structuralOwner)) {
                throw failure("Member-owner relocation requires a concrete invocation recipe: " + readable(symbol));
            }
            if (!rule.target().descriptor().equals(structuralDescriptor)) {
                boolean lifecycleRecipe = AdapterCatalog.LIFECYCLE.equals(adapterId(rule))
                        && rule.target().descriptor().equals(dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter
                        .methodDescriptor(owner, name, descriptor, structuralDescriptor));
                boolean configIdentityRecipe = AdapterCatalog.CONFIG_SPEC_IDENTITY.equals(adapterId(rule))
                        && rule.target().descriptor().equals(dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter
                        .methodDescriptor(owner, name, descriptor, structuralDescriptor));
                boolean plainBlockRecipe = AdapterCatalog.PLAIN_BLOCK.equals(adapterId(rule))
                        && rule.target().descriptor().equals(dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter
                        .registryDescriptor(owner, name, descriptor, structuralDescriptor));
                if (!method || !(lifecycleRecipe || configIdentityRecipe || plainBlockRecipe))
                    throw failure("Argument/return/field ABI change requires a concrete adapter recipe: " + readable(symbol));
            }
            if (symbol.kind() == SymbolKind.CONSTRUCTOR && !rule.target().name().equals("<init>")) {
                throw failure("Constructors must preserve <init>: " + readable(symbol));
            }
        }
        return selected;
    }

    /** Context-sensitive descriptor changes must not be applied to every occurrence of a type. */
    String callDescriptor(String owner, String name, String descriptor) {
        return memberRule(owner, name, descriptor, true).map(rule -> rule.target().descriptor())
                .orElseGet(() -> new DescriptorTransformer(this).remap(descriptor).after());
    }

    /** Annotation attributes have no descriptor in their use site; only an unambiguous zero-arg rule is safe. */
    Optional<MappingRule> annotationAttributeRule(String owner, String name) {
        var matches = explicitRules.keySet().stream().filter(symbol -> symbol.kind() == SymbolKind.METHOD
                && symbol.owner().equals(owner) && symbol.name().equals(name)).toList();
        if (matches.isEmpty()) {
            if (!isOwnedClassName(owner) && !owner.equals(mapClassName(owner))) {
                throw failure("Unresolved external annotation attribute after class relocation: " + owner + "." + name);
            }
            return Optional.empty();
        }
        if (matches.size() != 1 || !matches.get(0).descriptor().startsWith("()")) {
            throw failure("Annotation attribute mapping is ambiguous: " + owner + "." + name);
        }
        LegacySymbol symbol = matches.get(0);
        return memberRule(owner, name, symbol.descriptor(), true);
    }

    /** An invokedynamic name is not an ordinary method name; changing its SAM requires a bootstrap recipe. */
    void requireUnchangedInvokeDynamicName(String name, String descriptor) {
        org.objectweb.asm.Type result = org.objectweb.asm.Type.getReturnType(descriptor);
        if (result.getSort() != org.objectweb.asm.Type.OBJECT) {
            return;
        }
        if (!result.getInternalName().equals(mapClassName(result.getInternalName()))) {
            throw failure("Invokedynamic interface relocation requires a concrete bootstrap/SAM recipe: " + descriptor);
        }
        for (LegacySymbol symbol : explicitRules.keySet()) {
            if (symbol.kind() == SymbolKind.METHOD && symbol.owner().equals(result.getInternalName())
                    && symbol.name().equals(name)) {
                MappingRule rule = requireResolved(symbol);
                if (!rule.target().name().equals(name)) {
                    throw failure("Invokedynamic SAM rename requires a concrete bootstrap recipe: " + readable(symbol));
                }
            }
        }
    }

    private void rejectChainedAndCollidingClassRules() {
        var targets = new LinkedHashMap<String, String>();
        for (MappingRule rule : explicitRules.values()) {
            if (rule.legacy().kind() != SymbolKind.CLASS || rule.target() == null
                    || rule.legacy().owner().equals(rule.target().owner())) {
                continue;
            }
            String before = rule.legacy().owner();
            String after = rule.target().owner();
            MappingRule next = explicitRules.get(LegacySymbol.classSymbol(after));
            if (next != null && next.target() != null && !after.equals(next.target().owner())) {
                throw failure("Chained class rules are forbidden across transformation stages: " + before + " -> " + after);
            }
            String previous = targets.putIfAbsent(after, before);
            if (previous != null && !previous.equals(before)) {
                throw failure("Class mapping collision: " + previous + " and " + before + " -> " + after);
            }
        }
    }

    private boolean isOwnedClassName(String name) {
        if (modClasses.contains(name)) {
            return true;
        }
        return explicitRules.values().stream().anyMatch(rule -> rule.legacy().kind() == SymbolKind.CLASS
                && modClasses.contains(rule.legacy().owner()) && rule.target() != null
                && rule.target().owner().equals(name) && requireResolved(rule.legacy()).target().owner().equals(name));
    }

    static String readable(LegacySymbol symbol) {
        return symbol.kind() + " " + symbol.owner() + (symbol.name().isEmpty() ? "" : "." + symbol.name())
                + symbol.descriptor();
    }

    private static void validateInternalName(String name) {
        LegacySymbol.classSymbol(name);
    }

    private static void requireExistingAdapter(String id) {
        if (AdapterCatalog.find(id).isEmpty()) {
            throw failure("Unknown or unimplemented adapter: " + id);
        }
    }

    private static TransformationException failure(String message) {
        return new TransformationException(message);
    }
}
