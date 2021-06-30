/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira;

import javafx.util.StringConverter;

public class ConverterUtil {
    public static class TagIDConverter extends StringConverter<Integer> {
        private final MainModel mainModel;

        public TagIDConverter(MainModel mainModel) { this.mainModel = mainModel; }

        @Override
        public String toString(Integer tid) {
            return mainModel.getTag(tag -> tag.getID() == tid).map(Tag::getName).orElse("");
        }

        @Override
        public Integer fromString(String tagName) {
            return mainModel.getTag(tag -> tag.getName().equals(tagName)).map(Tag::getID).orElse(0);
        }
    }

    public static class AccountConverter extends StringConverter<Account> {
        private final MainModel mainModel;

        public AccountConverter(MainModel mainModel) { this.mainModel = mainModel; }

        @Override
        public String toString(Account account) { return account == null ? "" : account.getName(); }

        @Override
        public Account fromString(String s) {
            return mainModel.getAccount(a -> a.getName().equals(s)).orElse(null);
        }
    }

    public static class AccountIDConverter extends StringConverter<Integer> {
        private final MainModel mainModel;

        public AccountIDConverter(MainModel mainModel) { this.mainModel = mainModel; }

        @Override
        public String toString(Integer aid) {
            return mainModel.getAccount(a -> a.getID() == aid).map(Account::getName).orElse("");
        }

        @Override
        public Integer fromString(String accountName) {
            return mainModel.getAccount(a -> a.getName().equals(accountName)).map(Account::getID).orElse(0);
        }
    }

    public static class CategoryIDConverter extends StringConverter<Integer> {
        private final MainModel mainModel;

        public CategoryIDConverter(MainModel mainModel) { this.mainModel = mainModel; }

        @Override
        public String toString(Integer id) {
            if (id == null || id == 0)
                return "";

            if (id > 0)
                return mainModel.getCategory(c -> c.getID() == id).map(Category::getName).orElse("");

            return mainModel.getAccount(a -> a.getID() == -id).map(a -> "[" + a.getName() + "]").orElse("");
        }

        @Override
        public Integer fromString(String name) {
            if (name == null || name.isEmpty())
                return 0;

            if (name.startsWith("[") && name.endsWith("]"))
                return mainModel.getAccount(account -> ("[" + account.getName() + "]").equals(name))
                                .map(account -> -account.getID()).orElse(0);

            return mainModel.getCategory(category -> category.getName().equals(name))
                    .map(Category::getID).orElse(0);
        }
    }

    public static class SecurityConverter extends StringConverter<Security> {

        private final MainModel mainModel;

        public SecurityConverter(MainModel mainModel) { this.mainModel = mainModel; }

        @Override
        public String toString(Security security) {
            if (security == null)
                return "";
            return security.getName();
        }

        @Override
        public Security fromString(String name) {
            if (name == null || name.isEmpty())
                return null;
            return mainModel.getSecurity(security -> security.getName().equals(name)).orElse(null);
        }
    }
}
