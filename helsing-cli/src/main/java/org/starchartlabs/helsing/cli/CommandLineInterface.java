/*
 * Copyright (c) Nov 8, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli;

import org.starchartlabs.helsing.cli.command.HelsingCommand;

import picocli.CommandLine;

public class CommandLineInterface {

    public static void main(String[] args) throws Exception {
        new CommandLine(new HelsingCommand()).execute(args);
    }

}
