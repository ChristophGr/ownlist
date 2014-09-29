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

package com.example.listsync.webdav;

import io.milton.http.HttpManager;
import io.milton.http.fs.FileSystemResourceFactory;
import io.milton.http.fs.NullSecurityManager;
import io.milton.servlet.Config;
import io.milton.servlet.DefaultMiltonConfigurator;

import javax.servlet.ServletException;
import java.io.File;

public class MyMiltonConfigurator extends DefaultMiltonConfigurator {

    @Override
    public HttpManager configure(Config config) throws ServletException {
        String pathname = config.getInitParameter("webdav.root");
        builder.setMainResourceFactory(new FileSystemResourceFactory(new File(pathname), new NullSecurityManager()));
        return super.configure(config);
    }

}
