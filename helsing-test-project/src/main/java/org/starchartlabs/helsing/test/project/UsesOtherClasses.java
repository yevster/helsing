/*
 * Copyright (c) Feb 3, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.test.project;

/**
 * This class uses others in specific ways, for testing they aren't marked as dead
 *
 * @author romeara
 */
public class UsesOtherClasses extends UsedViaExtension {

    public int getThing() {
        return new UsedViaSimpleMethod().add(1, 2);
    }

    public int getOtherThing() {
        return UsedViaStaticMethod.multiply(3, 4);
    }

}
