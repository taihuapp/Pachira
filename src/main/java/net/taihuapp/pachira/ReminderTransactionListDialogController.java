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

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static net.taihuapp.pachira.ReminderTransaction.*;

public class ReminderTransactionListDialogController {

    private static final Logger logger = Logger.getLogger(ReminderTransactionListDialogController.class);

    private MainModel mainModel;
    private final ObservableList<ReminderTransaction> reminderTransactions = FXCollections.observableArrayList(
            rt -> new Observable[]{ rt.getStatusProperty() }
    );

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
        Stage stage = (Stage) mReminderTransactionTableView.getScene().getWindow();
        ReminderTransaction reminderTransaction = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = reminderTransaction.getReminder();
        try {
            mainModel.deleteReminder(reminder);
        } catch (DaoException e) {
            logger.error("Delete Reminder failed: " + e.getErrorCode(), e);
            DialogUtil.showExceptionDialog(stage,"Database Error",
                    "delete Reminder failed", e.getErrorCode() + "", e);
        }

        List<ReminderTransaction> toBeRemoved = new FilteredList<>(reminderTransactions,
                rt -> rt.getReminder().getID() == reminder.getID());
        reminderTransactions.removeAll(toBeRemoved);
    }

    @FXML
    private void handleEnter() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        Reminder reminder = rt.getReminder();
        Transaction.TradeAction ta = reminder.getType() == Reminder.Type.PAYMENT ?
                Transaction.TradeAction.WITHDRAW : Transaction.TradeAction.DEPOSIT;

        int accountID = reminder.getAccountID();
        Transaction transaction = new Transaction(accountID, rt.getDueDate(), ta, reminder.getCategoryID());
        transaction.setAmount(reminder.getAmount());
        transaction.setPayee(reminder.getPayee());
        transaction.setMemo(reminder.getMemo());
        final List<SplitTransaction> stList = new ArrayList<>();
        for (SplitTransaction st : reminder.getSplitTransactionList()) {
            final SplitTransaction stCopy = new SplitTransaction(st);
            stCopy.setID(0);
            stList.add(stCopy);
        }
        transaction.setSplitTransactionList(stList);
        transaction.setTagID(reminder.getTagID());
        Stage stage = (Stage) mReminderTransactionTableView.getScene().getWindow();
        try {
            int tid = DialogUtil.showEditTransactionDialog(mainModel, stage, transaction,
                    mainModel.getAccountList(a -> a.getType().isGroup(Account.Type.Group.SPENDING)),
                    mainModel.getAccount(a -> a.getID() == reminder.getAccountID()).orElse(null),
                    Collections.singletonList(ta));
            if (tid >= 0) {
                rt.setTransactionID(tid);
                mainModel.insertReminderTransaction(rt);
                reminderTransactions.setAll(mainModel.getReminderTransactionList());
            }
        } catch (DaoException | IOException e) {
            final String msg = e.getClass().getName() + " when opening EditTransactionDialog";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.getMessage(), e);
        }
    }

    @FXML
    private void handleSkip() {
        ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        rt.setTransactionID(0);
        try {
            mainModel.insertReminderTransaction(rt);
            reminderTransactions.setAll(mainModel.getReminderTransactionList());
        } catch (DaoException e) {
            logger.error("Insert ReminderTransaction failed: " + e.getErrorCode(), e);
            Stage stage = (Stage) mReminderTransactionTableView.getScene().getWindow();
            DialogUtil.showExceptionDialog(stage, "Exception", "Unable to insert ReminderTransaction",
                    e.getErrorCode() + "", e);
        }
    }

    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainModel(MainModel mainModel) {
        this.mainModel = mainModel;

        // uncheck the showCompletedTransactionCheckBox
        mShowCompletedTransactionsCheckBox.setSelected(false);

        // get the list of reminder transactions
        try {
            // skip those reminder transactions without a legit reminder (probably deleted before).
            reminderTransactions.setAll(mainModel.getReminderTransactionList()
                    .stream().filter(rt -> rt.getReminder() != null).collect(Collectors.toList()));

            // we need to call handleCheckBox here because setSelect don't trigger an event
            // and won't call the event handler
            handleCheckbox();
        } catch (DaoException e) {
            Stage stage = (Stage) mReminderTransactionTableView.getScene().getWindow();
            DialogUtil.showExceptionDialog(stage, "Exception", "Unable to get ReminderTransaction list",
                    e.getErrorCode() + "", e);
        }
    }

    private void showEditReminderDialog(Reminder reminder) {
        Stage stage = (Stage) mReminderTransactionTableView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditReminderDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Reminder:");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setScene(new Scene(loader.load()));

            EditReminderDialogController controller = loader.getController();
            controller.setMainModel(mainModel, reminder);
            dialogStage.showAndWait();
            reminderTransactions.setAll(mainModel.getReminderTransactionList());
        } catch (IOException | DaoException e) {
            final String msg = e.getClass().getName() + " when opening EditReminderDialog";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(stage, e.getClass().getName(), msg, e.toString(), e);
        }
    }

    void close() { ((Stage) mReminderTransactionTableView.getScene().getWindow()).close(); }

    @FXML
    private void handleCheckbox() {
        FilteredList<ReminderTransaction> filteredList = new FilteredList<>(reminderTransactions);
        filteredList.predicateProperty().bind(Bindings.createObjectBinding(()-> rt ->
                        (rt.getReminder() != null) && (mShowCompletedTransactionsCheckBox.isSelected()
                                || !(rt.getStatus().equals(SKIPPED) || rt.getStatus().equals(COMPLETED))),
                mShowCompletedTransactionsCheckBox.selectedProperty()));
        SortedList<ReminderTransaction> sortedList = new SortedList<>(filteredList,
                Comparator.comparing(ReminderTransaction::getDueDate));
        int cnt = 0;
        long minDistance = -1;
        for (int i = 0; i < sortedList.size(); i++) {
            long distance = Math.abs(DAYS.between(LocalDate.now(), sortedList.get(i).getDueDate()));
            if (minDistance < 0 || distance < minDistance) {
                cnt = i;
                minDistance = distance;
            }
        }
        mReminderTransactionTableView.setItems(sortedList);
        mReminderTransactionTableView.scrollTo(cnt);
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        // setup table columns
        mDueDateTableColumn.setCellValueFactory(cellData -> cellData.getValue().getDueDateProperty());
        mTypeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getTypeProperty());
        mPayeeTableColumn.setCellValueFactory(cellData -> cellData.getValue().getReminder().getPayeeProperty());
        mAmountTableColumn.setCellValueFactory(cellData -> {
            final int tid = cellData.getValue().getTransactionID();
            if (tid < 0)
                return cellData.getValue().getReminder().getAmountProperty();
            else
                return mainModel.getTransaction(t -> t.getID() == cellData.getValue().getTransactionID())
                        .map(Transaction::getAmountProperty).orElse(null);
        });
        mAmountTableColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    // format
                    setText(MainModel.DOLLAR_CENT_FORMAT.format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });

        mStatusTableColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());
        mStatusTableColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
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
                        case DUE_SOON:
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

        mTagTableColumn.setCellValueFactory(cd -> cd.getValue().getReminder().getTagIDProperty());
        mTagTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(mainModel.getTag(tag -> tag.getID() == item).map(Tag::getName).orElse(null));
                }
            }
        });

        mAccountTableColumn.setCellValueFactory(cellData ->
                mainModel.getAccount(account -> account.getID() == cellData.getValue().getReminder().getAccountID())
                .map(Account::getNameProperty).orElse(new ReadOnlyStringWrapper("")));

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
