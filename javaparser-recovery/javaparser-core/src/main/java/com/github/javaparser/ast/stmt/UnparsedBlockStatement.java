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

import static com.github.javaparser.ast.Node.Parsedness.UNPARSABLE;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.AllFieldsConstructor;
import com.github.javaparser.ast.NodeList;
import java.util.Optional;
import java.util.function.Consumer;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.metamodel.UnparsedBlockStatementMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.Generated;

/**
 * A block-shaped body that could not be parsed.
 *
 * <p>Occupies the exact position a {@link BlockStmt} would (e.g. a method body),
 * but its contents could not be parsed by the standard grammar. Nothing is known
 * about it except the tokens it covers, available through the node's token range.
 *
 * <p>This is the block-body counterpart of {@link UnparsableStmt} (which retains
 * tokens at the statement level). It is produced by error recovery at the
 * {@code Block()} production when token retention is enabled, so that external
 * tooling can re-parse the covered tokens and replace this node with a valid
 * subtree.
 *
 * <p>Because it extends {@link BlockStmt}, it fits any slot typed for a block body
 * (such as {@code MethodDeclaration.body}) without changing those declarations.
 * Being a block describes only the <em>slot</em> it occupies, not the meaning of
 * its tokens.
 */
public class UnparsedBlockStatement extends BlockStmt {

    @AllFieldsConstructor
    public UnparsedBlockStatement() {
        this(null);
    }

    /**
     * This constructor is used by the parser and is considered private.
     */
    public UnparsedBlockStatement(TokenRange tokenRange) {
        super(tokenRange, new NodeList<>());
        customInitialization();
    }

    @Override
    public Parsedness getParsed() {
        return UNPARSABLE;
    }

    @Override
    public boolean isUnparsedBlockStatement() {
        return true;
    }

    @Override
    public UnparsedBlockStatement asUnparsedBlockStatement() {
        return this;
    }

    @Override
    public Optional<UnparsedBlockStatement> toUnparsedBlockStatement() {
        return Optional.of(this);
    }

    public void ifUnparsedBlockStatement(Consumer<UnparsedBlockStatement> action) {
        action.accept(this);
    }

    @Override
    public UnparsedBlockStatement clone() {
        return (UnparsedBlockStatement) accept(new CloneVisitor(), null);
    }

    @Override
    public UnparsedBlockStatementMetaModel getMetaModel() {
        return JavaParserMetaModel.unparsedBlockStatementMetaModel;
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.AcceptGenerator")
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    @Generated("com.github.javaparser.generator.core.node.AcceptGenerator")
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }
}
