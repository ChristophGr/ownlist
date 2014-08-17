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

public class WebDavConfiguration {

    private final String adress;
    private final int port;
    private final String watchpath;
    private final String username;
    private final String password;
    private final String baseUrl;

    protected WebDavConfiguration(String adress, int port, String watchpath, String username, String password, boolean useSSL) {
        this.adress = adress;
        this.port = port;
        this.watchpath = watchpath;
        this.username = username;
        this.password = password;
        this.baseUrl = "http" + (useSSL ? "s" : "") + "://" + adress + ":" + port + "/";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAdress() {
        return adress;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getWatchpath() {
        return watchpath;
    }

    public static WebDavConfigurationBuilder builder(String adress, String watchpath) {
        return new WebDavConfigurationBuilder(adress, watchpath);
    }

    public static class WebDavConfigurationBuilder {
        private final String adress;
        private Integer port;
        private boolean useSSL = false;
        private String username;
        private String password;
        private final String watchpath;

        private WebDavConfigurationBuilder(String adress, String watchpath) {
            this.adress = adress;
            this.watchpath = watchpath;
        }

        public WebDavConfigurationBuilder usingSSL() {
            this.useSSL = true;
            return this;
        }

        public WebDavConfigurationBuilder usingSSL(boolean ssl) {
            this.useSSL = ssl;
            return this;
        }

        public WebDavConfigurationBuilder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public WebDavConfigurationBuilder customPort(Integer port) {
            this.port = port;
            return this;
        }

        public WebDavConfiguration build() {
            if (port == null) {
                port = useSSL ? 443 : 80;
            }
            return new WebDavConfiguration(adress, port, watchpath, username, password, useSSL);
        }
    }


}
