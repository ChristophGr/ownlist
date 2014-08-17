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

import com.google.common.base.Objects;

public class CheckItem {
    private String text;
    private boolean checked;

    public CheckItem(String text, boolean checked) {
        this.text = text;
        this.checked = checked;
    }

    public CheckItem(String text) {
        this(text, false);
    }

    public String getText() {
        return text;
    }

    public boolean isChecked() {
        return checked;
    }

    public CheckItem toggleChecked() {
        return new CheckItem(text, !checked);
    }

    public static CheckItem fromString(String itemString) {
        String[] split = itemString.split(" ", 2);
        if (split.length != 2) {
            return null;
            //throw new IllegalArgumentException("could not parse " + itemString);
        }
        return new CheckItem(split[1], split[0].equals("[✔]"));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", checked ? "✔" : "_", text);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CheckItem)) {
            return false;
        }
        CheckItem other = (CheckItem) o;
        return Objects.equal(this.text, other.text)
                && Objects.equal(this.checked, other.checked);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text, checked);
    }

}
