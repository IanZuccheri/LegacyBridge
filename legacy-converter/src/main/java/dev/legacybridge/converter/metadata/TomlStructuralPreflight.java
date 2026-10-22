package dev.legacybridge.converter.metadata;

/** Cheap non-semantic guard against recursively nested TOML input. */
final class TomlStructuralPreflight {
    static final int MAX_NESTING_DEPTH = 64;
    static final int MAX_STRUCTURAL_TOKENS = 100_000;

    private TomlStructuralPreflight() {
    }

    static void validate(String source) {
        int depth = 0;
        int structuralTokens = 0;
        State state = State.PLAIN;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (state == State.COMMENT) {
                if (current == '\n' || current == '\r') state = State.PLAIN;
                continue;
            }
            if (state == State.BASIC || state == State.MULTILINE_BASIC) {
                if (current == '\\') {
                    index++;
                    continue;
                }
                if (current == '"') {
                    if (state == State.MULTILINE_BASIC && hasTripleQuote(source, index, '"')) {
                        state = State.PLAIN;
                        index += 2;
                    } else if (state == State.BASIC) {
                        state = State.PLAIN;
                    }
                }
                continue;
            }
            if (state == State.LITERAL || state == State.MULTILINE_LITERAL) {
                if (current == '\'') {
                    if (state == State.MULTILINE_LITERAL && hasTripleQuote(source, index, '\'')) {
                        state = State.PLAIN;
                        index += 2;
                    } else if (state == State.LITERAL) {
                        state = State.PLAIN;
                    }
                }
                continue;
            }

            if (current == '#') {
                state = State.COMMENT;
            } else if (current == '"') {
                if (hasTripleQuote(source, index, '"')) {
                    state = State.MULTILINE_BASIC;
                    index += 2;
                } else {
                    state = State.BASIC;
                }
            } else if (current == '\'') {
                if (hasTripleQuote(source, index, '\'')) {
                    state = State.MULTILINE_LITERAL;
                    index += 2;
                } else {
                    state = State.LITERAL;
                }
            } else if (current == '[' || current == '{') {
                depth++;
                structuralTokens++;
                if (depth > MAX_NESTING_DEPTH) {
                    throw new IllegalArgumentException("TOML nesting exceeds policy");
                }
            } else if (current == ']' || current == '}') {
                depth = Math.max(0, depth - 1);
                structuralTokens++;
            } else if (current == '=' || current == ',') {
                structuralTokens++;
            }
            if (structuralTokens > MAX_STRUCTURAL_TOKENS) {
                throw new IllegalArgumentException("TOML structural token count exceeds policy");
            }
        }
    }

    private static boolean hasTripleQuote(String source, int offset, char quote) {
        return offset + 2 < source.length()
                && source.charAt(offset + 1) == quote
                && source.charAt(offset + 2) == quote;
    }

    private enum State {
        PLAIN,
        COMMENT,
        BASIC,
        LITERAL,
        MULTILINE_BASIC,
        MULTILINE_LITERAL
    }
}
