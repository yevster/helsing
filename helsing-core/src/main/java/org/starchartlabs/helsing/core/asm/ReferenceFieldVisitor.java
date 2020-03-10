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
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.starchartlabs.alloy.core.Strings;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

//TODO romeara
public class ReferenceFieldVisitor extends FieldVisitor {

    private final String currentClassName;

    // classname/how-used
    private final ClassUseConsumer referenceConsumer;

    public ReferenceFieldVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer) {
        this(api, currentClassName, referenceConsumer, null);
    }

    public ReferenceFieldVisitor(int api, String currentClassName, ClassUseConsumer referenceConsumer,
            @Nullable FieldVisitor fieldVisitor) {
        super(api, fieldVisitor);

        this.currentClassName = Objects.requireNonNull(currentClassName);
        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), Strings.format("%s field annotation", currentClassName));

        AnnotationVisitor superVisitor = super.visitAnnotation(descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(),
                Strings.format("%s field type annotation", currentClassName));

        AnnotationVisitor superVisitor = super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);

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
