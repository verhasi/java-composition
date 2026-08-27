package com.example;

import java.util.List;

public class Demo {

    private final String name;
    private final List<String> items;

    public Demo(String name, List<String> items) {
        this.name = name;
        this.items = items;
    }

    // Expression form (->)
    public String getName() -> this.name;

    public int itemCount() -> items.size();

    public String greet(String prefix) -> prefix + " " + this.name;

    public void printName() -> System.out.println(this.name);

    // Method reference form (=)
    public static int max(int a, int b) = Math::max;

    // Standard method (should pass through unchanged)
    public String toString() {
        return "Demo{name=" + name + ", items=" + items + "}";
    }
}
