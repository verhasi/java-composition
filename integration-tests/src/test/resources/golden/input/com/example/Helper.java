package com.example;

/**
 * Helper class used to test source-tree type resolution.
 * The preprocessor must resolve this class from the source root
 * (not from the classpath or reflection) to determine whether
 * methods are static or instance.
 */
public class Helper {

    private String value;

    public Helper(String value) {
        this.value = value;
    }

    /** Instance method — used to test unbound instance reference resolution */
    public String getValue() {
        return value;
    }

    /** Instance method with parameter — used to test unbound instance reference resolution */
    public boolean startsWith(String prefix) {
        return value.startsWith(prefix);
    }

    /** Static method — used to test static reference resolution */
    public static Helper of(String value) {
        return new Helper(value);
    }

    /** Static method with two params — used to test static reference resolution */
    public static String combine(String a, String b) {
        return a + b;
    }
}
