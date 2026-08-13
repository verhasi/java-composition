package com.example;

/**
 * Tests method reference resolution against types in the same source tree.
 * The type 'Helper' is NOT on the classpath — it's only available as a source
 * file in the same source root. The preprocessor must resolve it via
 * JavaParserTypeSolver to determine static vs instance.
 */
public class SourceTreeResolution {

    private Helper helper;

    // Unbound instance method reference: Helper::getValue
    // Resolved from source tree → instance method → first param is receiver
    public String getValue(Helper h) {
        return h.getValue();
    }

    // Unbound instance method with extra param: Helper::startsWith
    // Resolved from source tree → instance method → first param is receiver, rest are args
    public boolean startsWith(Helper h, String prefix) {
        return h.startsWith(prefix);
    }

    // Static method reference: Helper::of
    // Resolved from source tree → static method → all params are args
    public Helper of(String value) {
        return Helper.of(value);
    }

    // Static method reference: Helper::combine
    // Resolved from source tree → static method → all params are args
    public String combine(String a, String b) {
        return Helper.combine(a, b);
    }

    // Bound instance reference to a field of source-tree type
    public String getHelperValue() {
        return helper.getValue();
    }

    // Constructor reference for source-tree type
    public Helper create(String value) {
        return new Helper(value);
    }
}
