package dev.legacybridge.converter.conversion;

import java.util.Map;

/** A refusal has no artifact. Success is limited to the named declarative subset. */
public record ConversionResult(boolean converted, Map<String, Object> report) { }
