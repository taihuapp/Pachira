/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

import java.util.Collection;

class AutoCompleteTextFieldHelper {
    private final TextField mTextField;
    private final FilteredList<MenuItem> mFilteredItems;
    private boolean mKeyEvent;

    AutoCompleteTextFieldHelper(final TextField textField, Collection<String> items) {
        mTextField = textField;
        ObservableList<MenuItem> allItems = FXCollections.observableArrayList();
        for (String s : items) {
            MenuItem mi = new MenuItem(s);
            mi.setOnAction(eh -> mTextField.setText(s));
            allItems.add(mi);
        }
        mFilteredItems = new FilteredList<>(allItems);
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().setAll(mFilteredItems);
        mTextField.setContextMenu(contextMenu);

        mTextField.setOnKeyPressed(e -> mKeyEvent = true);
        mTextField.setOnKeyReleased(e -> mKeyEvent = false);
        mTextField.textProperty().addListener((obs, ov, nv) -> {
            if (!mKeyEvent)
                return; // if change is not due to keyevent, do nothing

            if (nv == null || nv.isEmpty()) {
                mFilteredItems.setPredicate(item -> true);
                return;
            }

            mFilteredItems.setPredicate(item -> item.getText().toLowerCase().contains(nv.toLowerCase()));
            mTextField.getContextMenu().getItems().setAll(mFilteredItems);
        });
    }
}
