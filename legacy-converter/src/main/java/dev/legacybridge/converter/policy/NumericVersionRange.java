package dev.legacybridge.converter.policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Conservative parser for the numeric subset of Maven version ranges used by
 * the source-profile heuristic. Unsupported syntax never produces a match.
 */
public final class NumericVersionRange {
    private NumericVersionRange() {
    }

    public static boolean contains(String expression, String targetVersion) {
        List<BigInteger> target = parseVersion(targetVersion);
        if (target == null || !isSupported(expression)) {
            return false;
        }

        String value = expression.trim();
        if (value.matches("[0-9]+(?:\\.[0-9]+)*")) {
            List<BigInteger> exact = parseVersion(value);
            return exact != null && compare(target, exact) == 0;
        }
        if (value.length() < 3) {
            return false;
        }

        char opening = value.charAt(0);
        char closing = value.charAt(value.length() - 1);
        if ((opening != '[' && opening != '(') || (closing != ']' && closing != ')')) {
            return false;
        }

        String body = value.substring(1, value.length() - 1).trim();
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (opening != '[' || closing != ']' || body.isEmpty()) {
                return false;
            }
            List<BigInteger> exact = parseVersion(body);
            return exact != null && compare(target, exact) == 0;
        }
        if (comma != body.lastIndexOf(',')) {
            return false;
        }

        String lowerText = body.substring(0, comma).trim();
        String upperText = body.substring(comma + 1).trim();
        if (lowerText.isEmpty() && upperText.isEmpty()) {
            return false;
        }

        if (!lowerText.isEmpty()) {
            List<BigInteger> lower = parseVersion(lowerText);
            if (lower == null) {
                return false;
            }
            int comparison = compare(target, lower);
            if (comparison < 0 || (comparison == 0 && opening == '(')) {
                return false;
            }
        }
        if (!upperText.isEmpty()) {
            List<BigInteger> upper = parseVersion(upperText);
            if (upper == null) {
                return false;
            }
            int comparison = compare(target, upper);
            if (comparison > 0 || (comparison == 0 && closing == ')')) {
                return false;
            }
        }
        return true;
    }

    /** Returns whether the supported numeric range intersects [major, major+1). */
    public static boolean intersectsMajor(String expression, int major) {
        if (major < 0 || !isSupported(expression)) {
            return false;
        }
        String value = expression.trim();
        List<BigInteger> majorFloor = List.of(BigInteger.valueOf(major));
        List<BigInteger> nextMajor = List.of(BigInteger.valueOf((long) major + 1));
        if (value.matches("[0-9]+(?:\\.[0-9]+)*")) {
            List<BigInteger> exact = parseVersion(value);
            return exact != null && compare(exact, majorFloor) >= 0
                    && compare(exact, nextMajor) < 0;
        }

        char closing = value.charAt(value.length() - 1);
        String body = value.substring(1, value.length() - 1).trim();
        int comma = body.indexOf(',');
        if (comma < 0) {
            List<BigInteger> exact = parseVersion(body);
            return exact != null && compare(exact, majorFloor) >= 0
                    && compare(exact, nextMajor) < 0;
        }
        String lowerText = body.substring(0, comma).trim();
        String upperText = body.substring(comma + 1).trim();
        if (!lowerText.isEmpty()) {
            List<BigInteger> lower = parseVersion(lowerText);
            if (lower == null || compare(lower, nextMajor) >= 0) {
                return false;
            }
        }
        if (!upperText.isEmpty()) {
            List<BigInteger> upper = parseVersion(upperText);
            if (upper == null) return false;
            int comparison = compare(upper, majorFloor);
            if (comparison < 0 || comparison == 0 && closing == ')') {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether the expression belongs to the deliberately small,
     * numeric-only range grammar used by the initial analyzer. Maven unions,
     * qualifiers and substitutions are intentionally unsupported.
     */
    public static boolean isSupported(String expression) {
        if (expression == null) {
            return false;
        }
        String value = expression.trim();
        if (value.matches("[0-9]+(?:\\.[0-9]+)*")) {
            return true;
        }
        if (value.length() < 3) {
            return false;
        }

        char opening = value.charAt(0);
        char closing = value.charAt(value.length() - 1);
        if ((opening != '[' && opening != '(') || (closing != ']' && closing != ')')) {
            return false;
        }

        String body = value.substring(1, value.length() - 1).trim();
        int comma = body.indexOf(',');
        if (comma < 0) {
            return opening == '[' && closing == ']' && parseVersion(body) != null;
        }
        if (comma != body.lastIndexOf(',')) {
            return false;
        }

        String lowerText = body.substring(0, comma).trim();
        String upperText = body.substring(comma + 1).trim();
        if (lowerText.isEmpty() && upperText.isEmpty()) {
            return false;
        }
        List<BigInteger> lower = lowerText.isEmpty() ? null : parseVersion(lowerText);
        List<BigInteger> upper = upperText.isEmpty() ? null : parseVersion(upperText);
        if ((!lowerText.isEmpty() && lower == null) || (!upperText.isEmpty() && upper == null)) {
            return false;
        }
        if (lower != null && upper != null) {
            int bounds = compare(lower, upper);
            if (bounds > 0 || (bounds == 0 && (opening == '(' || closing == ')'))) {
                return false;
            }
        }
        return true;
    }

    private static List<BigInteger> parseVersion(String value) {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]+)*")) {
            return null;
        }
        List<BigInteger> parts = new ArrayList<>();
        for (String part : value.split("\\.")) {
            parts.add(new BigInteger(part));
        }
        return List.copyOf(parts);
    }

    private static int compare(List<BigInteger> left, List<BigInteger> right) {
        int size = Math.max(left.size(), right.size());
        for (int index = 0; index < size; index++) {
            BigInteger leftPart = index < left.size() ? left.get(index) : BigInteger.ZERO;
            BigInteger rightPart = index < right.size() ? right.get(index) : BigInteger.ZERO;
            int comparison = leftPart.compareTo(rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
