package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests all method reference kinds in the = form.
 */
public class MethodRefCases {

    private List<String> items;

    // Bound instance method reference: expr::method
    public int size() = items::size;
    public String getFirst(int index) = items::get;

    // Unbound instance method reference: Type::instanceMethod
    public boolean isEmpty(String s) = String::isEmpty;
    public int compareTo(String a, String b) = String::compareTo;
    public int length(String s) = String::length;

    // Static method reference: Type::staticMethod
    public int max(int a, int b) = Math::max;
    public int parseInt(String s) = Integer::parseInt;
    public String valueOf(int n) = String::valueOf;

    // Constructor reference: Type::new
    public ArrayList<String> newList() = ArrayList::new;

    // Void method with bound reference
    public void clear() = items::clear;

    // Mixed: -> and = in same class
    public String name() -> "MethodRefCases";
}
