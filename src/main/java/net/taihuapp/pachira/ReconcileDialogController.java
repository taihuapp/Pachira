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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.ParsePosition;

public class ReconcileDialogController {

    private MainApp mMainApp;
    private Stage mStage;
    private ObjectProperty<BigDecimal> mOpeningBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mEndingBalanceProperty = new SimpleObjectProperty<>(null);

    @FXML
    private TextField mOpeningBalanceTextField;
    @FXML
    private TextField mEndingBalanceTextField;
    @FXML
    private DatePicker mEndDatePicker;

    @FXML
    private void handleOK() {
        System.out.println("OK");
    }

    @FXML
    private void handleCancel() {
        mStage.close();
    }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mStage = stage;

        BigDecimal openingBalance = mMainApp.getCurrentAccount().getReconciledBalance();
        mOpeningBalanceTextField.setText(MainApp.DOLLAR_CENT_FORMAT.format(openingBalance));
    }

    @FXML
    private void initialize() {
        StringConverter<BigDecimal> dollarCentStringConverter = new StringConverter<BigDecimal>() {
            @Override
            public String toString(BigDecimal object) {
                if (object == null)
                    return "";
                return MainApp.DOLLAR_CENT_FORMAT.format(object);
            }

            @Override
            public BigDecimal fromString(String string) {
                ParsePosition parsePosition = new ParsePosition(0);
                BigDecimal bd = (BigDecimal) MainApp.DOLLAR_CENT_FORMAT.parse(string, parsePosition);
                if (parsePosition.getIndex() != string.length()) {
                    return null; // parse unsuccessful
                } else {
                    return bd;
                }
            }
        };

        mOpeningBalanceTextField.setTextFormatter(new TextFormatter<>(dollarCentStringConverter));
        mEndingBalanceTextField.setTextFormatter(new TextFormatter<>(dollarCentStringConverter));

        mOpeningBalanceTextField.textProperty().bindBidirectional(mOpeningBalanceProperty, dollarCentStringConverter);
        mEndingBalanceTextField.textProperty().bindBidirectional(mEndingBalanceProperty, dollarCentStringConverter);
    }
}
