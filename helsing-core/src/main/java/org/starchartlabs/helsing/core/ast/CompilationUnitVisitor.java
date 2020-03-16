/*
 * Copyright (c) Dec 16, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.ast;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.alloy.core.Strings;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.printer.YamlPrinter;

public class CompilationUnitVisitor implements Consumer<String> {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<String> unreferencedClasses;

    private final ClassUseConsumer referenceConsumer;

    private final Optional<String> traceClass;

    private final JavaParser parser;

    public CompilationUnitVisitor(Set<String> unreferencedClasses, ClassUseConsumer referenceConsumer,
            @Nullable String traceClass) {
        this.unreferencedClasses = Objects.requireNonNull(unreferencedClasses);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
        this.traceClass = Optional.ofNullable(traceClass);

        this.parser = new JavaParser();
    }

    @Override
    public void accept(String contents) {
        Objects.requireNonNull(contents);

        ParseResult<CompilationUnit> parseResult = parser.parse(contents);

        if (parseResult.isSuccessful()) {
            CompilationUnit compilationUnit = parseResult.getResult().get();
            FieldAccessVisitor fieldAccessVisitor = new FieldAccessVisitor();

            compilationUnit.accept(fieldAccessVisitor, null);

            String currentClassName = getQualifiedName(compilationUnit);

            logTracing(compilationUnit, currentClassName);

            // Find direct imports of classes
            findNonStaticImports(unreferencedClasses, compilationUnit).stream()
            .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName,
                            Strings.format("%s import", currentClassName)));

            findStaticImports(unreferencedClasses, compilationUnit).stream()
            .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName,
                            Strings.format("%s static import", currentClassName)));

            // Find uses of classes by simple name
            Map<String, String> allowedSimpleNameReferences = getValidSimpleNameReferences(unreferencedClasses,
                    compilationUnit);

            // Find uses of classes by fully qualified name
            Set<String> fieldClassesAccess = fieldAccessVisitor.getFieldClassesAccessed();

            // Record fully qualified uses
            unreferencedClasses.stream()
            .filter(fieldClassesAccess::contains)
            .forEach(classFound -> referenceConsumer.recordUsedClass(classFound, currentClassName,
                            Strings.format("%s fully qualified reference", currentClassName)));

            // Record simple name uses
            unreferencedClasses.stream()
            .filter(allowedSimpleNameReferences::containsKey)
            .filter(classToFind -> fieldClassesAccess.contains(allowedSimpleNameReferences.get(classToFind)))
                    .forEach(classFound -> referenceConsumer.recordUsedClass(classFound, currentClassName,
                            Strings.format("%s simple name reference", currentClassName)));
        } else {
            throw new ParseProblemException(parseResult.getProblems());
        }
    }

    private String getQualifiedName(CompilationUnit compilationUnit) {
        Optional<String> packageName = compilationUnit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString);

        String className = compilationUnit.getType(0).getNameAsString();

        return packageName
                .map(pack -> pack + "." + className)
                .orElse(className);
    }

    private void logTracing(CompilationUnit compilationUnit, String currentClassName) {
        if (Objects.equals(currentClassName, traceClass.orElse(null))) {
            YamlPrinter printer = new YamlPrinter(true);
            logger.info("{} AST Tree: {}", traceClass.orElse(null), printer.output(compilationUnit));
        }
    }

    private Collection<String> findNonStaticImports(Set<String> classesToFind, CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(statement -> !statement.isStatic())
                .map(ImportDeclaration::getNameAsString)
                .filter(classesToFind::contains)
                .collect(Collectors.toSet());
    }

    private Collection<String> findStaticImports(Set<String> classesToFind, CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
                .filter(ImportDeclaration::isStatic)
                .map(ImportDeclaration::getNameAsString)
                .filter(statement -> classesToFind.stream().anyMatch(c -> statement.startsWith(c)))
                .collect(Collectors.toSet());
    }

    // Finds unreferenced classes which are allowed to referenced by simple name
    private Map<String, String> getValidSimpleNameReferences(Set<String> classesToFind,
            CompilationUnit compilationUnit) {
        String packageName = compilationUnit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        Set<String> innerClasses = compilationUnit.getTypes().stream()
                .map(TypeDeclaration::getFullyQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        Collection<String> wildcardImports = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .map(statement -> statement.endsWith(".*") ? statement.substring(0, statement.length() - 2) : statement)
                .collect(Collectors.toSet());

        Predicate<String> notDefinedLocally = (className -> !innerClasses.stream()
                .anyMatch(t -> className.startsWith(t)));
        Predicate<String> wildcardImportOrSamePackage = (className -> className
                .equals(packageName + "." + getSimpleName(className))
                || wildcardImports.stream().anyMatch(wildcardPackage -> className.startsWith(wildcardPackage)));

        return classesToFind.stream()
                .filter(notDefinedLocally)
                .filter(wildcardImportOrSamePackage)
                .collect(Collectors.toMap(Function.identity(), this::getSimpleName));
    }

    private String getSimpleName(String sourceClass) {
        String[] elements = sourceClass.split("\\.");

        return elements[elements.length - 1];
    }

    private static final class FieldAccessVisitor extends GenericVisitorAdapter<String, String> {

        private final Set<String> fieldClassesAccessed = new HashSet<>();

        public Set<String> getFieldClassesAccessed() {
            return fieldClassesAccessed;
        }

        @Override
        public String visit(ClassExpr n, String arg) {
            String found = n.getType().accept(new NameBuildingVisitor(), null);

            if (found != null && !found.trim().isEmpty()) {
                fieldClassesAccessed.add(found);
            }

            return null;
        }

        @Override
        public String visit(FieldAccessExpr n, String arg) {
            String found = n.getScope().accept(new NameBuildingVisitor(), null);

            if (found != null && !found.trim().isEmpty()) {
                fieldClassesAccessed.add(found);
            }

            return null;
        }

    }

    private static final class NameBuildingVisitor extends GenericVisitorAdapter<String, String> {

        @Override
        public String visit(ClassOrInterfaceType n, String arg) {
            String combinedName = n.getNameAsString() + (arg != null ? "." + arg : "");

            return n.getScope()
                    .map(scope -> scope.accept(this, combinedName))
                    .orElse(combinedName);
        }

        @Override
        public String visit(FieldAccessExpr n, String arg) {
            return n.getScope().accept(this, n.getNameAsString() + (arg != null ? "." + arg : ""));
        }

        @Override
        public String visit(NameExpr n, String arg) {
            return n.getNameAsString() + (arg != null ? "." + arg : "");
        }

    }

}
