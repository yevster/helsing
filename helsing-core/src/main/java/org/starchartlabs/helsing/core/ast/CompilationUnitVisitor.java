/*
 * Copyright (c) Dec 16, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.ast;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class CompilationUnitVisitor implements BiConsumer<String, String> {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ClassUseConsumer referenceConsumer;

    private final JavaParser parser;

    private final YamlPrinter yamlPrinter;

    public CompilationUnitVisitor(ClassUseConsumer referenceConsumer) {
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);

        this.parser = new JavaParser();
        yamlPrinter = new YamlPrinter(true);
    }

    @Override
    public void accept(String contents, String filePath) {
        Objects.requireNonNull(contents);

        ParseResult<CompilationUnit> parseResult = parser.parse(contents);

        if (parseResult.isSuccessful()) {
            CompilationUnit compilationUnit = parseResult.getResult().get();
            FieldAccessVisitor fieldAccessVisitor = new FieldAccessVisitor();

            compilationUnit.accept(fieldAccessVisitor, null);

            if (compilationUnit.getTypes().size() > 0) {
                String currentClassName = getQualifiedName(compilationUnit);

                referenceConsumer.recordClassTracing(currentClassName,
                        () -> "AST Tree: " + yamlPrinter.output(compilationUnit));

                // Find direct imports of classes
                Collection<String> nonStaticImports = findNonStaticImports(referenceConsumer.getUnusedClasses(),
                        compilationUnit);
                String importUse = currentClassName + " import";

                nonStaticImports.stream()
                        .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName, importUse));

                // Deal with static import case
                Collection<String> staticImports = findStaticImports(referenceConsumer.getUnusedClasses(), compilationUnit);
                String staticImportUse = currentClassName + " static import";

                staticImports.stream()
                        .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName, staticImportUse));

                // Find uses of classes by simple name
                Map<String, String> allowedSimpleNameReferences = getValidSimpleNameReferences(
                        referenceConsumer.getUnusedClasses(),
                        compilationUnit);

                // Find uses of classes by fully qualified name
                Set<String> fieldClassesAccess = fieldAccessVisitor.getFieldClassesAccessed();

                // Record fully qualified uses
                Collection<String> fullyQualfiedAccess = referenceConsumer.getUnusedClasses().stream()
                        .filter(fieldClassesAccess::contains)
                        .collect(Collectors.toSet());
                String qualifiedUse = currentClassName + " fully qualified reference";

                fullyQualfiedAccess.stream()
                        .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName, qualifiedUse));

                // Record simple name uses
                Collection<String> simpleNameAccess = referenceConsumer.getUnusedClasses().stream()
                        .filter(allowedSimpleNameReferences::containsKey)
                        .filter(classToFind -> fieldClassesAccess.contains(allowedSimpleNameReferences.get(classToFind)))
                        .collect(Collectors.toSet());
                String simpleNameUse = currentClassName + " simple name reference";

                simpleNameAccess.stream()
                        .forEach(used -> referenceConsumer.recordUsedClass(used, currentClassName, simpleNameUse));
            } else {
                // Log file without valid types
                // TODO reduce log level?
                logger.info("Found Java file with no valid types: {}", filePath);
            }
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
