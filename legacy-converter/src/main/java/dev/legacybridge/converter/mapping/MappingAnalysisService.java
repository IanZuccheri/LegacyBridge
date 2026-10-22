package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.bytecode.BytecodeScanException;
import dev.legacybridge.converter.bytecode.BytecodeScanner;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.OperationControl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Full static pass with independent bounded mapping inventory, never the analyzer's 256-item sample. */
public final class MappingAnalysisService {
    public static final int MAX_SYMBOLS = 20_000;
    public static final int MAX_SYMBOL_COMPONENT_CHARS = 4_096;
    public static final int MAX_CLASS_EVIDENCE = 4;

    public MappingReport analyze(Path input, Path profile) throws IOException, InputRejectedException {
        if (profile == null) throw new IllegalArgumentException("Explicit mapping profile required; prepare local data separately");
        return analyze(input, new MappingInputsLoader().load(profile));
    }

    public MappingReport analyze(Path input, MappingInputs inputs) throws InputRejectedException {
        if (inputs.scope() != MappingInputs.Scope.FULL_ENVIRONMENT)
            throw new IllegalArgumentException("Broad mapping inspection requires full-environment inputs, not a finite bootstrap contract");
        Inventory inventory = new Inventory();
        var scan = new SafeJarReader().scan(input, inventory);
        inventory.references.entrySet().removeIf(entry -> inventory.definitions.contains(entry.getKey().owner()));
        var database = new MappingDatabaseGenerator(inputs).generate(inventory.references.keySet());
        List<MappingReport.Entry> entries = new ArrayList<>();
        for (var entry : inventory.references.entrySet()) {
            OperationControl.checkpoint();
            LegacySymbol symbol = entry.getKey();
            MappingRule rule = symbol.kind() == SymbolKind.DESCRIPTOR
                    ? database.resolveDescriptor(symbol.descriptor()) : database.resolve(symbol);
            Usage usage = entry.getValue();
            entries.add(new MappingReport.Entry(rule, usage.count, List.copyOf(usage.classes), usage.truncated));
        }
        List<String> diagnostics = new ArrayList<>(inputs.diagnostics());
        if (inventory.unreadable > 0) diagnostics.add("Unreadable classes: reference and internal-owner inventory is incomplete.");
        if (inventory.truncated) diagnostics.add("Mapping inventory limit reached; omitted symbols were not resolved or approved.");
        return new MappingReport(scan.input(), inputs.complete(), inputs.sources(), diagnostics,
                inventory.readable, inventory.unreadable, inventory.observed, inventory.truncated, entries);
    }

    private static final class Inventory implements JarEntryVisitor {
        private final BytecodeScanner scanner = new BytecodeScanner();
        private final TreeMap<LegacySymbol, Usage> references = new TreeMap<>();
        private final Set<String> definitions = new HashSet<>();
        private int readable;
        private int unreadable;
        private long observed;
        private boolean truncated;

        @Override public boolean requiresBytes(String name) { return name.endsWith(".class"); }

        @Override public void visit(String name, byte[] contents) {
            TreeMap<LegacySymbol, Usage> staged = new TreeMap<>();
            long[] count = {0};
            boolean[] omitted = {false};
            try {
                var result = scanner.scan(name, contents, reference -> {
                    OperationControl.checkpoint();
                    if (isJdk(reference.owner())) return;
                    count[0]++;
                    if (reference.owner().length() > MAX_SYMBOL_COMPONENT_CHARS
                            || reference.memberName().length() > MAX_SYMBOL_COMPONENT_CHARS
                            || reference.descriptor().length() > MAX_SYMBOL_COMPONENT_CHARS) {
                        omitted[0] = true;
                        return;
                    }
                    LegacySymbol symbol = from(reference);
                    if (symbol == null) { omitted[0] = true; return; }
                    retain(staged, symbol, name, omitted);
                    if (!reference.descriptor().isEmpty()) {
                        try {
                            // TYPE observations can originate in declarations, handles and invokedynamic.
                            // Generic signatures are not JVM descriptors and must not be guessed into one.
                            retain(staged, new LegacySymbol(SymbolKind.DESCRIPTOR, "", "", reference.descriptor()), name, omitted);
                        } catch (IllegalArgumentException signatureOrMalformedDescriptor) {
                            // Its individual type owners are still emitted and mapped by ASM.
                        }
                    }
                });
                definitions.add(result.internalName());
                readable++;
                observed += count[0];
                truncated |= omitted[0];
                for (var entry : staged.entrySet()) {
                    OperationControl.checkpoint();
                    if (!references.containsKey(entry.getKey()) && references.size() >= MAX_SYMBOLS) { truncated = true; continue; }
                    references.computeIfAbsent(entry.getKey(), ignored -> new Usage()).merge(entry.getValue());
                }
            } catch (BytecodeScanException exception) {
                unreadable++;
            }
        }

        private static void retain(Map<LegacySymbol, Usage> target, LegacySymbol symbol, String name, boolean[] omitted) {
            if (!target.containsKey(symbol) && target.size() >= MAX_SYMBOLS) { omitted[0] = true; return; }
            Usage usage = target.computeIfAbsent(symbol, ignored -> new Usage());
            usage.count++;
            usage.addClass(name);
        }
    }

    private static LegacySymbol from(BytecodeReference reference) {
        try {
            if (reference.kind() == BytecodeReferenceKind.METHOD) {
                if ("<clinit>".equals(reference.memberName())) return null;
                return new LegacySymbol("<init>".equals(reference.memberName()) ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD,
                        reference.owner(), reference.memberName(), reference.descriptor());
            }
            if (reference.kind() == BytecodeReferenceKind.FIELD) return new LegacySymbol(SymbolKind.FIELD, reference.owner(), reference.memberName(), reference.descriptor());
            return new LegacySymbol(SymbolKind.CLASS, reference.owner(), "", "");
        } catch (IllegalArgumentException exception) { return null; }
    }

    private static boolean isJdk(String owner) {
        return owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("jdk/") || owner.startsWith("sun/");
    }

    private static final class Usage {
        private long count;
        private final TreeSet<String> classes = new TreeSet<>();
        private boolean truncated;
        private void addClass(String name) {
            classes.add(name);
            if (classes.size() > MAX_CLASS_EVIDENCE) { classes.pollLast(); truncated = true; }
        }
        private void merge(Usage other) {
            count += other.count;
            other.classes.forEach(this::addClass);
            truncated |= other.truncated;
        }
    }
}
