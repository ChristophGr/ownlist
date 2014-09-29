/*
 * Copyright Christoph Gritschenberger 2014.
 *
 * This file is part of OwnList.
 *
 * OwnList is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OwnList is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OwnList.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.listsync;

import com.example.listsync.webdav.MyMiltonConfigurator;
import io.milton.servlet.MiltonServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class WebDavServerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private Server server;
    protected int localPort;

    @Before
    public void setUpJetty() throws Exception {
        Path tempPath = Paths.get(temporaryFolder.getRoot().toURI());
        Files.write(tempPath.resolve("test.txt"), "foo".getBytes());
        server = new Server(0);
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        ServletHandler handler = new ServletHandler();
        ServletHolder servletHolder = handler.addServletWithMapping(MiltonServlet.class, "/");
        servletHolder.setInitParameter("milton.configurator", MyMiltonConfigurator.class.getName());
        servletHolder.setInitParameter("webdav.root", temporaryFolder.getRoot().getAbsolutePath());
        server.setHandler(handler);
        server.start();
        localPort = connector.getLocalPort();
    }

    @After
    public void tearDownJetty() throws Exception {
        server.stop();
        server.join();
    }

}
