package dev.legacybridge.converter.conversion.extract;

import dev.legacybridge.converter.mapping.source.MappingInputs;

/** Shares inert ASM fixtures with orchestration tests without exposing production testing hooks. */
public final class SyntheticContentFixture {
    private SyntheticContentFixture() { }
    public static byte[] bytecode(String shape) { return StaticContentExtractorTest.fixture(shape); }
    public static MappingInputs mappingInputs(byte[] bytecode) throws Exception { return StaticContentExtractorTest.inputs(bytecode); }
}
