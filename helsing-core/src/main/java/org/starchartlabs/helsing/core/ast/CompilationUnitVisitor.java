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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.starchartlabs.alloy.core.Strings;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

public class CompilationUnitVisitor implements Consumer<String> {

    private final Set<String> unreferencedClasses;

    private final BiConsumer<String, String> referenceConsumer;

    public CompilationUnitVisitor(Set<String> unreferencedClasses, BiConsumer<String, String> referenceConsumer) {
        this.unreferencedClasses = Objects.requireNonNull(unreferencedClasses);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
    }

    @Override
    public void accept(String contents) {
        Objects.requireNonNull(contents);

        CompilationUnit compilationUnit = StaticJavaParser.parse(contents);

        String currentClassName = compilationUnit.getPrimaryTypeName().orElse("");

        // Find direct imports of classes
        findNonStaticImports(unreferencedClasses, compilationUnit).stream()
        .forEach(used -> referenceConsumer.accept(used, Strings.format("%s import", currentClassName)));

        findStaticImports(unreferencedClasses, compilationUnit).stream()
        .forEach(used -> referenceConsumer.accept(used, Strings.format("%s static import", currentClassName)));

        // Find uses of classes by simple name
        Map<String, String> allowedSimpleNameReferences = getValidSimpleNameReferences(unreferencedClasses,
                compilationUnit);

        if (!allowedSimpleNameReferences.isEmpty()) {
            for (Entry<String, String> allowedSimpleNameEntry : allowedSimpleNameReferences.entrySet()) {
                Pattern pattern = Pattern.compile(
                        ".*[^a-zA-Z0-9]" + allowedSimpleNameEntry.getValue() + "[^a-zA-Z0-9].*", Pattern.DOTALL);

                if (pattern.matcher(contents).matches()) {
                    referenceConsumer.accept(allowedSimpleNameEntry.getKey(),
                            Strings.format("%s simple name reference", currentClassName));
                }
            }
        }

        // Find uses of classes by fully qualified name
        for (String classToFind : unreferencedClasses) {
            Pattern pattern = Pattern.compile(".*[^a-zA-Z0-9]" + classToFind + "[^a-zA-Z0-9].*", Pattern.DOTALL);

            if (pattern.matcher(contents).matches()) {
                referenceConsumer.accept(classToFind, Strings.format("%s fully qualified reference", currentClassName));
            }
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

}
