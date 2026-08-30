/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2026 The JavaParser Team.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.UnparsedBlockStatement;
import org.junit.jupiter.api.Test;

/**
 * Task 3 verification: block-body error recovery retains skipped tokens as an
 * {@link UnparsedBlockStatement} when {@code retainUnparsedTokens} is enabled, and
 * behaves identically to before (empty unparsable {@link BlockStmt}) when disabled.
 *
 * <p>The body {@code { @ @ @ }} is fully tokenizable but ungrammatical — a general
 * ungrammatical body, not concise syntax.
 */
class BlockBodyRecoveryRetentionTest {

    private static final String UNGRAMMATICAL_BODY = "class Example { int broken() { @ @ @ } }";

    @Test
    void retentionOn_bodyIsUnparsedBlockStatementWithTokens() {
        ParserConfiguration config = new ParserConfiguration().setRetainUnparsedTokens(true);
        ParseResult<CompilationUnit> result = new JavaParser(config).parse(UNGRAMMATICAL_BODY);

        assertFalse(result.isSuccessful(), "ungrammatical body must be an unsuccessful parse");
        CompilationUnit cu = result.getResult().get();

        MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);
        assertTrue(method.getBody().isPresent(), "a body node is present");

        BlockStmt body = method.getBody().get();
        assertTrue(body instanceof UnparsedBlockStatement,
                "with retention on, the body is an UnparsedBlockStatement");

        // The retained node covers the skipped tokens (its token range is non-empty).
        assertTrue(body.getTokenRange().isPresent(), "the unparsed block retains its token range");
    }

    @Test
    void retentionOff_bodyIsEmptyBlockStmt_identicalToHistorical() {
        ParserConfiguration config = new ParserConfiguration().setRetainUnparsedTokens(false);
        ParseResult<CompilationUnit> result = new JavaParser(config).parse(UNGRAMMATICAL_BODY);

        assertFalse(result.isSuccessful());
        CompilationUnit cu = result.getResult().get();

        MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);
        BlockStmt body = method.getBody().get();

        assertFalse(body instanceof UnparsedBlockStatement,
                "with retention off, the body is a plain empty BlockStmt (historical behavior)");
        assertTrue(body.getStatements().isEmpty(), "the historical recovery body is empty");
    }

    @Test
    void retentionDefaultsOff() {
        // Default configuration must preserve historical behavior.
        ParseResult<CompilationUnit> result = new JavaParser().parse(UNGRAMMATICAL_BODY);
        CompilationUnit cu = result.getResult().get();
        BlockStmt body = cu.findAll(MethodDeclaration.class).get(0).getBody().get();
        assertFalse(body instanceof UnparsedBlockStatement,
                "retention must default to off");
    }

    @Test
    void validCodeUnaffectedByRetention() {
        // Turning retention on must not change parsing of valid code.
        ParserConfiguration config = new ParserConfiguration().setRetainUnparsedTokens(true);
        ParseResult<CompilationUnit> result =
                new JavaParser(config).parse("class Example { int ok() { return 1; } }");
        assertTrue(result.isSuccessful());
        BlockStmt body = result.getResult().get()
                .findAll(MethodDeclaration.class).get(0).getBody().get();
        assertFalse(body instanceof UnparsedBlockStatement);
        assertFalse(body.getStatements().isEmpty());
    }
}
