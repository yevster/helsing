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

import java.util.Objects;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.starchartlabs.alloy.core.Strings;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

//TODO romeara
public class ReferenceAnnotationVisitor extends AnnotationVisitor {

    private final String currentClassName;

    // classname/how-used
    private final ClassUseConsumer referenceConsumer;

    public ReferenceAnnotationVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer) {
        this(api, currentClassName, referenceConsumer, null);
    }

    public ReferenceAnnotationVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer,
            @Nullable AnnotationVisitor annotationVisitor) {
        super(api, annotationVisitor);

        this.currentClassName = Objects.requireNonNull(currentClassName);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
    }

    @Override
    public void visit(String name, Object value) {
        if (value instanceof Type) {
            Type valueType = (Type) value;

            String context = Strings.format("%s annotation '%s' value", currentClassName, name);
            registerUsedClass(valueType.getInternalName(), context);
        }

        super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        Type enumType = Type.getType(descriptor);

        String context = Strings.format("%s annotation '%s' enum value (%s)", currentClassName, name, value);
        registerUsedClass(enumType.getInternalName(), context);

        super.visitEnum(name, descriptor, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        AnnotationVisitor superVisitor = super.visitArray(name);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        Type annotationType = Type.getType(descriptor);

        String context = Strings.format("%s nested annotation", currentClassName);
        registerUsedClass(annotationType.getInternalName(), context);

        AnnotationVisitor superVisitor = super.visitAnnotation(name, descriptor);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    private int getAsmApi() {
        return api;
    }

    private void registerUsedClass(String internalClassName, String whereUsed) {
        Objects.requireNonNull(internalClassName);
        Objects.requireNonNull(whereUsed);

        // TODO log ignored self uses?
        // Referencing yourself doesn't count as a use
        String usedClassName = AsmUtils.toExternalName(internalClassName);

        if (!Objects.equals(currentClassName, usedClassName)) {
            referenceConsumer.recordUsedClass(usedClassName, currentClassName, whereUsed);
        }
    }

}
