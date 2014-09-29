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

import com.google.common.base.*;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.MoveMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Arrays;
import java.util.List;

public class WebDavRepository implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebDavRepository.class);

    private final WebDavConfiguration config;
    private HttpClient client;

    public WebDavRepository(WebDavConfiguration config) {
        this.config = config;
        init();
    }

    private void init() {
        client = new HttpClient();
        if (config.getUsername() != null) {
            client.getState().setCredentials(
                    new AuthScope(config.getAdress(), config.getPort()),
                    new UsernamePasswordCredentials(config.getUsername(), config.getPassword())
            );
        }
    }

    private String getFullWatchURL() {
        return config.getBaseUrl() + "/" + config.getWatchpath() + "/";
    }

    @Override
    public ListRepository getList(String name) {
        return new WebListDavRepository(name);
    }

    private MultiStatusResponse[] doPropFind(String url) throws IOException {
        DavMethod pFind = new PropFindMethod(url, DavConstants.PROPFIND_ALL_PROP, DavConstants.DEPTH_INFINITY);
        client.executeMethod(pFind);
        MultiStatus multiStatus;
        try {
            multiStatus = pFind.getResponseBodyAsMultiStatus();
        } catch (DavException e) {
            throw new IOException(e);
        }
        return multiStatus.getResponses();
    }

    @Override
    public List<String> getLists() throws IOException {
        MultiStatusResponse[] responses = doPropFind(getFullWatchURL());
        return Lists.newArrayList(Iterables.filter(
                Iterables.transform(Arrays.asList(responses), new Function<MultiStatusResponse, String>() {
                    @Override
                    public String apply(MultiStatusResponse multiStatusResponse) {
                        return multiStatusResponse.getHref().replaceFirst("/" + config.getWatchpath(), "");
                    }
                }), new Predicate<String>() {
                    @Override
                    public boolean apply(String s) {
                        return !Strings.isNullOrEmpty(s) && !s.endsWith("/");
                    }
                }
        ));
        /*List<DavResource> list = sardine.list(getFullWatchURL());
        return Lists.newArrayList(
                Iterables.transform(
                        Iterables.filter(list, new Predicate<DavResource>() {
                            @Override
                            public boolean apply(DavResource input) {
                                return input.isDirectory();
                            }
                        }),
                        new Function<DavResource, String>() {
                            @Override
                            public String apply(DavResource input) {
                                return input.getName();
                            }
                        }
                )
        );*/
    }

    private class WebListDavRepository implements ListRepository {
        private final String listName;

        public WebListDavRepository(String listName) {
            this.listName = listName;
        }

        @Override
        public List<CheckItem> getContent() throws IOException {
            MultiStatusResponse[] responses = doPropFind(getFullWatchURL() + "/" + listName);
            return FluentIterable.from(Arrays.asList(responses))
                    .transform(new Function<MultiStatusResponse, String>() {
                        @Override
                        public String apply(MultiStatusResponse input) {
                            return input.getHref()
                                    .replaceFirst("/" + config.getWatchpath() + "/" + listName + "/", "")
                                    .replaceAll("^\\/*", "");                        }
                    })
                    .filter(new Predicate<String>() {
                        @Override
                        public boolean apply(String input) {
                            return !input.isEmpty();
                        }
                    })
                    .transform(new Function<String, CheckItem>() {
                        @Override
                        public CheckItem apply(String input) {
                            try {
                                String unescaped = URLDecoder.decode(input, Charsets.UTF_8.name());
                                return CheckItem.fromString(unescaped);
                            } catch (UnsupportedEncodingException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    })
                    .toList();
        }

        @Override
        public void remove(CheckItem item) throws IOException {
            DeleteMethod deleteMethod = new DeleteMethod(itemUrl(item));
            int code = client.executeMethod(deleteMethod);
            LOGGER.info("upload resultcode: {}", code);
        }

        @Override
        public void add(CheckItem item) throws IOException {
            PutMethod putMethod = new PutMethod(itemUrl(item));
            putMethod.setRequestEntity(new ByteArrayRequestEntity(new byte[0]));
            int code = client.executeMethod(putMethod);
            LOGGER.info("upload resultcode: {}", code);
        }

        @Override
        public void toggle(CheckItem newItem) throws IOException {
            MoveMethod moveMethod = new MoveMethod(itemUrl(newItem), itemUrl(newItem.toggleChecked()), true);
            int code = client.executeMethod(moveMethod);
            LOGGER.info("move resultcode: {}", code);
        }

        private String itemUrl(CheckItem item) throws MalformedURLException, UnsupportedEncodingException {
            String encodedListName = URLEncoder.encode(listName, Charsets.UTF_8.name()).replace("+", "%20");
            String encodedItem = URLEncoder.encode(item.toString(), Charsets.UTF_8.name()).replace("+", "%20");
            return Joiner.on("/").join(getFullWatchURL(), encodedListName, encodedItem);
        }
    }
}
