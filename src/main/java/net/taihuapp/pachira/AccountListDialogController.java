/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;

public class AccountListDialogController {

    private static final Logger mLogger = LogManager.getLogger(AccountListDialogController.class);

    private MainModel mainModel;

    @FXML
    private ChoiceBox<Account.Type.Group> mGroupChoiceBox;
    @FXML
    private TableView<Account> mAccountTableView;
    @FXML
    private TableColumn<Account, String> mAccountNameTableColumn;
    @FXML
    private TableColumn<Account, Account.Type> mAccountTypeTableColumn;
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
    private Button hideButton;

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
        Account accountAbove = mAccountTableView.getItems().get(selectedIdx-1);
        swapAccountDisplayOrder(account, accountAbove);
    }

    @FXML
    private void handleMoveDown() {
        int selectedIdx = mAccountTableView.getSelectionModel().getSelectedIndex();
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        Account accountBelow = mAccountTableView.getItems().get(selectedIdx+1);
        swapAccountDisplayOrder(account, accountBelow);
    }

    private void swapAccountDisplayOrder(Account a1, Account a2) {
        try {
            mainModel.swapAccountDisplayOrder(a1, a2);
        } catch (DaoException e) {
            final String msg = e.getErrorCode() + "DaoException on SwapAccountDisplayOrder with accounts "
                    + a1.getName() + "/" + a2.getName();
            mLogger.error(msg, e);
            Stage stage = (Stage) mAccountTableView.getScene().getWindow();
            DialogUtil.showExceptionDialog(stage, "DaoException", msg, e.toString(), e);
        }
    }

    @FXML
    private void handleUnhide() {
        Account account = mAccountTableView.getSelectionModel().getSelectedItem();
        if (account == null)
            return;  // is this necessary?
        // working on the account in mMainApp.mAccountList
        account.setHiddenFlag(!account.getHiddenFlag());
        try {
            mainModel.insertUpdateAccount(account);
        } catch (ModelException e) {
            // first put back
            account.setHiddenFlag(!account.getHiddenFlag());
            Stage stage = (Stage) mAccountTableView.getScene().getWindow();
            final String msg = e.getErrorCode() + " ModelException when hide/unhide account " + account.getName();
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, "ModelException", msg, e.toString(), e);
        }
    }

    @FXML
    private void handleClose() { close(); }

    private void showEditAccountDialog(Account account, Account.Type.Group g) {
        Stage stage = (Stage) mAccountTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditAccountDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(account == null ? "New Account" : "Edit Account");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));
            EditAccountDialogController controller = loader.getController();
            if (controller == null) {
                mLogger.error("Null controller?");
                return;
            }

            controller.setAccount(mainModel, account, g);
            dialogStage.showAndWait();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            DialogUtil.showExceptionDialog(stage, "IOException", "showEditAccountDialog IO Exception",
                    e.toString(), e);
        }
    }

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;

        class AccountGroupConverter extends StringConverter<Account.Type.Group> {
            public Account.Type.Group fromString(String s) {
                try {
                    return Account.Type.Group.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            public String toString(Account.Type.Group g) {
                if (g == null)
                    return "All";
                return g.toString();
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

        hideButton.textProperty().bind(Bindings.createStringBinding(() -> {
            final Account account = mAccountTableView.getSelectionModel().selectedItemProperty().get();
            if (account != null && account.getHiddenFlag())
                return "Unhide";
            return "Hide";
        }, mAccountTableView.getSelectionModel().selectedItemProperty()));
        hideButton.disableProperty().bind(mAccountTableView.getSelectionModel().selectedItemProperty().isNull());

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
                    // format, balances always have scale of 2.
                    setText(ConverterUtil.getDollarCentFormatInstance().format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        mAccountHiddenFlagTableColumn.setCellValueFactory(cellData -> cellData.getValue().getHiddenFlagProperty());
        mAccountHiddenFlagTableColumn.setCellFactory(c -> new CheckBoxTableCell<>());


        mGroupChoiceBox.setConverter(new AccountGroupConverter());
        mGroupChoiceBox.getItems().setAll(Account.Type.Group.values());
        mGroupChoiceBox.getItems().add(null);
        mGroupChoiceBox.getSelectionModel().selectedItemProperty().addListener((ob, o, n) -> {
            // get all account for the given group, exclude deleted account.
            mAccountTableView.setItems(new SortedList<>(mainModel.getAccountList(a -> n == null ||
                            (a.getType().isGroup(n) && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME))),
                    Comparator.comparing(Account::getType).thenComparing(Account::getDisplayOrder)));
            mEditButton.setDisable(true);
            mMoveUpButton.setDisable(true);
            mMoveDownButton.setDisable(true);
        });
        mGroupChoiceBox.getSelectionModel().selectFirst();
    }

    void close() {
        ((Stage) mAccountTableView.getScene().getWindow()).close();
    }
}
