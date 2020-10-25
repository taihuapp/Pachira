/*
 * Copyright (C) 2018-2020.  Guangliang He.  All Rights Reserved.
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

import javafx.scene.control.DatePicker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.time.format.DateTimeParseException;

public class DatePickerUtil {
    // The edited date in a DatePicker obj is not captured when the datePicker obj
    // goes out of focus.
    public static void captureEditedDate(final DatePicker datePicker) {
        datePicker.getEditor().focusedProperty().addListener((obj, wasFocused, isFocused) -> {
            if (!isFocused) // goes out of focus, save the edited date
                captureEditedDateCore(datePicker);
        });
        datePicker.addEventFilter(KeyEvent.KEY_PRESSED, eh -> {
            if (eh.getCode() == KeyCode.ENTER) // user hit the enter key
                captureEditedDateCore(datePicker);
        });
    }

    // method used by captureEditedDate
    private static void captureEditedDateCore(final DatePicker datePicker) {
        try {
            datePicker.setValue(datePicker.getConverter().fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException e) {
            datePicker.getEditor().setText(datePicker.getConverter().toString(datePicker.getValue()));
        }
    }
}
