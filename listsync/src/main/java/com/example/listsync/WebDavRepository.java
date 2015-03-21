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
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.*;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

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
            System.err.println(e.getErrorCode());
            if (e.getErrorCode() == 404) {
                MkColMethod mkColMethod = new MkColMethod(url);
                client.executeMethod(mkColMethod);
                return new MultiStatusResponse[0];
            }
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
        public synchronized List<CheckItem> getContent() throws IOException {
            MultiStatusResponse[] responses = doPropFind(getFullWatchURL() + "/" + listName);
            List<MultiStatusResponse> sourceList = Arrays.asList(responses);
            Collections.sort(sourceList, new Comparator<MultiStatusResponse>() {
                @Override
                public int compare(MultiStatusResponse o1, MultiStatusResponse o2) {
                    String l1 = (String) o1.getProperties(200).get(DavPropertyName.GETLASTMODIFIED).getValue();
                    String l2 = (String) o2.getProperties(200).get(DavPropertyName.GETLASTMODIFIED).getValue();
                    return ComparisonChain.start().compare(doParseHttpDate(l1), doParseHttpDate(l2)).result();
                }
            });
            return FluentIterable.from(sourceList)
                    .transform(new Function<MultiStatusResponse, String>() {
                        @Override
                        public String apply(MultiStatusResponse input) {
                            return input.getHref()
                                    .replaceFirst("/" + config.getWatchpath() + "/" + listName + "/", "")
                                    .replaceAll("^\\/*", "");
                        }
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
                    .toSortedList(new Comparator<CheckItem>() {
                        @Override
                        public int compare(CheckItem o1, CheckItem o2) {
                            return ComparisonChain.start().compare(o1.isChecked(), o2.isChecked()).result();
                        }
                    });
        }

        private Date doParseHttpDate(String l1) {
            try {
                return DateUtil.parseDate(l1);
            } catch (DateParseException e) {
                return new Date(0);
            }
        }

        /* need to synchronize these. client is not thread-save*/
        @Override
        public synchronized void remove(CheckItem item) throws IOException {
            DeleteMethod deleteMethod = new DeleteMethod(itemUrl(item));
            int code = client.executeMethod(deleteMethod);
            LOGGER.info("upload resultcode: {}", code);
        }

        @Override
        public synchronized void add(CheckItem item) throws IOException {
            PutMethod putMethod = new PutMethod(itemUrl(item));
            putMethod.setRequestEntity(new ByteArrayRequestEntity(new byte[0]));
            int code = client.executeMethod(putMethod);
            LOGGER.info("upload resultcode: {}", code);
        }

        @Override
        public synchronized void toggle(CheckItem newItem) throws IOException {
            String newUrl = itemUrl(newItem.toggleChecked());
            MoveMethod moveMethod = new MoveMethod(itemUrl(newItem), newUrl, true);
            int code = client.executeMethod(moveMethod);
            LOGGER.info("move resultcode: {}", code);
            PutMethod putMethod = new PutMethod(newUrl);
            putMethod.setRequestEntity(new ByteArrayRequestEntity(new byte[0]));
            int code2 = client.executeMethod(putMethod);
            LOGGER.info("update-put resultcode: {}", code2);
        }

        private String itemUrl(CheckItem item) throws MalformedURLException, UnsupportedEncodingException {
            String encodedListName = URLEncoder.encode(listName, Charsets.UTF_8.name()).replace("+", "%20");
            String encodedItem = URLEncoder.encode(item.toString(), Charsets.UTF_8.name()).replace("+", "%20");
            return Joiner.on("/").join(getFullWatchURL(), encodedListName, encodedItem);
        }
    }
}
