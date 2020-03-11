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
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.starchartlabs.alloy.core.Strings;
import org.starchartlabs.helsing.core.model.ClassUseConsumer;

// TODO romeara
public class ReferenceClassVisitor extends ClassVisitor {

    // classname/how-used
    private final ClassUseConsumer referenceConsumer;

    private String currentClassName;

    public ReferenceClassVisitor(int api, ClassUseConsumer referenceConsumer) {
        this(api, referenceConsumer, null);
    }

    public ReferenceClassVisitor(int api, ClassUseConsumer referenceConsumer, ClassVisitor classVisitor) {
        super(api, classVisitor);

        this.referenceConsumer = Objects.requireNonNull(referenceConsumer);
        currentClassName = null;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentClassName = AsmUtils.toExternalName(name);
        String externalClassName = AsmUtils.toExternalName(name);

        registerUsedClass(superName, Strings.format("Super of %s", externalClassName));

        if (interfaces != null) {
            for (String internalInterfaceName : interfaces) {
                registerUsedClass(internalInterfaceName, Strings.format("Implemented by %s", externalClassName));
            }
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), Strings.format("%s type annotation", currentClassName));

        AnnotationVisitor superVisitor = super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        Type annotationType = Type.getType(descriptor);

        registerUsedClass(annotationType.getInternalName(), Strings.format("%s annotation", currentClassName));

        AnnotationVisitor superVisitor = super.visitAnnotation(descriptor, visible);

        return new ReferenceAnnotationVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        Type fieldType = Type.getType(descriptor);

        registerUsedClass(fieldType.getInternalName(), Strings.format("%s class field", currentClassName));

        FieldVisitor superVisitor = super.visitField(access, name, descriptor, signature, value);

        return new ReferenceFieldVisitor(getAsmApi(), currentClassName, referenceConsumer, superVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        String qualifiedMethodName = currentClassName + "." + name;

        // Register types for returns and arguments
        Type methodType = Type.getMethodType(desc);

        registerUsedClass(methodType.getReturnType().getInternalName(),
                Strings.format("%s declared method return", qualifiedMethodName));

        Stream.of(methodType.getArgumentTypes())
        .map(Type::getInternalName)
        .forEach(argumentType -> registerUsedClass(argumentType,
                Strings.format("%s declared method argument", qualifiedMethodName)));

        if (exceptions != null) {
            for (String exceptionName : exceptions) {
                registerUsedClass(exceptionName, Strings.format("%s thrown exception", qualifiedMethodName));
            }
        }

        MethodVisitor superVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        return new ReferenceMethodVisitor(
                getAsmApi(),
                currentClassName,
                referenceConsumer,
                superVisitor);
    }

    @Override
    public void visitEnd() {
        currentClassName = null;

        super.visitEnd();
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
