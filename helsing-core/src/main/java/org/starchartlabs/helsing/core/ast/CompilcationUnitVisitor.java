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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.starchartlabs.alloy.core.Strings;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

public class CompilcationUnitVisitor implements Consumer<String> {

    private final Set<String> unreferencedClasses;

    private final BiConsumer<String, String> referenceConsumer;

    public CompilcationUnitVisitor(Set<String> unreferencedClasses, BiConsumer<String, String> referenceConsumer) {
        this.unreferencedClasses = Objects.requireNonNull(unreferencedClasses);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
    }

    @Override
    public void accept(String contents) {
        Objects.requireNonNull(contents);

        CompilationUnit compilationUnit = StaticJavaParser.parse(contents);

        String currentClassName = compilationUnit.getPrimaryTypeName().orElse("");

        compilationUnit.getImports()
        .forEach(importStatement -> registerImport(currentClassName, importStatement));

        Collection<String> allowedGenerics = getAllowedGeneralReferences(compilationUnit);

        if (!allowedGenerics.isEmpty()) {
            for (String allowedGeneric : allowedGenerics) {
                Pattern pattern = Pattern.compile(".*[^a-zA-Z0-9]" + getSimpleName(allowedGeneric) + "[^a-zA-Z0-9].*",
                        Pattern.DOTALL);

                if (pattern.matcher(contents).matches()) {
                    referenceConsumer.accept(allowedGeneric, Strings.format("%s generic reference", currentClassName));
                }
            }
        }

        // TODO Handle direct fully-qualified reference
    }

    private void registerImport(String currentClass, ImportDeclaration importStatement) {
        Set<String> referencedClasses = new HashSet<>();

        if (importStatement.isStatic()) {
            referencedClasses = unreferencedClasses.stream()
                    .filter(sourceClass -> sourceClass.startsWith(importStatement.getNameAsString()))
                    .collect(Collectors.toSet());
        } else {
            referencedClasses.add(importStatement.getNameAsString());
        }

        referencedClasses
        .forEach(statement -> referenceConsumer.accept(statement, Strings.format("%s import", currentClass)));
    }

    private Collection<String> getAllowedGeneralReferences(CompilationUnit compilationUnit) {
        Set<String> definedTypes = compilationUnit.getTypes().stream()
                .map(TypeDeclaration::getFullyQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        String packageName = compilationUnit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        Collection<String> generalImports = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .map(statement -> statement.endsWith(".*") ? statement.substring(0, statement.length() - 2) : statement)
                .collect(Collectors.toSet());

        return unreferencedClasses.stream()
                .filter(sourceClass -> !definedTypes.stream().anyMatch(t -> sourceClass.startsWith(t)))
                .filter(sourceClass -> sourceClass.startsWith(packageName)
                        || generalImports.stream().anyMatch(i -> sourceClass.startsWith(i)))
                .collect(Collectors.toSet());
    }

    private String getSimpleName(String sourceClass) {
        String[] elements = sourceClass.split("\\.");

        return elements[elements.length - 1];
    }

}
