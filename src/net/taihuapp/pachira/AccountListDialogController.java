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

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AccountListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;
    private ListChangeListener<Account> mAccountListChangeListener = c -> {
        while (c.next()) {
            if (c.wasUpdated()) {
                for (int i = c.getFrom(); i < c.getTo(); i++) {
                    mMainApp.insertUpdateAccountToDB(c.getList().get(i));
                }
            }
        }
    };

    @FXML
    private ChoiceBox<Account.Type> mTypeChoiceBox;
    @FXML
    private TableView<Account> mAccountTableView;
    @FXML
    private TableColumn<Account, String> mAccountNameTableColumn;
    @FXML
    private TableColumn<Account, String> mAccountTypeTableColumn;
    @FXML
    private TableColumn<Account, BigDecimal> mAccountBalanceTableColumn;
    @FXML
    private TableColumn<Account, Boolean> mAccountHiddenFlagTableColumn;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mMoveUpButton;
    @FXML
    private Button mMoveDownButton;

    @FXML
    private void handleNew() {
        mMainApp.showEditAccountDialog(null, mTypeChoiceBox.getValue());
    }

    @FXML
    private void handleEdit() {
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account != null)
            mMainApp.showEditAccountDialog(account, null);
    }

    @FXML
    private void handleMoveUp() {
        int selectedIdx = mAccountTableView.getSelectionModel().getSelectedIndex();
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account == null || selectedIdx <= 0)
            return; // how we got here

        Account accountAbove = mAccountTableView.getItems().get(selectedIdx-1);
        if (accountAbove == null || accountAbove.getType() != account.getType())
            return; // how did this happen?

        account.setDisplayOrder(account.getDisplayOrder()-1);
        accountAbove.setDisplayOrder(account.getDisplayOrder()+1);
        mMainApp.insertUpdateAccountToDB(account);
        mMainApp.insertUpdateAccountToDB(accountAbove);
    }

    @FXML
    private void handleMoveDown() {
        int selectedIdx = mAccountTableView.getSelectionModel().getSelectedIndex();
        int numberOfRows = mAccountTableView.getItems().size();
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account == null || selectedIdx < 0 || selectedIdx >= numberOfRows-1)
            return; // we shouldn't be here

        Account accountBelow = mAccountTableView.getItems().get(selectedIdx+1);
        if (accountBelow == null || accountBelow.getType() != account.getType())
            return;

        account.setDisplayOrder(account.getDisplayOrder()+1);
        accountBelow.setDisplayOrder(accountBelow.getDisplayOrder()-1);

        // these two accounts are in MainApp.mAccountList, no need to update
        mMainApp.insertUpdateAccountToDB(account);
        mMainApp.insertUpdateAccountToDB(accountBelow);
    }

    @FXML
    private void handleUnhide() {
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account == null)
            return;  // is this necessary?
        // working on the account in mMainApp.mAccountList
        account.setHiddenFlag(!account.getHiddenFlag());
        mMainApp.insertUpdateAccountToDB(account);
    }

    @FXML
    private void handleClose() { close(); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        class AccountTypeConverter extends StringConverter<Account.Type> {
            public Account.Type fromString(String s) {
                Account.Type t;
                try {
                    t = Account.Type.valueOf(s);
                } catch (IllegalArgumentException e) {
                    t = null;
                }
                return t;
            }
            public String toString(Account.Type t) {
                if (t == null)
                    return "All";
                return t.toString();
            }
        }

        // first make sure all account display order is set properly
        for (Account.Type t : Account.Type.values()) {
            final List<Account> accountList = new ArrayList<>(mMainApp.getAccountList(t, null, true));
            for (int i = 0; i < accountList.size(); i++) {
                Account a = accountList.get(i);
                if (a.getDisplayOrder() != i) {
                    a.setDisplayOrder(i);
                    mMainApp.insertUpdateAccountToDB(a); // save to DB
                }
            }
        }

        mAccountTableView.setEditable(true);
        // double click to edit the account
        mAccountTableView.setRowFactory(tv -> {
            TableRow<Account> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if ((event.getClickCount() == 2) && (!row.isEmpty())) {
                    mMainApp.showEditAccountDialog(row.getItem(), null);
                }
            });
            return row;
        });

        // add a selection change listener to the table
        mAccountTableView.getSelectionModel().selectedIndexProperty().addListener((ob, ov, nv) -> {
            // is it possible nv is null?
            mEditButton.setDisable(nv == null); // if we have a selection, edit and unhide should be enabled

            // disable move up button if
            // 1) nv is null or at the top (selectedIdx <= 0)
            // 2) nv is with different type from the account above it
            int numberOfRows = mAccountTableView.getItems().size();
            Account newAccount = (nv == null || nv.intValue() < 0 || nv.intValue() >= numberOfRows) ?
                    null : mAccountTableView.getItems().get(nv.intValue());
            mMoveUpButton.setDisable((nv == null) || (nv.intValue() == 0) || (newAccount == null)
                    || (newAccount.getType() != mAccountTableView.getItems().get(nv.intValue() - 1).getType()));

            // disable move down button if
            // 1) nv is null or at the bottom (selectedIdx < 0 || selectedIdx > numberOfRows)
            // 2) nv is with different type from the account below it
            mMoveDownButton.setDisable((nv == null) || (nv.intValue() == (numberOfRows-1))  || (newAccount == null)
                    || (newAccount.getType() != mAccountTableView.getItems().get(nv.intValue()+1).getType()));
        });

        mAccountNameTableColumn.setCellValueFactory(cellData -> cellData.getValue().getNameProperty());
        mAccountTypeTableColumn.setCellValueFactory(cellData
                -> new SimpleStringProperty(cellData.getValue().getType().toString()));
        mAccountBalanceTableColumn.setCellValueFactory(cellData -> cellData.getValue().getCurrentBalanceProperty());
        mAccountBalanceTableColumn.setCellFactory(column -> new TableCell<Account, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    // format
                    setText(MainApp.DOLLAR_CENT_FORMAT.format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        mAccountHiddenFlagTableColumn.setCellValueFactory(cellData -> cellData.getValue().getHiddenFlagProperty());
        mAccountHiddenFlagTableColumn.setCellFactory(c -> new CheckBoxTableCell<>());


        mTypeChoiceBox.setConverter(new AccountTypeConverter());
        mTypeChoiceBox.getItems().setAll(Account.Type.values());
        mTypeChoiceBox.getItems().add(null);
        mTypeChoiceBox.getSelectionModel().selectedItemProperty().addListener((ob, o, n) -> {
            // remove old listener if there is any
            mAccountTableView.getItems().removeListener(mAccountListChangeListener);
            // get all account for the given type, exclude deleted account.
            mAccountTableView.setItems(mMainApp.getAccountList(n, null, true));
            mAccountTableView.getItems().addListener(mAccountListChangeListener);
            mEditButton.setDisable(true);
            mMoveUpButton.setDisable(true);
            mMoveDownButton.setDisable(true);
        });
        mTypeChoiceBox.getSelectionModel().selectFirst();
    }

    void close() {
        mAccountTableView.getItems().removeListener(mAccountListChangeListener);
        mDialogStage.close();
    }
}
