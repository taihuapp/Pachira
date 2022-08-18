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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Pair;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EditSecurityPriceDialogController {

    private static final Logger logger = Logger.getLogger(EditSecurityPriceDialogController.class);

    private MainModel mainModel;
    private Security security;
    private final ObservableList<Price> priceList = FXCollections.observableArrayList();

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

    void setMainModel(MainModel mainModel, Security security) {

        final Stage stage = (Stage) mPriceTableView.getScene().getWindow();

        this.mainModel = mainModel;
        this.security = security;
        try {
            priceList.setAll(mainModel.getSecurityPriceList(security));
        } catch (DaoException e) {
            final String msg = "Failed to get security prices for " + security;
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }

        mNameLabel.setText(security.getName());

        mTickerLabel.setText(security.getTicker());

        mPriceTableView.setItems(priceList);
        mPriceTableView.setEditable(true); // make it editable
        mPriceTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue)
                -> mDeleteButton.setDisable(newValue == null));

        mPriceDateTableColumn.setCellValueFactory(cellData->cellData.getValue().getDateProperty());

        mPricePriceTableColumn.setCellValueFactory(cellData->cellData.getValue().getPriceProperty());

        mPricePriceTableColumn.setCellFactory(col -> new EditableTableCell<>(
                ConverterUtil.getPriceQuantityStringConverterInstance(),
                c -> RegExUtil.getPriceQuantityInputRegEx().matcher(c.getControlNewText()).matches() ? c : null));
        mPricePriceTableColumn.setOnEditCommit(event -> {
            LocalDate date = event.getRowValue().getDate();
            BigDecimal newPrice = event.getNewValue();
            if (newPrice == null || newPrice.signum() < 0) {
                DialogUtil.showWarningDialog(stage, "Warning!", "Bad input price, change discarded!",
                        "Security Name  : " + this.security.getName() + System.lineSeparator() +
                                "Security Ticker: " + this.security.getTicker() + System.lineSeparator() +
                                "Security ID    : " + this.security.getID() + System.lineSeparator() +
                                "Date           : " + date);
                return;  // bad price, send user back
            }
            if (newPrice.signum() == 0) {
                if (!DialogUtil.showConfirmationDialog(stage, "Confirmation",
                        "Do you want to save zero price?",
                        "Security Name  : " + this.security.getName() + System.lineSeparator() +
                                "Security Ticker: " + this.security.getTicker() + System.lineSeparator() +
                                "Security ID    : " + this.security.getID() + System.lineSeparator() +
                                "Date           : " + date + System.lineSeparator() +
                                "Price          : " + newPrice + "?" )) {
                    // user didn't click OK, return now
                    return;
                }
            }
            try {
                mainModel.mergeSecurityPrices(List.of(new Pair<>(this.security, new Price(date, newPrice))));
                event.getRowValue().setPrice(newPrice);
                mainModel.updateAccountBalance(a -> a.hasSecurity(this.security));
            } catch (DaoException e) {
                final String msg = "Failed to merge price for '" + this.security.getTicker() + "'/("
                        + this.security.getID() + "), " + date + ", " + newPrice;
                logger.error(msg, e);
                DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
            }
        });
        // scroll to the last row
        // if size == 0, scrollTo(-1) will do nothing.
        int sizeOfList = priceList.size();
        mPriceTableView.scrollTo(sizeOfList-1);

        LocalDate defaultDate;
        if (sizeOfList == 0)
            defaultDate = LocalDate.now();
        else
            defaultDate = priceList.get(sizeOfList-1).getDate().plusDays(1);
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
        DatePickerUtil.captureEditedDate(mNewDateDatePicker);
    }

    @FXML
    private void handleAdd() {
        Price newPrice = new Price(mNewDateDatePicker.getValue(), null);
        int index = Collections.binarySearch(priceList, newPrice, Comparator.comparing(Price::getDate));
        if (index < 0) {
            index = -index - 1;
            priceList.add(index, newPrice);
        }
        mPriceTableView.scrollTo(index);
    }

    @FXML
    private void handleDelete() {
        int index = mPriceTableView.getSelectionModel().getSelectedIndex();
        if (index < 0)
            return; // how did we get here

        try {
            mainModel.deleteSecurityPrice(this.security, priceList.get(index).getDate());
            priceList.remove(index);
            mainModel.updateAccountBalance(a -> a.hasSecurity(this.security));
        } catch (DaoException e) {
            final Stage stage = (Stage) mPriceTableView.getScene().getWindow();
            final String msg = "Failed delete security price or update account balance";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleClose() { ((Stage) mPriceTableView.getScene().getWindow()).close(); }
}
