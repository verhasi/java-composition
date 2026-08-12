package com.example;

public class Greeting {
    private String name;

    String getName() -> this.name;

    String greet(String prefix) -> prefix + " " + this.name;

    void print() -> System.out.println(this.name);
}
