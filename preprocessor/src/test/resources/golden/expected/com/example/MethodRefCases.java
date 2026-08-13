package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests all method reference kinds in the = form.
 */
public class MethodRefCases {

    private List<String> items;

    // Bound instance method reference: expr::method
    public int size() {
        return items.size();
    }

    public String getFirst(int index) {
        return items.get(index);
    }

    // Unbound instance method reference: Type::instanceMethod
    public boolean isEmpty(String s) {
        return s.isEmpty();
    }

    public int compareTo(String a, String b) {
        return a.compareTo(b);
    }

    public int length(String s) {
        return s.length();
    }

    // Static method reference: Type::staticMethod
    public int max(int a, int b) {
        return Math.max(a, b);
    }

    public int parseInt(String s) {
        return Integer.parseInt(s);
    }

    public String valueOf(int n) {
        return String.valueOf(n);
    }

    // Constructor reference: Type::new
    public ArrayList<String> newList() {
        return new ArrayList();
    }

    // Void method with bound reference
    public void clear() {
        items.clear();
    }

    // Mixed: -> and = in same class
    public String name() {
        return "MethodRefCases";
    }
}
