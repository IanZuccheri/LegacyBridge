package dev.legacybridge.converter.transformation;

import org.objectweb.asm.ClassWriter;

import java.util.Map;

/**
 * COMPUTE_FRAMES/COMPUTE_MAXS writer whose hierarchy lookup never loads mod classes.
 * Supplied entries may use internal names or internal names followed by {@code .class}.
 * A missing dependency required to merge frames aborts instead of guessing Object.
 */
public final class OfflineClassWriter extends ClassWriter {
    private final ClassHierarchy hierarchy;

    public OfflineClassWriter(int flags, Map<String, byte[]> classes) {
        super(flags);
        hierarchy = new ClassHierarchy(classes);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        return hierarchy.commonSuperClass(type1, type2);
    }
}
