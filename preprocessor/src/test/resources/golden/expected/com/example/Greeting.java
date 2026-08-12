package com.example;

public class Greeting {

    private String name;

    String getName() {
        return this.name;
    }

    String greet(String prefix) {
        return prefix + " " + this.name;
    }

    void print() {
        System.out.println(this.name);
    }
}
