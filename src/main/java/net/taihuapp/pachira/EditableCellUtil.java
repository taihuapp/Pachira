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

import javafx.scene.control.Cell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.util.StringConverter;

import java.util.function.UnaryOperator;

public class EditableCellUtil {
    static <T> TextField createTextField(final Cell<T> cell,
                                         final StringConverter<T> converter,
                                         final UnaryOperator<TextFormatter.Change> filter) {
        final TextField textField = new TextField();
        final TextFormatter<T> textFormatter = new TextFormatter<>(converter, null, filter);
        textField.setTextFormatter(textFormatter);
        textFormatter.valueProperty().bindBidirectional(cell.itemProperty());

        textField.setOnAction(event -> {
            cell.commitEdit(textFormatter.getValue());
            event.consume();
        });
        textField.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cell.cancelEdit();
                event.consume();
            }
        });
        return textField;
    }

    static <T> void startEdit(final Cell<T> cell, final TextField textField) {
        cell.setText(null);
        cell.setGraphic(textField);

        // Maybe because of the interaction with the textFormatter,
        // selectAll has to be called AFTER requestFocus, this
        // behavior is different from CellUtil.java in jfx
        textField.requestFocus();
        textField.selectAll();
    }

    static <T> void cancelEdit(final Cell<T> cell, final StringConverter<T> converter) {
        cell.setText(getItemText(cell, converter));
        cell.setGraphic(null);
    }

    static <T> void updateItem(final Cell<T> cell, final StringConverter<T> converter, final TextField textField) {
        if (cell.isEmpty()) {
            cell.setText(null);
            cell.setGraphic(null);
        } else {
            if (cell.isEditing()) {
                cell.setText(null);
                cell.setGraphic(textField);
            } else {
                cell.setText(getItemText(cell, converter));
                cell.setGraphic(null);
            }
        }
    }

    private static <T> String getItemText(final Cell<T> cell, final StringConverter<T> converter) {
        return converter == null ?
                (cell.getItem() == null ? "" : cell.getItem().toString()) :
                converter.toString(cell.getItem());
    }
}
