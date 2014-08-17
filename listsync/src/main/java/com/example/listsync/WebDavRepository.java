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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class WebDavRepository implements Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebDavRepository.class);

    private final UUID clientId = UUID.randomUUID();

    private final WebDavConfiguration config;
    private HttpClient client;

    public WebDavRepository(WebDavConfiguration config) {
        this.config = config;
        init();
    }

    private void init() {
        client = new HttpClient();
        client.getState().setCredentials(
                new AuthScope(config.getAdress(), config.getPort()),
                new UsernamePasswordCredentials(config.getUsername(), config.getPassword())
        );
    }

    public List<String> download(String file) throws IOException {
        return doDownload(file + ".list");
    }

    private List<String> doDownload(String file) throws IOException {
        LOGGER.info("downloading {}", file);
        GetMethod get = new GetMethod(getFullWatchURL() + file);
        int i = client.executeMethod(get);
        if (i != 200) {
            return new ArrayList<>();
        }
        byte[] bytes = get.getResponseBody();
        LOGGER.info("download complete {}", file);
        return IOUtils.readLines(new ByteArrayInputStream(bytes));
    }

    public Collection<String> listFiles() throws IOException {
        MultiStatusResponse[] responses = doPropFind(getFullWatchURL());
        return Collections2.filter(
                Lists.transform(Arrays.asList(responses), new Function<MultiStatusResponse, String>() {
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
        );
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

    public void upload(String file, List<String> content) throws IOException {
        doUpload(file + ".list", content);
    }

    private void doUpload(String file, List<String> content) throws IOException {
        LOGGER.info("uploading {}", file);
        LOGGER.info("uploading {} -> {}", file, content);
        PutMethod putMethod = new PutMethod(config.getBaseUrl() + config.getWatchpath() + file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IOUtils.writeLines(content, IOUtils.LINE_SEPARATOR_UNIX, output);
        putMethod.setRequestEntity(new ByteArrayRequestEntity(output.toByteArray()));
        int code = client.executeMethod(putMethod);
        LOGGER.info("upload resultcode: {}", code);
    }

    private String getFullWatchURL() {
        return config.getBaseUrl() + config.getWatchpath();
    }

    private static Date getLastModified(MultiStatusResponse multiStatusResponse) {
        DavProperty<?> davProperty = multiStatusResponse.getProperties(200).get(DavConstants.PROPERTY_GETLASTMODIFIED);
        try {
            return new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z").parse(davProperty.getValue().toString());
        } catch (ParseException e) {
            return new Date();
        }
    }

    @Override
    public void lock(String name) {
        try {
            final String lockFileName = getLockFileBaseName(name);
            doUpload(lockFileName + clientId.toString(), Arrays.asList(clientId.toString()));
            waitForLockFileToBecomeTop(lockFileName);
        } catch (IOException e) {
            throw new LockException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockException(e);
        }
    }

    private void waitForLockFileToBecomeTop(final String lockFileName) throws IOException, InterruptedException {
        LOGGER.info("waiting for {} to become top", lockFileName);
        while (true) {
            MultiStatusResponse[] multiStatusResponses = doPropFind(getFullWatchURL());
            List<MultiStatusResponse> lockFiles = Lists.newArrayList(Collections2.filter(Arrays.asList(multiStatusResponses), new Predicate<MultiStatusResponse>() {
                @Override
                public boolean apply(MultiStatusResponse input) {
                    return input.getHref().contains(lockFileName);
                }
            }));
            Collections.sort(lockFiles, new Comparator<MultiStatusResponse>() {
                @Override
                public int compare(MultiStatusResponse o1, MultiStatusResponse o2) {
                    return getLastModified(o1).compareTo(getLastModified(o2));
                }
            });
            removeOldLockFiles(lockFiles);
            if (lockFiles.isEmpty()) {
                throw new IllegalStateException("expected there to be lock-files");
            }
            if (lockFiles.get(0).getHref().contains(clientId.toString())) {
                LOGGER.info("{} now on top", lockFileName);
                return;
            } else {
                Thread.sleep(1000);
            }
        }
    }

    private void removeOldLockFiles(List<MultiStatusResponse> lockFiles) throws IOException {
        Iterator<MultiStatusResponse> iterator = lockFiles.iterator();
        while (iterator.hasNext()) {
            MultiStatusResponse next = iterator.next();
            if (isOlderThan(next, 10000)) {
                client.executeMethod(new DeleteMethod(config.getBaseUrl() + lockFiles.get(0).getHref()));
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private boolean isOlderThan(MultiStatusResponse next, int millis) {
        Date millisAgo = new Date(new Date().getTime() - millis);
        return getLastModified(next).before(millisAgo);
    }

    private String getLockFileBaseName(String name) {
        return "." + name + ".lock";
    }

    @Override
    public void unlock(String name) {
        try {
            DeleteMethod deleteMethod = new DeleteMethod(config.getBaseUrl() + config.getWatchpath() + getLockFileBaseName(name) + clientId.toString());
            client.executeMethod(deleteMethod);
        } catch (IOException e) {
            throw new LockException(e);
        }
    }

}
