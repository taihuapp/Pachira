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
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.util.function.UnaryOperator;

/**
 * javafx has an editable table cell class called TextFieldTableCell
 * But it does not let you add a TextFormatter to it.
 * This class add this feature.  The code is largely adapted from javafx.
 *
 * @param <S> The type of the TableView generic type
 * @param <T> The type of the elements contained within the TableColumn
 */
public class EditableTableCell<S, T> extends TableCell<S, T> {

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

    // constructors
    public EditableTableCell(StringConverter<T> converter, UnaryOperator<TextFormatter.Change> filter) {
        this.getStyleClass().add("editable-table-cell");
        setConverter(converter);
        setFilter(filter);
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing())
            return; // why are we here?

        if (textField == null) {
            // create textField now
            textField = new TextField();
            final TextFormatter<T> textFormatter = new TextFormatter<>(getConverter(), null, getFilter());
            textField.setTextFormatter(textFormatter);
            textFormatter.valueProperty().bindBidirectional(itemProperty());
            textFormatter.valueProperty().addListener((obs, ov, nv) -> commitEdit(textFormatter.getValue()));
        }

        setText(null);
        setGraphic(textField);

        // Maybe because of the interaction with the textFormatter.
        // need to call selectAll after requestFocus.  It is different from
        // CellUtil.java in javafx.
        textField.requestFocus();
        textField.selectAll();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setGraphic(null);
        setText(itemToString());
    }

    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (isEmpty()) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                setText(null);
                setGraphic(textField);
            } else {
                setText(itemToString());
                setGraphic(null);
            }
        }
    }

    // help method
    private String itemToString() {
        return getConverter() == null ?
                getItem() == null ? "" : getItem().toString() :
                getConverter().toString(getItem());
    }
}