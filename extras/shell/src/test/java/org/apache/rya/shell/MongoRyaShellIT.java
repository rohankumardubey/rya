/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.rya.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.apache.rya.api.client.Install.InstallConfiguration;
import org.apache.rya.api.client.RyaClient;
import org.apache.rya.api.client.mongo.MongoConnectionDetails;
import org.apache.rya.api.client.mongo.MongoRyaClientFactory;
import org.apache.rya.shell.SharedShellState.ConnectionState;
import org.apache.rya.shell.SharedShellState.ShellState;
import org.apache.rya.shell.util.ConsolePrinter;
import org.apache.rya.shell.util.InstallPrompt;
import org.apache.rya.shell.util.PasswordPrompt;
import org.apache.rya.shell.util.SparqlPrompt;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.Bootstrap;
import org.springframework.shell.core.CommandResult;
import org.springframework.shell.core.JLineShellComponent;

import com.mongodb.MongoClient;

/**
 * Integration tests the functions of the Mongo Rya Shell.
 */
public class MongoRyaShellIT extends RyaShellMongoITBase {

    @Test
    public void connectMongo_noAuth() throws IOException {
        final JLineShellComponent shell = getTestShell();

        // Connect to the Mongo instance.
        final String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort();

        final CommandResult connectResult = shell.executeCommand(cmd);

        // Ensure the connection was successful.
        assertTrue(connectResult.isSuccess());
    }

    @Test
    public void printConnectionDetails_notConnected() {
        final JLineShellComponent shell = getTestShell();

        // Run the print connection details command.
        final CommandResult printResult = shell.executeCommand( RyaConnectionCommands.PRINT_CONNECTION_DETAILS_CMD );
        final String msg = (String) printResult.getResult();

        final String expected = "The shell is not connected to anything.";
        assertEquals(expected, msg);
    }

    @Test
    public void printConnectionDetails_connectedToMongo_noAuths() throws IOException {
        final JLineShellComponent shell = getTestShell();

        // Connect to the Mongo instance.
        final String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort();
        shell.executeCommand(cmd);

        // Run the print connection details command.
        final CommandResult printResult = shell.executeCommand( RyaConnectionCommands.PRINT_CONNECTION_DETAILS_CMD );
        final String msg = (String) printResult.getResult();

        final String expected =
                "The shell is connected to an instance of MongoDB using the following parameters:\n" +
                "    Hostname: " + super.conf.getMongoHostname() + "\n" +
                "    Port: " + super.conf.getMongoPort() + "\n";
        assertEquals(expected, msg);
    }

    @Test
    public void printConnectionDetails_connectedToMongo_auths() throws IOException {
        final Bootstrap bootstrap = getTestBootstrap();
        final JLineShellComponent shell = getTestShell();

        // Mock the user entering the correct password.
        final ApplicationContext context = bootstrap.getApplicationContext();
        final PasswordPrompt mockPrompt = context.getBean( PasswordPrompt.class );
        when(mockPrompt.getPassword()).thenReturn("password".toCharArray());

        // Connect to the Mongo instance.
        final String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort() + " " +
                        "--username bob";
        shell.executeCommand(cmd);

        // Run the print connection details command.
        final CommandResult printResult = shell.executeCommand( RyaConnectionCommands.PRINT_CONNECTION_DETAILS_CMD );
        final String msg = (String) printResult.getResult();

        final String expected =
                "The shell is connected to an instance of MongoDB using the following parameters:\n" +
                "    Hostname: " + super.conf.getMongoHostname() + "\n" +
                "    Port: " + super.conf.getMongoPort() + "\n" +
                "    Username: bob\n";
        assertEquals(expected, msg);
    }

    @Test
    public void connectToInstance_instanceDoesNotExist() throws IOException {
        final JLineShellComponent shell = getTestShell();

        // Connect to the Mongo instance.
        String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort();
        shell.executeCommand(cmd);

        // Try to connect to a non-existing instance.
        cmd = RyaConnectionCommands.CONNECT_INSTANCE_CMD + " --instance doesNotExist";
        final CommandResult result = shell.executeCommand(cmd);
        assertFalse( result.isSuccess() );
    }

    @Test
    public void connectToInstance_noAuths() throws IOException {
        final Bootstrap bootstrap = getTestBootstrap();
        final JLineShellComponent shell = getTestShell();

        // Connect to the Mongo instance.
        String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort();
        shell.executeCommand(cmd);

        // Install an instance of rya.
        final String instanceName = "testInstance";
        final InstallConfiguration installConf = InstallConfiguration.builder().build();

        final ApplicationContext context = bootstrap.getApplicationContext();
        final InstallPrompt installPrompt = context.getBean( InstallPrompt.class );
        when(installPrompt.promptInstanceName()).thenReturn("testInstance");
        when(installPrompt.promptInstallConfiguration("testInstance")).thenReturn( installConf );
        when(installPrompt.promptVerified(instanceName, installConf)).thenReturn(true);

        CommandResult result = shell.executeCommand( RyaAdminCommands.INSTALL_CMD );
        assertTrue( result.isSuccess() );

        // Connect to the instance that was just installed.
        cmd = RyaConnectionCommands.CONNECT_INSTANCE_CMD + " --instance " + instanceName;
        result = shell.executeCommand(cmd);
        assertTrue( result.isSuccess() );

        // Verify the shell state indicates it is connected to an instance.
        final SharedShellState sharedState = context.getBean( SharedShellState.class );
        final ShellState state = sharedState.getShellState();
        assertEquals(ConnectionState.CONNECTED_TO_INSTANCE, state.getConnectionState());
    }

    @Test
    public void disconnect() throws IOException {
        final JLineShellComponent shell = getTestShell();

        // Connect to the Mongo instance.
        final String cmd =
                RyaConnectionCommands.CONNECT_MONGO_CMD + " " +
                        "--hostname " + super.conf.getMongoHostname() + " " +
                        "--port " + super.conf.getMongoPort();
        shell.executeCommand(cmd);

        // Disconnect from it.
        final CommandResult disconnectResult = shell.executeCommand( RyaConnectionCommands.DISCONNECT_COMMAND_NAME_CMD );
        assertTrue( disconnectResult.isSuccess() );
    }

    // TODO the rest of them?


    @Test
    public void blah() throws Exception {
        final MongoConnectionDetails details =
                new MongoConnectionDetails(
                        "localhost",
                        27017,
                        Optional.empty(),
                        Optional.empty());
        final RyaClient client = MongoRyaClientFactory.build(details, new MongoClient("localhost", 27017));
        final SharedShellState state = new SharedShellState();
        state.connectedToMongo(details, client);
        state.connectedToInstance("rya_");
        final ShellState shell = state.getShellState();
        final SparqlPrompt sparqlPrompt = mock(SparqlPrompt.class);
        when(sparqlPrompt.getSparql()).thenReturn(
                com.google.common.base.Optional.<String>of("SELECT * WHERE { ?a ?b ?c }"));
        final RyaCommands cmnds = new RyaCommands
                (state, sparqlPrompt, systemPrinter);
        cmnds.sparqlQuery(null);
    }

    private static final ConsolePrinter systemPrinter = new ConsolePrinter() {

        @Override
        public void print(final CharSequence cs) throws IOException {
            System.out.print(cs);
        }

        @Override
        public void println(final CharSequence cs) throws IOException {
            System.out.println(cs);
        }

        @Override
        public void println() throws IOException {
            System.out.println();
        }

        @Override
        public void flush() throws IOException {
            System.out.flush();
        }
    };
}