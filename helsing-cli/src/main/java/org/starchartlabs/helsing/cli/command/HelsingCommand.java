/*
 * Copyright (c) Jan 21, 2020 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.helsing.cli.command;

import picocli.CommandLine.Command;

/**
 * Represents the helsing command-set as a whole. This structure is used by the picocli library, as it assumes output
 * may be things structured like "git", where there is a first command indicating the program, and second indicating the
 * operation
 *
 * @author romeara
 * @since 0.1.0
 */
@Command(mixinStandardHelpOptions = true, subcommands = { DeadClassCandidatesCommand.class })
public class HelsingCommand implements Runnable {

    @Override
    public void run() {
        // This command has no behavior of its own, and serves to structure sub-commands
    }

}
