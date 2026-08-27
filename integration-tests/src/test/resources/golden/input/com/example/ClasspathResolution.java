package com.example;

import com.external.ExternalUtil;

/**
 * Tests method reference resolution against types from a JAR on the classpath.
 * The type 'ExternalUtil' is NOT in the source tree — it's only available as
 * a compiled class in external-util.jar. The preprocessor must resolve it via
 * JarTypeSolver to determine static vs instance.
 */
public class ClasspathResolution {

    private ExternalUtil util;

    // Static method reference: ExternalUtil::add
    // Resolved from JAR → static method → all params are args
    public int add(int a, int b) = ExternalUtil::add;

    // Static method reference: ExternalUtil::wrap
    // Resolved from JAR → static method → all params are args
    public String wrap(String s) = ExternalUtil::wrap;

    // Unbound instance method reference: ExternalUtil::describe
    // Resolved from JAR → instance method → first param is receiver
    public String describe(ExternalUtil e) = ExternalUtil::describe;

    // Unbound instance method reference: ExternalUtil::matches
    // Resolved from JAR → instance method → first param is receiver, rest are args
    public boolean matches(ExternalUtil e, String pattern) = ExternalUtil::matches;

    // Bound instance reference to a field of JAR type
    public String describeUtil() = util::describe;

    // Constructor reference for JAR type
    public ExternalUtil create() = ExternalUtil::new;
}
