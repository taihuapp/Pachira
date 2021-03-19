/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
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

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AccountListDialogController {

    private static final Logger mLogger = Logger.getLogger(AccountListDialogController.class);

    private MainApp mMainApp = null;
    private Stage mDialogStage = null;
    private final ListChangeListener<Account> mAccountListChangeListener = c -> {
        while (c.next()) {
            if (c.wasUpdated()) {
                for (int i = c.getFrom(); i < c.getTo(); i++) {
                    mMainApp.insertUpdateAccountToDB(c.getList().get(i));
                }
            }
        }
    };

    @FXML
    private ChoiceBox<Account.NewType.Group> mGroupChoiceBox;
    @FXML
    private TableView<Account> mAccountTableView;
    @FXML
    private TableColumn<Account, String> mAccountNameTableColumn;
    @FXML
    private TableColumn<Account, Account.NewType> mAccountTypeTableColumn;
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
        showEditAccountDialog(null, mGroupChoiceBox.getValue());
    }

    @FXML
    private void handleEdit() {
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account != null)
            showEditAccountDialog(account, null);
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

    private void showEditAccountDialog(Account account, Account.NewType.Group g) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditAccountDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(account == null ? "New Account" : "Edit Account");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));
            EditAccountDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller?");
                return;
            }

            controller.setDialogStage(dialogStage);
            controller.setAccount(mMainApp, account, g);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
        }
    }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        class AccountGroupConverter extends StringConverter<Account.NewType.Group> {
            public Account.NewType.Group fromString(String s) {
                try {
                    return Account.NewType.Group.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            public String toString(Account.NewType.Group g) {
                if (g == null)
                    return "All";
                return g.toString();
            }
        }

        // first make sure all account display order is set properly
        for (Account.NewType.Group g : Account.NewType.Group.values()) {
            final List<Account> accountList = new ArrayList<>(mMainApp.getAccountList(g, null, true));
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
                    showEditAccountDialog(row.getItem(), null);
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
        mAccountTypeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getTypeProperty());
        mAccountBalanceTableColumn.setCellValueFactory(cellData -> cellData.getValue().getCurrentBalanceProperty());
        mAccountBalanceTableColumn.setCellFactory(column -> new TableCell<>() {
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


        mGroupChoiceBox.setConverter(new AccountGroupConverter());
        mGroupChoiceBox.getItems().setAll(Account.NewType.Group.values());
        mGroupChoiceBox.getItems().add(null);
        mGroupChoiceBox.getSelectionModel().selectedItemProperty().addListener((ob, o, n) -> {
            // remove old listener if there is any
            mAccountTableView.getItems().removeListener(mAccountListChangeListener);
            // get all account for the given type, exclude deleted account.
            mAccountTableView.setItems(mMainApp.getAccountList(n, null, true));
            mAccountTableView.getItems().addListener(mAccountListChangeListener);
            mEditButton.setDisable(true);
            mMoveUpButton.setDisable(true);
            mMoveDownButton.setDisable(true);
        });
        mGroupChoiceBox.getSelectionModel().selectFirst();
    }

    void close() {
        mAccountTableView.getItems().removeListener(mAccountListChangeListener);
        mDialogStage.close();
    }
}
