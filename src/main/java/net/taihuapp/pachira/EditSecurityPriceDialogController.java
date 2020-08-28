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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;

public class EditSecurityPriceDialogController {

    private MainApp mMainApp;
    private Security mSecurity;
    private Stage mDialogStage;
    private ObservableList<Price> mPriceList = null;

    @FXML
    private Label mNameLabel;
    @FXML
    private Label mTickerLabel;
    @FXML
    private TableView<Price> mPriceTableView;
    @FXML
    private TableColumn<Price, LocalDate> mPriceDateTableColumn;
    @FXML
    private TableColumn<Price, BigDecimal> mPricePriceTableColumn;
    @FXML
    private DatePicker mNewDateDatePicker;
    @FXML
    private Button mAddButton;
    @FXML
    private Button mDeleteButton;
    @FXML
    private Button mCloseButton;

    void setMainApp(MainApp mainApp, Security security, Stage stage) {
        mMainApp = mainApp;
        mSecurity = security;
        mDialogStage = stage;
        mPriceList = FXCollections.observableList(mainApp.getSecurityPrice(security.getID(), security.getTicker()));

        mNameLabel.setText(security.getName());

        mTickerLabel.setText(security.getTicker());

        mPriceTableView.setItems(mPriceList);
        mPriceTableView.setEditable(true); // make it editable
        mPriceTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue)
                -> mDeleteButton.setDisable(newValue == null));

        mPriceDateTableColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());

        mPricePriceTableColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());
        mPricePriceTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(BigDecimal object) {
                if (object == null)
                    return null;
                return object.toString();
            }

            @Override
            public BigDecimal fromString(String string) {
                BigDecimal result;
                try {
                    result = new BigDecimal(string);
                } catch (NumberFormatException | NullPointerException e){
                    result = null;
                }
                return result;
            }
        }));
        mPricePriceTableColumn.setOnEditCommit(e -> {
            int dbMode;
            if (e.getOldValue() == null)
                dbMode = 1; // new price, insert
            else
                dbMode = 2; // update
            LocalDate date = e.getRowValue().getDate();
            BigDecimal newPrice = e.getNewValue();
            if (newPrice == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning!");
                alert.setHeaderText("Bad Input Price, change discarded!");
                alert.setContentText(""
                        + "Security Name  : " + mSecurity.getName() + "\n"
                        + "Security Ticker: " + mSecurity.getTicker() + "\n"
                        + "Security ID    : " + mSecurity.getID() + "\n"
                        + "Date           : " + date);
                alert.showAndWait();
                return;  // bad price, send user back
            }
            if (newPrice.signum() < 0)
                return; // we don't want to anything with bad input (negative price)
            if (newPrice.signum() == 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText("Do you want to save zero price to database?");
                alert.setContentText(""
                        + "Security Name  : " + mSecurity.getName() + "\n"
                        + "Security Ticker: " + mSecurity.getTicker() + "\n"
                        + "Security ID    : " + mSecurity.getID() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice + "?");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK)
                    return; // don't save, go back
            }
            if (!mMainApp.insertUpdatePriceToDB(mSecurity.getID(), mSecurity.getTicker(), date, newPrice, dbMode)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText("Failed to insert/update price:");
                alert.setContentText("Security Name: " + mSecurity.getName() + "\n"
                        + "Security ID    : " + mSecurity.getID() + "\n"
                        + "Security Ticker: " + mSecurity.getTicker() + "\n"
                        + "Date           : " + date + "\n"
                        + "Price          : " + newPrice);
                alert.showAndWait();
            } else {
                mMainApp.updateAccountBalance(mSecurity);
            }
        });
        // scroll to the last row
        // if size == 0, scrollTo(-1) will do nothing.
        int sizeOfList = mPriceList.size();
        mPriceTableView.scrollTo(sizeOfList-1);

        LocalDate defaultDate;
        if (sizeOfList == 0)
            defaultDate = LocalDate.now();
        else
            defaultDate = mPriceList.get(sizeOfList-1).getDate().plusDays(1);
        switch (defaultDate.getDayOfWeek()) {
            case SATURDAY:
                defaultDate = defaultDate.plusDays(2);
                break;
            case SUNDAY:
                defaultDate = defaultDate.plusDays(1);
                break;
            default:
                // do nothing
                break;
        }
        mNewDateDatePicker.setValue(defaultDate);
    }

    @FXML
    private void handleAdd() {
        Price newPrice = new Price(mNewDateDatePicker.getValue(), null);
        int index = Collections.binarySearch(mPriceList, newPrice, Comparator.comparing(Price::getDate));
        if (index < 0) {
            index = -index - 1;
            mPriceList.add(index, newPrice);
        }
        mPriceTableView.scrollTo(index);
    }

    @FXML
    private void handleDelete() {
        int index = mPriceTableView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && mMainApp.deleteSecurityPriceFromDB(mSecurity.getID(), mSecurity.getTicker(),
                mPriceList.get(index).getDate())) {
            mPriceList.remove(index);
            mMainApp.updateAccountBalance(mSecurity);
        }
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }
}
