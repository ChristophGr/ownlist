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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class WebDAVIT extends WebDavServerTest {

    @Test
    public void testSavesItemInWebdav() throws Exception {
        final WebDavConfiguration config = new WebDavConfiguration(InetAddress.getLocalHost().getHostAddress(), localPort, "", null, null, false);
        Repository client1 = new WebDavRepository(config);
        ListRepository foo = client1.getList("foo");
        foo.add(new CheckItem("asdf"));
        List<CheckItem> content = foo.getContent();
        assertThat(content, contains(is(new CheckItem("asdf"))));
    }

}
