/*
 * Copyright (C) 2018-2022.  Guangliang He.  All Rights Reserved.
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TreeTableCell;
import javafx.util.StringConverter;

import java.util.function.UnaryOperator;

/**
 * javafx has an editable tree table cell class called TextFieldTreeTableCell
 * But it does not let you add a TextFormatter to it.
 * This class add this feature.  The code is largely adapted from javafx.
 *
 * @param <S> The type of the TableView generic type
 * @param <T> The type of the elements contained within the TableColumn
 */

public class EditableTreeTableCell<S, T> extends TreeTableCell<S, T> {

    // the text field for the editable cell
    private TextField textField;

    // the converter property
    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(null, "converter");

    // getter and setter for converter
    private ObjectProperty<StringConverter<T>> converterProperty() { return converter; }
    private void setConverter(StringConverter<T> converter) { converterProperty().set(converter); }
    private StringConverter<T> getConverter() { return converterProperty().get(); }

    // the filter property
    private final ObjectProperty<UnaryOperator<TextFormatter.Change>> filter  =
            new SimpleObjectProperty<>(null, "filter");

    // getter and setter for filter
    private ObjectProperty<UnaryOperator<TextFormatter.Change>> filterProperty() { return filter; }
    private void setFilter(UnaryOperator<TextFormatter.Change> filter) { filterProperty().set(filter); }
    private UnaryOperator<TextFormatter.Change> getFilter() { return filterProperty().get(); }

    // constructor
    public EditableTreeTableCell(StringConverter<T> converter, UnaryOperator<TextFormatter.Change> filter) {
        this.getStyleClass().add("editable-tree-table-cell");
        setConverter(converter);
        setFilter(filter);
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing())
            return;

        if (textField == null) {
            // create text field now
            textField = EditableCellUtil.createTextField(this, getConverter(), getFilter());
        }

        EditableCellUtil.startEdit(this, textField);
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        EditableCellUtil.cancelEdit(this, getConverter());
    }

    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        EditableCellUtil.updateItem(this, getConverter(), textField);
    }
}
