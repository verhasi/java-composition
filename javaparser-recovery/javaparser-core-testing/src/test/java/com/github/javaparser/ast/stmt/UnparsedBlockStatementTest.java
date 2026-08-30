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
package com.github.javaparser.ast.stmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

/**
 * Task 2 verification: the {@link UnparsedBlockStatement} node is wired correctly.
 *
 * <p>It extends {@link BlockStmt} so it fits a method body slot, it is a first-class
 * node (findable, clonable, castable), and it reports itself as UNPARSABLE.
 */
class UnparsedBlockStatementTest {

    @Test
    void isABlockStmtSubtype() {
        UnparsedBlockStatement node = new UnparsedBlockStatement();
        assertTrue(node instanceof BlockStmt, "must be a BlockStmt so it fits the body slot");
        assertTrue(node instanceof Node);
    }

    @Test
    void reportsItselfAsUnparsable() {
        UnparsedBlockStatement node = new UnparsedBlockStatement();
        assertEquals(Node.Parsedness.UNPARSABLE, node.getParsed());
    }

    @Test
    void castingHelpersWork() {
        UnparsedBlockStatement node = new UnparsedBlockStatement();
        assertTrue(node.isUnparsedBlockStatement());
        assertSame(node, node.asUnparsedBlockStatement());
        assertTrue(node.toUnparsedBlockStatement().isPresent());
    }

    @Test
    void clonesToSameType() {
        UnparsedBlockStatement node = new UnparsedBlockStatement();
        UnparsedBlockStatement clone = node.clone();
        assertTrue(clone instanceof UnparsedBlockStatement);
    }

    @Test
    void fitsMethodDeclarationBodySlot() {
        // The whole point: a bare Node would NOT fit, but because UnparsedBlockStatement
        // extends BlockStmt, it drops into MethodDeclaration.body with no AST change.
        MethodDeclaration method = new MethodDeclaration();
        UnparsedBlockStatement body = new UnparsedBlockStatement();
        method.setBody(body);
        assertTrue(method.getBody().isPresent());
        assertSame(body, method.getBody().get());
    }

    @Test
    void isFindableInATree() {
        MethodDeclaration method = new MethodDeclaration();
        method.setBody(new UnparsedBlockStatement());
        assertThat(method.findAll(UnparsedBlockStatement.class)).hasSize(1);
    }
}
