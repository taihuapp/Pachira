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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;

public class ReminderTransactionListDialogController {
    private MainApp mMainApp = null;
    private Stage mDialogStage = null;

    @FXML
    private CheckBox mShowCompletedTransactionsCheckBox;
    @FXML
    private TableView<ReminderTransaction> mReminderTransactionTableView;
    @FXML
    private TableColumn<ReminderTransaction, String> mStatusTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, LocalDate> mDueDateTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mAccountTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, Reminder.Type> mTypeTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mPayeeTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, BigDecimal> mAmountTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, Integer> mTagTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, String> mFrequencyTableColumn;

    @FXML
    private Button mEnterButton;
    @FXML
    private Button mSkipButton;
    @FXML
    private Button mEditButton;
    @FXML
    private Button mDeleteButton;

    @FXML
    private void handleEdit() {
        // create a copy of the reminder for editing
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = new Reminder(rt.getReminder());
        // put the next due date as the start date for the reminder to be edited
        reminder.getDateSchedule().setStartDate(rt.getDueDate());
        showEditReminderDialog(new Reminder(reminder));
    }
    @FXML
    private void handleDelete() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = rt.getReminder();
        try {
            if (!mMainApp.setDBSavepoint()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("DB Save Point unexpected set.");
                alert.setContentText("Something is wrong.  Please restart.");
                alert.showAndWait();
                return;
            }
            mMainApp.deleteReminderFromDB(reminder.getID());
            mMainApp.commitDB();
        } catch (SQLException e) {
            try {
                mMainApp.showExceptionDialog("Database Error", "insert or update Reminder failed",
                        MainApp.SQLExceptionToString(e), e);
                mMainApp.rollbackDB();
            } catch (SQLException e1) {
                mMainApp.showExceptionDialog("Database Error",
                        "Failed to rollback reminder database update",
                        MainApp.SQLExceptionToString(e), e);
            }
        } finally {
            try {
                mMainApp.releaseDBSavepoint();
            } catch (SQLException e) {
                mMainApp.showExceptionDialog("Database Error",
                        "set autocommit failed after insert update reminder",
                        MainApp.SQLExceptionToString(e), e);
            }
        }
        mMainApp.initReminderMap();
        mMainApp.initReminderTransactionList();
    }

    @FXML
    private void handleEnter() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = rt.getReminder();
        Transaction.TradeAction ta;
        switch (reminder.getType()) {
            case PAYMENT:
                ta = Transaction.TradeAction.WITHDRAW;
                break;
            case TRANSFER:
                ta = Transaction.TradeAction.XOUT;
                break;
            case DEPOSIT:
            default:
                ta = Transaction.TradeAction.DEPOSIT;
                break;
        }

        int accountID = reminder.getAccountID();
        Transaction transaction = new Transaction(accountID, rt.getDueDate(), ta, reminder.getCategoryID());
        transaction.setAmount(reminder.getAmount());
        transaction.setPayee(reminder.getPayee());
        transaction.setMemo(reminder.getMemo());
        transaction.setSplitTransactionList(reminder.getSplitTransactionList());
        transaction.setTagID(reminder.getTagID());
        int tid = mMainApp.showEditTransactionDialog(mMainApp.getStage(), transaction,
                mMainApp.getAccountList(Account.Type.SPENDING, null, false),
                mMainApp.getAccountByID(reminder.getAccountID()), Collections.singletonList(ta));
        if (tid >= 0) {
            mMainApp.insertReminderTransactions(rt, tid);
            mMainApp.initReminderTransactionList();
        }
    }

    @FXML
    private void handleSkip() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        mMainApp.insertReminderTransactions(rt, 0);
        mMainApp.initReminderTransactionList();
    }

    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mDialogStage = stage;

        // uncheck the showCompletedTransactionCheckBox
        mShowCompletedTransactionsCheckBox.setSelected(false);
        // we need to call handleCheckBox here because setSelect don't trigger an event
        // and won't call the event handler
        handleCheckbox();
    }

    private void showEditReminderDialog(Reminder reminder) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("EditReminderDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Reminder:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mDialogStage);
            dialogStage.setScene(new Scene(loader.load()));

            EditReminderDialogController controller = loader.getController();
            controller.setMainApp(mMainApp, reminder, dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void close() { mDialogStage.close(); }

    @FXML
    private void handleCheckbox() {
        boolean showCompleted = mShowCompletedTransactionsCheckBox.isSelected();
        ObservableList<ReminderTransaction> rtList = mMainApp.getReminderTransactionList(showCompleted);
        mReminderTransactionTableView.setItems(rtList);
        int i;
        for (i = 0; i < rtList.size(); i++) {
            String status = rtList.get(i).getStatus();
            if (status.equals(ReminderTransaction.DUESOON) || status.equals(ReminderTransaction.OVERDUE))
                break;
        }
        mReminderTransactionTableView.scrollTo(i < rtList.size() ? i : rtList.size()-1);
    }

    @FXML
    private void handleClose() { mDialogStage.close(); }

    @FXML
    private void initialize() {
        // setup table columns
        mDueDateTableColumn.setCellValueFactory(cellData -> cellData.getValue().getDueDateProperty());
        mTypeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getTypeProperty());
        mPayeeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getPayeeProperty());
        mAmountTableColumn.setCellValueFactory(cellData -> {
            Transaction t = mMainApp.getTransactionByID(cellData.getValue().getTransactionID());
            if (t != null)
                return t.getAmountProperty();
            if (cellData.getValue().getStatus().equals(ReminderTransaction.SKIPPED))
                return null;
            return cellData.getValue().getReminder().getAmountProperty();
        });
        mAmountTableColumn.setCellFactory(column -> new TableCell<ReminderTransaction, BigDecimal>() {
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

        mStatusTableColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());
        mStatusTableColumn.setCellFactory(column -> new TableCell<ReminderTransaction, String>() {
            @Override
            protected  void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                setGraphic(null);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case ReminderTransaction.OVERDUE:
                            setStyle("-fx-background-color:red");
                            break;
                        case ReminderTransaction.DUESOON:
                            setStyle("-fx-background-color:yellow");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });

        mFrequencyTableColumn.setCellValueFactory(cellData -> {
            DateSchedule ds = cellData.getValue().getReminder().getDateSchedule();
            return new SimpleStringProperty("Every " + ds.getNumPeriod() + " "
                    + ds.getBaseUnit().toString().toLowerCase());
        });

        mTagTableColumn.setCellValueFactory(cd -> cd.getValue().getReminder().getTagIDProperty().asObject());
        mTagTableColumn.setCellFactory(c -> new TableCell<ReminderTransaction, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);

                Tag t;
                if (item == null || empty || (t = mMainApp.getTagByID(item)) == null) {
                    setText(null);
                } else {
                    setText(t.getName());
                }
            }
        });

        mAccountTableColumn.setCellValueFactory(cellData
                -> mMainApp.getAccountByID(cellData.getValue().getReminder().getAccountID()).getNameProperty());

        BooleanBinding visibility = Bindings.createBooleanBinding(() -> {
            ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
            return (rt != null) && !rt.getStatus().equals(ReminderTransaction.COMPLETED)
                    && !rt.getStatus().equals(ReminderTransaction.SKIPPED);
        }, mReminderTransactionTableView.getSelectionModel().getSelectedItems());

        mEditButton.visibleProperty().bind(visibility);
        mDeleteButton.visibleProperty().bind(visibility);
        mEnterButton.visibleProperty().bind(visibility);
        mSkipButton.visibleProperty().bind(visibility);
    }
}