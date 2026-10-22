package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;

import java.util.List;
import java.util.TreeSet;

/** Bounded package inventory derived from retained definition/reference symbols. */
public final class PackageUsageAnalyzer {
    public static final int PACKAGE_LIMIT = 256;

    public Result analyze(ArchiveReferenceInventory inventory) {
        TreeSet<String> packages = new TreeSet<>();
        boolean truncated = !inventory.definitionIndexComplete() || !inventory.referenceSamplesComplete();
        for (String owner : inventory.definitions().owners()) {
            truncated |= !retain(packages, packageName(owner));
        }
        for (var reference : inventory.references().samples()) {
            truncated |= !retain(packages, packageName(reference.owner()));
        }
        return new Result(List.copyOf(packages), truncated);
    }

    private static boolean retain(TreeSet<String> packages, String packageName) {
        if (packageName.isBlank()) return true;
        packages.add(packageName);
        if (packages.size() <= PACKAGE_LIMIT) return true;
        packages.pollLast();
        return false;
    }

    private static String packageName(String owner) {
        int separator = owner.lastIndexOf('/');
        return separator <= 0 ? "" : owner.substring(0, separator).replace('/', '.');
    }

    public record Result(List<String> packages, boolean truncated) {
        public Result {
            packages = List.copyOf(packages);
        }
    }
}
