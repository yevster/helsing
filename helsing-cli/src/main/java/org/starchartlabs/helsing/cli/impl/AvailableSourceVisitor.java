/*
 * Copyright (c) Nov 12, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli.impl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO romeara
public class AvailableSourceVisitor extends ClassVisitor {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Set<String> sourceClassNames;

    private String baseName;

    private boolean anonymous;

    public AvailableSourceVisitor(int api) {
        super(api);

        sourceClassNames = new HashSet<>();
        reset();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        baseName = name;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // If the innerName is null, this is an "anonymous" class, as opposed to a defined inner class
        // https://stackoverflow.com/questions/42676404/how-do-i-know-if-i-am-visiting-an-anonymous-class-in-asm
        logger.debug("Visiting inner class {}:{}", name, innerName);

        anonymous = (innerName == null);
    }

    @Override
    public void visitEnd() {
        Optional<String> sourceClassName = Optional.ofNullable(baseName)
                .filter(name -> !anonymous);

        sourceClassName.ifPresent(sourceClassNames::add);

        reset();
    }

    public Set<String> getSourceClassNames() {
        return sourceClassNames;
    }

    private void reset() {
        this.baseName = null;
        this.anonymous = false;
    }

}
