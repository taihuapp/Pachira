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

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

import java.util.Collection;

public class AutoCompletion {
    public interface AutoCompleteComparator<T> {
        // return true if the object is considered as a match tothe input text
        boolean matches(String text, T object);
    }

    // autoComplete TextField with default comparator (case insensitive containing)
    public static void autoComplete2(TextField textField, Collection<String> suggestions) {
        autoComplete2(textField, suggestions, (typedText, suggestion) ->
                suggestion.toLowerCase().contains(typedText.toLowerCase()));
    }

    // try to use menu item visibility
    public static void autoComplete2(TextField textField, Collection<String> suggestions,
                                     AutoCompleteComparator<String> comparator) {
        ContextMenu contextMenu = new ContextMenu();
        for (String s : suggestions) {
            MenuItem mi = new MenuItem(s);
            mi.setOnAction(eh -> textField.setText(s));
            mi.visibleProperty().bind(Bindings.createBooleanBinding(() ->
                    comparator.matches(textField.textProperty().get(), mi.getText()),
                    textField.textProperty()));
            contextMenu.getItems().add(mi);
        }
        textField.setContextMenu(contextMenu);
        textField.focusedProperty().addListener((obs, ov, nv) -> {
            // show contextMenu whenever focus is on, hide if not
            if (nv) {
                contextMenu.show(textField, Side.BOTTOM, 0, 0);
            } else {
                contextMenu.hide();
            }
        });
    }

    // autoComplete TextField with default comparator (case insensitive containing)
    public static void autoComplete(TextField textField, Collection<String> suggestions) {
        autoComplete(textField, suggestions, (typedText, suggestion) ->
                suggestion.toLowerCase().contains(typedText.toLowerCase()));
    }

    //  Based on the logic of the code at https://stackoverflow.com/a/27384068/3079849
    public static void autoComplete(TextField textField, Collection<String> suggestions,
                                    AutoCompleteComparator<String> comparator) {
        ObservableList<MenuItem> allMenuItems = FXCollections.observableArrayList();
        for (String s : suggestions) {
            MenuItem mi = new MenuItem(s);
            mi.setOnAction(eh -> textField.setText(s));
            allMenuItems.add(mi);
        }
        FilteredList<MenuItem> filteredMenuItems = new FilteredList<>(allMenuItems);
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().setAll(filteredMenuItems);
        textField.setContextMenu(contextMenu);
        textField.focusedProperty().addListener((obs, ov, nv) -> {
            if (nv)
                contextMenu.show(textField, Side.BOTTOM, 0, 0);
            else
                contextMenu.hide();
        });
        textField.addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<>() {
            private boolean mMoveCaretToPos = false;
            private int mCaretPos;

            @Override
            public void handle(KeyEvent keyEvent) {
                switch (keyEvent.getCode()) {
                    case DOWN:
                    case UP:
                        mCaretPos = -1;
                        if (textField.getText() != null) {
                            moveCaret(textField, mCaretPos, textField.getText().length());
                            mMoveCaretToPos = false;
                        }
                        return;
                    case ENTER:
                    case RIGHT:
                    case LEFT:
                    case SHIFT:
                    case CONTROL:
                    case HOME:
                    case END:
                    case TAB:
                        return;
                    default:
                        final String s = textField.getText();
                        final String s1 = s == null ? "" : s;
                        if (s != null) {
                            mMoveCaretToPos = true;
                            mCaretPos = textField.getCaretPosition();
                        }
                        // setup filter
                        filteredMenuItems.setPredicate(t -> comparator.matches(s1, t.getText()));
                        contextMenu.getItems().setAll(filteredMenuItems);
                        textField.setText(s1);
                        if (!mMoveCaretToPos)
                            mCaretPos = -1;
                        moveCaret(textField, mCaretPos, s1.length());
                        mMoveCaretToPos = false;
                        break;
                }
            }
        });
    }

    // default comparator is case insensitive containing
    public static<T> void autoComplete(ComboBox<T> comboBox) {
        autoComplete(comboBox, (typedText, itemToCompare) ->
                comboBox.getConverter().toString(itemToCompare).toLowerCase()
                        .contains(typedText.toLowerCase()));
    }

    //  Based on the logic of the code at https://stackoverflow.com/a/27384068/3079849
    public static<T> void autoComplete(ComboBox<T> comboBox, AutoCompleteComparator<T> comparator) {
        // original items in the comboBox
        FilteredList<T> filteredList = new FilteredList<>(comboBox.getItems());
        comboBox.setItems(filteredList);
        comboBox.setEditable(true);

        comboBox.getEditor().focusedProperty().addListener(obs -> {
            if (comboBox.getSelectionModel().getSelectedIndex() < 0) {
                comboBox.getEditor().setText(null);
            }
        });

        comboBox.addEventHandler(KeyEvent.KEY_PRESSED, eh -> comboBox.hide());
        comboBox.addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<>() {
            private boolean mMoveCaretToPos = false;
            private int mCaretPos;

            @Override
            public void handle(KeyEvent keyEvent) {
                switch (keyEvent.getCode()) {
                    case DOWN:
                        if (!comboBox.isShowing())
                            comboBox.show();
                        // fall through
                    case UP:
                        mCaretPos = -1;
                        if (comboBox.getEditor().getText() != null) {
                            moveCaret(comboBox.getEditor(), mCaretPos, comboBox.getEditor().getText().length());
                            mMoveCaretToPos = false;
                        }
                        return;
                    case ENTER:
                        if ((comboBox.getSelectionModel().getSelectedIndex() < 0)
                                && (!comboBox.getItems().isEmpty()))
                            comboBox.getSelectionModel().selectFirst();
                        return;
                    case RIGHT:
                    case LEFT:
                    case SHIFT:
                    case CONTROL:
                    case HOME:
                    case END:
                    case TAB:
                        return;
                    default:
                        final String s = comboBox.getEditor().getText();
                        final String s1 = s == null ? "" : s;
                        if (s != null) {
                            mMoveCaretToPos = true;
                            mCaretPos = comboBox.getEditor().getCaretPosition();
                        }
                        // setup filter
                        filteredList.setPredicate(t -> comparator.matches(s1, t));
                        comboBox.getEditor().setText(s1);
                        if (!mMoveCaretToPos)
                            mCaretPos = -1;
                        moveCaret(comboBox.getEditor(), mCaretPos, s1.length());
                        mMoveCaretToPos = false;
                        if (!filteredList.isEmpty() && !s1.isEmpty())
                            comboBox.show();
                        break;
                }
            }
        });
    }

    static private void moveCaret(TextField textField, int caretPos, int textLen) {
        textField.positionCaret(caretPos == -1 ? textLen : caretPos);
    }
}
