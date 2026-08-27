package com.example;

public class Greeter {

    private final String name;

    public Greeter(String name) {
        this.name = name;
    }

    public String getName() -> this.name;

    public String greet(String prefix) -> prefix + " " + this.name;
}
