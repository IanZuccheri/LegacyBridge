package dev.legacybridge.converter.bytecode;

/** Counts bytecode structures visited without loading the analyzed class. */
public record BytecodeFeatureCounts(
        long descriptorSites,
        long signatureSites,
        long annotationSites,
        long instructions,
        long invokeDynamicInstructions,
        long handles,
        long constantDynamics,
        long nestHosts,
        long nestMembers,
        long recordComponents,
        long moduleDescriptors,
        long moduleDirectives,
        long permittedSubclasses,
        long nativeMethods
) {
    public static final BytecodeFeatureCounts EMPTY = new BytecodeFeatureCounts(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    );

    public BytecodeFeatureCounts {
        if (descriptorSites < 0 || signatureSites < 0 || annotationSites < 0
                || instructions < 0 || invokeDynamicInstructions < 0 || handles < 0
                || constantDynamics < 0 || nestHosts < 0 || nestMembers < 0
                || recordComponents < 0 || moduleDescriptors < 0 || moduleDirectives < 0
                || permittedSubclasses < 0 || nativeMethods < 0) {
            throw new IllegalArgumentException("bytecode feature counts must not be negative");
        }
    }

    public BytecodeFeatureCounts plus(BytecodeFeatureCounts other) {
        return new BytecodeFeatureCounts(
                Math.addExact(descriptorSites, other.descriptorSites),
                Math.addExact(signatureSites, other.signatureSites),
                Math.addExact(annotationSites, other.annotationSites),
                Math.addExact(instructions, other.instructions),
                Math.addExact(invokeDynamicInstructions, other.invokeDynamicInstructions),
                Math.addExact(handles, other.handles),
                Math.addExact(constantDynamics, other.constantDynamics),
                Math.addExact(nestHosts, other.nestHosts),
                Math.addExact(nestMembers, other.nestMembers),
                Math.addExact(recordComponents, other.recordComponents),
                Math.addExact(moduleDescriptors, other.moduleDescriptors),
                Math.addExact(moduleDirectives, other.moduleDirectives),
                Math.addExact(permittedSubclasses, other.permittedSubclasses),
                Math.addExact(nativeMethods, other.nativeMethods)
        );
    }
}
