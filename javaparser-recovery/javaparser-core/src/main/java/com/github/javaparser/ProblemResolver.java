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

import com.github.javaparser.ast.Node;

/**
 * Asked, one {@link Problem} at a time, whether it can resolve that problem — for example
 * by re-parsing and replacing an {@code UnparsedBlockStatement} that the problem refers to.
 *
 * <p>The parser is the sole owner of the problem list. After parsing and before
 * {@link Processor}s run, the parser iterates the recorded problems and asks each registered
 * resolver whether it resolves a given problem. When a resolver returns {@code true}, the
 * <em>parser</em> removes that problem from the list. A resolver must never mutate the
 * problem list itself: it provides policy ("can this be resolved?"), the parser owns the
 * mechanism (list mutation).
 *
 * <p>Resolvers are registered on {@link ParserConfiguration#getProblemResolvers()}. When
 * none are registered, the parser skips resolution entirely and behavior is unchanged.
 */
@FunctionalInterface
public interface ProblemResolver {

    /**
     * @param result        the parse result (with its partial AST and problem list)
     * @param configuration the configuration in effect for this parse
     * @param problem       a single recorded problem to consider
     * @return {@code true} if this resolver resolved the problem (the parser will then
     *         remove it from the problem list); {@code false} otherwise
     */
    boolean isProblemResolved(ParseResult<? extends Node> result,
                              ParserConfiguration configuration,
                              Problem problem);
}
