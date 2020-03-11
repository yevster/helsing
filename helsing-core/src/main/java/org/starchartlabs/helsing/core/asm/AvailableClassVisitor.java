/*
 * Copyright (c) Dec 9, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.core.asm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.helsing.core.io.FileConsumer;
import org.starchartlabs.helsing.core.io.FilteringFileVisitor;

//TODO romeara
public class AvailableClassVisitor extends ClassVisitor implements FileConsumer {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<String> classNames;

    private String baseName;

    private boolean anonymous;

    public AvailableClassVisitor(int api) {
        super(api);

        classNames = new HashSet<>();
        reset();
    }

    public AvailableClassVisitor(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);

        classNames = new HashSet<>();
        reset();
    }

    public FileVisitor<Path> getFileVisitor(Predicate<Path> fileFilter) {
        Objects.requireNonNull(fileFilter);

        Predicate<Path> classFileFilter = file -> file.toString().matches(".*\\.class");

        return new FilteringFileVisitor(classFileFilter.and(fileFilter), this);
    }

    public Set<String> getClassNames() {
        return classNames;
    }

    @Override
    public void acceptFile(Path file) throws IOException {
        Objects.requireNonNull(file);

        try (InputStream inputStream = Files.newInputStream(file)) {
            ClassReader cr = new ClassReader(inputStream);

            cr.accept(this, 0);
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        baseName = name;

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // If the innerName is null, this is an "anonymous" class, as opposed to a defined inner class
        // https://stackoverflow.com/questions/42676404/how-do-i-know-if-i-am-visiting-an-anonymous-class-in-asm
        logger.debug("Visiting inner class {}:{}", name, innerName);

        anonymous = (innerName == null);

        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitEnd() {
        // Standardize names from folder name style to dot-separated names
        Optional<String> sourceClassName = Optional.ofNullable(baseName)
                .filter(name -> !anonymous)
                .map(AsmUtils::toExternalName);

        sourceClassName.ifPresent(classNames::add);

        reset();

        super.visitEnd();
    }

    private void reset() {
        this.baseName = null;
        this.anonymous = false;
    }

}
