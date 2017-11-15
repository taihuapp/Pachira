/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
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

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextFormatter;

import java.util.function.Predicate;

class AutoCompleteComboBoxHelper<T> {
    private final ComboBox<T> mComboBox;
    private final FilteredList<T> mFilteredItems;
    private boolean mKeyEvent;

    AutoCompleteComboBoxHelper(final ComboBox<T> comboBox) {
        mKeyEvent = false;
        mComboBox = comboBox;
        final ObservableList<T> allItems = mComboBox.getItems();
        mFilteredItems = new FilteredList<>(allItems);
        mComboBox.setItems(mFilteredItems);
        mComboBox.setEditable(true);

        // this line suppose to work around for JavaFX needing an enter to commit combobox edit
        mComboBox.getEditor().setTextFormatter(new TextFormatter<>(TextFormatter.IDENTITY_STRING_CONVERTER));

        mComboBox.getEditor().setOnKeyPressed(event -> {
            // once we are in editing mode, existing selection is not longer valid
            // clear out now.  Otherwise, ComboBox will clear it and causes exception
            mComboBox.getSelectionModel().clearSelection();
            mKeyEvent = true;
        });
        mComboBox.getEditor().setOnKeyReleased(event -> mKeyEvent = false);

        mComboBox.getEditor().textProperty().addListener((obs, ov, nv) -> {
            if (!mKeyEvent)
                return; // if change is not due to keyevent, do nothing

            if (nv == null || nv.isEmpty()) {
                mFilteredItems.setPredicate(item -> true);
                return;
            }

            Predicate<? super T> oldPredicate = mFilteredItems.getPredicate();
            // setPredicate sometime (why?) trigs a change in mComboBox.getEditor().textProperty()
            // we don't want to process that.  set mKeyEvent to false here
            mKeyEvent = false;
            mFilteredItems.setPredicate(item ->
                    mComboBox.getConverter().toString(item).toLowerCase().contains(nv.toLowerCase()));
            if (mFilteredItems.size() == 0) {
                // nv is not contained in any items, reject it, put back ov.
                int c = mComboBox.getEditor().getCaretPosition();
                mComboBox.getEditor().setText(ov);
                mComboBox.getEditor().positionCaret(c);
                mFilteredItems.setPredicate(oldPredicate);
            }
        });
    }
}
