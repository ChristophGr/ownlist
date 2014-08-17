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

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class WebDAVUT {

    @Test
    public void testName() throws Exception {
        final WebDavConfiguration config = buildConfigFromProperties();
        Repository client1 = new WebDavRepository(config);
        client1.upload("foo", new ArrayList<String>());
        Repository slowClient = (Repository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Repository.class}, new InvocationHandler() {
            private Repository target = new WebDavRepository(config);

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Thread.sleep(1000);
                return method.invoke(target, args);
            }
        });
        ListRepository foo = new ListRepository("foo", client1);
        final ListRepository fooRemote = new ListRepository("foo", slowClient);

        foo.add(Arrays.asList(new CheckItem("test")));
        Thread bg = new Thread() {
            @Override
            public void run() {
                try {
                    fooRemote.add(Arrays.asList(new CheckItem("bar")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("removing test");
                try {
                    fooRemote.remove(Arrays.asList(new CheckItem("test")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("test removed");
            }
        };
        bg.start();
        foo.add(Arrays.asList(new CheckItem("bar2")));
        bg.join();
        foo.refresh();
        assertThat(foo.getContent(), containsInAnyOrder(new CheckItem("bar"), new CheckItem("bar2")));
    }

    private WebDavConfiguration buildConfigFromProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(ClassLoader.getSystemResourceAsStream("webdavut.properties"));
        String adress = properties.getProperty("adress");
        String watchpath = properties.getProperty("watchpath");
        int port = Integer.parseInt(properties.getProperty("port"));
        String user = properties.getProperty("user");
        String password = properties.getProperty("password");
        return WebDavConfiguration.builder(adress, watchpath)
                .customPort(port)
                .credentials(user, password)
                .build();
    }

}
