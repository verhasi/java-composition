/*
 * Copyright (C) 2013-2026 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link ProblemResolver} SPI: the parser iterates recorded problems, asks
 * registered resolvers, and removes a problem when a resolver reports it resolved. The
 * resolver never mutates the problem list.
 */
class ProblemResolverTest {

    // A body the standard grammar cannot parse, so recovery records a Problem.
    private static final String BROKEN = "class Example { int broken() { @ @ @ } }";

    @Test
    void withoutResolversTheProblemRemainsAndParseIsUnsuccessful() {
        ParseResult<CompilationUnit> result =
                new JavaParser(new ParserConfiguration()).parse(BROKEN);

        assertFalse(result.isSuccessful());
        assertFalse(result.getProblems().isEmpty());
    }

    @Test
    void aResolverThatResolvesDrainsTheProblemAndParseBecomesSuccessful() {
        ParserConfiguration config = new ParserConfiguration();
        // Resolver that claims every problem as resolved.
        config.getProblemResolvers().add(() -> (result, cfg, problem) -> true);

        ParseResult<CompilationUnit> result = new JavaParser(config).parse(BROKEN);

        assertTrue(result.getProblems().isEmpty(), "resolved problem should be removed by the parser");
        assertTrue(result.isSuccessful(), "isSuccessful() should be true once problems are drained");
    }

    @Test
    void aResolverThatDeclinesLeavesTheProblemInPlace() {
        ParserConfiguration config = new ParserConfiguration();
        // Resolver that resolves nothing.
        config.getProblemResolvers().add(() -> (result, cfg, problem) -> false);

        ParseResult<CompilationUnit> result = new JavaParser(config).parse(BROKEN);

        assertFalse(result.getProblems().isEmpty(), "declined problem must remain");
        assertFalse(result.isSuccessful());
    }

    @Test
    void resolverIsAskedOncePerProblemAndParserOwnsRemoval() {
        ParserConfiguration config = new ParserConfiguration();
        int[] asked = {0};
        config.getProblemResolvers().add(() -> (result, cfg, problem) -> {
            asked[0]++;
            // Deliberately do NOT touch result.getProblems() — parser owns removal.
            return true;
        });

        ParseResult<CompilationUnit> result = new JavaParser(config).parse(BROKEN);

        assertEquals(1, asked[0], "resolver should be asked exactly once for the single problem");
        assertTrue(result.getProblems().isEmpty(), "parser should have removed the resolved problem");
    }
}
