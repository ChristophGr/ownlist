/*
 * Copyright Christoph Gritschenberger 2015.
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

import java.io.IOException;
import java.util.List;

public interface ListRepository {

    List<CheckItem> getContent() throws IOException;

    void remove(CheckItem item) throws IOException;

    void add(CheckItem item) throws IOException;

    void toggle(CheckItem item) throws IOException;

}
