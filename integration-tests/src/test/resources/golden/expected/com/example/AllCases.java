package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Comprehensive test of concise method bodies (-> form).
 * Covers: getters, parameters, void, annotations, generics, throws,
 * method references as return values, and mixed methods.
 */
public class AllCases {

    private String name;

    private InputStream input;

    private java.io.Closeable stream;

    // Simple getter
    String getName() {
        return this.name;
    }

    // Expression with parameters
    int add(int a, int b) {
        return a + b;
    }

    // Void method with expression statement
    void close() throws IOException {
        stream.close();
    }

    // Method with annotation
    @Override
    public String toString() {
        return "AllCases[" + name + "]";
    }

    // Method with generics
    <T> T first(List<T> list) {
        return list.get(0);
    }

    // Method with throws
    int read() throws IOException {
        return input.read();
    }

    // Method returning functional interface via method reference
    Supplier<String> supplier() {
        return this::toString;
    }

    // Method returning Predicate via method reference
    Predicate<String> nonEmpty() {
        return s -> !s.isEmpty();
    }

    // Standard method (should not be affected)
    public String greetFull(String prefix) {
        String greeting = prefix + " " + this.name;
        return greeting.trim();
    }

    // Another standard method (abstract-like but in class, won't compile but tests parsing)
    // Actually let's use a regular method with multiple statements
    public int compute(int x) {
        int doubled = x * 2;
        return doubled + 1;
    }

    // Ternary expression
    int abs(int x) {
        return x >= 0 ? x : -x;
    }

    // Constructor call expression
    Object newList() {
        return new java.util.ArrayList<>();
    }

    // Static method with concise body
    static int max(int a, int b) {
        return Math.max(a, b);
    }

    // Final method with concise body
    final String prefix() {
        return name.substring(0, 1);
    }
}
