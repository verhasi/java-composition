package com.github.preprocessor.transform;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AST visitor that expands method reference bodies ({@code = MethodRef;}) into
 * standard Java method bodies by resolving the method reference and generating
 * the correct invocation expression.
 *
 * <p>Requires a configured {@link TypeSolver} to disambiguate between static
 * and unbound instance method references ({@code Type::method}).
 *
 * <h2>Expansion Rules</h2>
 * <ul>
 *   <li>Bound instance: {@code expr::method} → {@code expr.method(params...)}</li>
 *   <li>Unbound instance: {@code Type::method} → {@code param0.method(param1, ...)}</li>
 *   <li>Static: {@code Type::method} → {@code Type.method(params...)}</li>
 *   <li>Constructor: {@code Type::new} → {@code new Type(params...)}</li>
 *   <li>Array creation: {@code Type[]::new} → {@code new Type[param0]}</li>
 * </ul>
 */
public class MethodReferenceBodyTransformer extends ModifierVisitor<Void> {

    private final TypeSolver typeSolver;
    private boolean transformed;

    /**
     * Create a transformer with the given type solver.
     *
     * @param typeSolver configured type solver for resolving method references
     */
    public MethodReferenceBodyTransformer(TypeSolver typeSolver) {
        this.typeSolver = typeSolver;
    }

    /**
     * Create a type solver from source root and classpath.
     *
     * @param sourceRoot source root for resolving types in the project
     * @param classpath  list of directories and JAR files
     * @return a combined type solver
     */
    public static TypeSolver createTypeSolver(Path sourceRoot, List<Path> classpath) {
        CombinedTypeSolver combined = new CombinedTypeSolver();
        combined.add(new ReflectionTypeSolver()); // JDK classes

        if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
            combined.add(new JavaParserTypeSolver(sourceRoot));
        }

        for (Path entry : classpath) {
            try {
                if (entry.toString().endsWith(".jar") && Files.exists(entry)) {
                    combined.add(new JarTypeSolver(entry));
                } else if (Files.isDirectory(entry)) {
                    combined.add(new JavaParserTypeSolver(entry));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to add classpath entry: " + entry, e);
            }
        }

        return combined;
    }

    /**
     * @return true if the last visit() call transformed any method reference bodies
     */
    public boolean hasTransformed() {
        return transformed;
    }

    /**
     * Reset the transformed flag before a new visit.
     */
    public void resetTransformed() {
        this.transformed = false;
    }

    @Override
    public Visitable visit(MethodDeclaration md, Void arg) {
        if (md.hasMethodReferenceBody()) {
            Expression refExpr = md.getMethodReferenceBody().orElseThrow();

            if (!(refExpr instanceof MethodReferenceExpr methodRef)) {
                throw new IllegalStateException(
                        "Method reference body must be a MethodReferenceExpr, got: " + refExpr.getClass().getSimpleName()
                                + " in method " + md.getNameAsString());
            }

            Expression invocation = expandMethodReference(md, methodRef);

            NodeList<Statement> stmts = new NodeList<>();
            if (md.getType() instanceof VoidType) {
                stmts.add(new ExpressionStmt(invocation));
            } else {
                stmts.add(new ReturnStmt(invocation));
            }

            md.setBody(new BlockStmt(stmts));
            md.setMethodReferenceBody(null);
            transformed = true;
        }
        return super.visit(md, arg);
    }

    private Expression expandMethodReference(MethodDeclaration md, MethodReferenceExpr methodRef) {
        String identifier = methodRef.getIdentifier();
        Expression scope = methodRef.getScope();

        // Case 1: Constructor reference (Type::new)
        if ("new".equals(identifier)) {
            return expandConstructorReference(md, scope);
        }

        // Case 2: Array creation reference (Type[]::new) 
        // Handled by constructor case since JavaParser parses it with scope as ArrayType
        if (scope instanceof TypeExpr typeExpr && typeExpr.getType() instanceof ArrayType) {
            return expandArrayCreationReference(md, typeExpr);
        }

        // Case 3: Bound instance reference (expr::method) - scope is not a type name
        // e.g., aList::size, this::method, super::method
        if (isBoundReference(scope)) {
            return expandBoundInstanceReference(md, scope, identifier);
        }

        // Case 4: Type::method - need to resolve: static or unbound instance?
        return expandTypeMethodReference(md, scope, identifier);
    }

    private Expression expandConstructorReference(MethodDeclaration md, Expression scope) {
        // Type::new → new Type(params...)
        // Array::new → new Type[param0]
        if (scope instanceof TypeExpr typeExpr) {
            if (typeExpr.getType() instanceof ArrayType arrayType) {
                // Array creation: int[]::new → new int[size]
                NodeList<Parameter> params = md.getParameters();
                if (params.isEmpty()) {
                    throw new IllegalStateException("Array creation reference requires at least one parameter");
                }
                NodeList<Expression> dimensions = new NodeList<>();
                dimensions.add(new NameExpr(params.get(0).getNameAsString()));
                return new ArrayCreationExpr(
                        arrayType.getComponentType(),
                        new NodeList<>(new ArrayCreationLevel(new NameExpr(params.get(0).getNameAsString()))),
                        null);
            }
            // Regular constructor: Foo::new → new Foo(params...)
            NodeList<Expression> args = paramsToArgs(md.getParameters(), 0);
            return new ObjectCreationExpr(null, typeExpr.getType().asClassOrInterfaceType(), args);
        }

        // Fallback: treat scope as a class name
        NodeList<Expression> args = paramsToArgs(md.getParameters(), 0);
        return new ObjectCreationExpr(null,
                new com.github.javaparser.ast.type.ClassOrInterfaceType(null, scope.toString()),
                args);
    }

    private Expression expandArrayCreationReference(MethodDeclaration md, TypeExpr typeExpr) {
        ArrayType arrayType = (ArrayType) typeExpr.getType();
        NodeList<Parameter> params = md.getParameters();
        if (params.isEmpty()) {
            throw new IllegalStateException("Array creation reference requires at least one parameter");
        }
        return new ArrayCreationExpr(
                arrayType.getComponentType(),
                new NodeList<>(new ArrayCreationLevel(new NameExpr(params.get(0).getNameAsString()))),
                null);
    }

    private Expression expandBoundInstanceReference(MethodDeclaration md, Expression scope, String methodName) {
        // expr::method → expr.method(params...)
        // If scope is a TypeExpr (parser wraps ambiguous names), unwrap to NameExpr
        Expression receiver;
        if (scope instanceof TypeExpr typeExpr) {
            receiver = new NameExpr(typeExpr.getType().asString());
        } else {
            receiver = scope.clone();
        }
        NodeList<Expression> args = paramsToArgs(md.getParameters(), 0);
        return new MethodCallExpr(receiver, methodName, args);
    }

    private Expression expandTypeMethodReference(MethodDeclaration md, Expression scope, String methodName) {
        // Type::method → resolve whether it's static or instance
        String typeName = scope.toString();

        boolean isStatic = resolveIsStaticMethod(typeName, methodName, md);

        if (isStatic) {
            // Static: Type.method(params...)
            NodeList<Expression> args = paramsToArgs(md.getParameters(), 0);
            return new MethodCallExpr(new NameExpr(typeName), methodName, args);
        } else {
            // Unbound instance: param0.method(param1, param2, ...)
            NodeList<Parameter> params = md.getParameters();
            if (params.isEmpty()) {
                throw new IllegalStateException(
                        "Unbound instance method reference requires at least one parameter (receiver), " +
                                "but method " + md.getNameAsString() + " has none");
            }
            Expression receiver = new NameExpr(params.get(0).getNameAsString());
            NodeList<Expression> args = paramsToArgs(params, 1);
            return new MethodCallExpr(receiver, methodName, args);
        }
    }

    /**
     * Determine if the scope represents a bound reference (field, variable, this, super)
     * as opposed to a type name.
     */
    private boolean isBoundReference(Expression scope) {
        // 'this' or 'super' are always bound
        if (scope instanceof ThisExpr || scope instanceof SuperExpr) {
            return true;
        }

        // Field access (e.g., this.field, obj.field) is bound
        if (scope instanceof FieldAccessExpr) {
            return true;
        }

        // A name expression could be a type or a variable.
        // Convention: type names start with uppercase, variables with lowercase.
        if (scope instanceof NameExpr nameExpr) {
            String name = nameExpr.getNameAsString();
            return Character.isLowerCase(name.charAt(0));
        }

        // TypeExpr: JavaParser wraps ambiguous references in TypeExpr.
        // If the type name starts with lowercase, it's actually a variable/field.
        if (scope instanceof TypeExpr typeExpr) {
            String typeName = typeExpr.getType().asString();
            // Simple name starting with lowercase → variable/field (bound)
            // Starts with uppercase or is qualified → type (unbound or static)
            return !typeName.contains(".") && Character.isLowerCase(typeName.charAt(0));
        }

        // Any other expression (method call, array access, etc.) is bound
        return true;
    }

    /**
     * Resolve whether the named method on the given type is static.
     * Uses the Symbol Solver for type resolution.
     */
    private boolean resolveIsStaticMethod(String typeName, String methodName, MethodDeclaration md) {
        try {
            ResolvedReferenceTypeDeclaration typeDecl = solveType(typeName, md);
            int paramCount = md.getParameters().size();

            // Look for the method - check if any matching method is static
            Set<ResolvedMethodDeclaration> methods = typeDecl.getDeclaredMethods().stream()
                    .filter(m -> m.getName().equals(methodName))
                    .collect(Collectors.toSet());

            if (methods.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot resolve method '" + methodName + "' on type '" + typeName + "'");
            }

            // If there's a static method with matching arity → static
            boolean hasStaticMatch = methods.stream()
                    .anyMatch(m -> m.isStatic() && m.getNumberOfParams() == paramCount);

            // If there's an instance method with arity = paramCount - 1 → unbound instance
            boolean hasInstanceMatch = methods.stream()
                    .anyMatch(m -> !m.isStatic() && m.getNumberOfParams() == paramCount - 1);

            if (hasStaticMatch && !hasInstanceMatch) {
                return true;
            }
            if (hasInstanceMatch && !hasStaticMatch) {
                return false;
            }
            if (hasStaticMatch && hasInstanceMatch) {
                throw new IllegalStateException(
                        "Ambiguous method reference: both static and instance methods named '" + methodName +
                                "' match on type '" + typeName + "' for method " + md.getNameAsString());
            }

            // Neither matched exactly by arity — try just checking if any method is static
            boolean anyStatic = methods.stream().anyMatch(ResolvedMethodDeclaration::isStatic);
            boolean anyInstance = methods.stream().anyMatch(m -> !m.isStatic());

            if (anyStatic && !anyInstance) return true;
            if (anyInstance && !anyStatic) return false;

            throw new IllegalStateException(
                    "Cannot determine if '" + typeName + "::" + methodName +
                            "' is static or instance for method " + md.getNameAsString());

        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException(
                    "Failed to resolve type '" + typeName + "' for method reference in " +
                            md.getNameAsString() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convert method parameters to argument expressions, starting from the given index.
     */
    private NodeList<Expression> paramsToArgs(NodeList<Parameter> params, int startIndex) {
        NodeList<Expression> args = new NodeList<>();
        for (int i = startIndex; i < params.size(); i++) {
            args.add(new NameExpr(params.get(i).getNameAsString()));
        }
        return args;
    }

    /**
     * Resolve a type by name. Tries the name as-is first, then with java.lang. prefix
     * for unqualified names.
     */
    private ResolvedReferenceTypeDeclaration solveType(String typeName, MethodDeclaration md) {
        // Try as-is (works for fully qualified names)
        try {
            return typeSolver.solveType(typeName);
        } catch (Exception e) {
            // Fall through
        }

        // Try with java.lang prefix (for String, Integer, Math, etc.)
        if (!typeName.contains(".")) {
            try {
                return typeSolver.solveType("java.lang." + typeName);
            } catch (Exception e) {
                // Fall through
            }
        }

        // Try same package as the declaring class
        if (!typeName.contains(".") && md.findCompilationUnit().isPresent()) {
            var cu = md.findCompilationUnit().get();
            if (cu.getPackageDeclaration().isPresent()) {
                String pkg = cu.getPackageDeclaration().get().getNameAsString();
                try {
                    return typeSolver.solveType(pkg + "." + typeName);
                } catch (Exception e) {
                    // Fall through
                }
            }
        }

        // Try imports from the compilation unit
        if (md.findCompilationUnit().isPresent()) {
            var cu = md.findCompilationUnit().get();
            for (var imp : cu.getImports()) {
                if (!imp.isAsterisk() && imp.getNameAsString().endsWith("." + typeName)) {
                    try {
                        return typeSolver.solveType(imp.getNameAsString());
                    } catch (Exception e) {
                        // Fall through
                    }
                }
                if (imp.isAsterisk()) {
                    try {
                        return typeSolver.solveType(imp.getNameAsString() + "." + typeName);
                    } catch (Exception e) {
                        // Fall through
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Cannot resolve type '" + typeName + "' for method reference in " + md.getNameAsString());
    }
}
