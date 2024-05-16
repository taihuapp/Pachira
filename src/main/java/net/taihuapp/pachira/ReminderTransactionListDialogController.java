/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;

public class ReminderTransactionListDialogController {

    private static final String DUE_SOON = "Due soon";
    private static final String OVERDUE = "Over due";
    private static final String COMPLETED = "Completed";
    private static final String SKIPPED = "Skipped";

    private static final Logger logger = LogManager.getLogger(ReminderTransactionListDialogController.class);

    private ReminderModel reminderModel;

    @FXML
    private CheckBox mShowCompletedTransactionsCheckBox;
    @FXML
    private TableView<ReminderTransaction> mReminderTransactionTableView;
    @FXML
    private TableColumn<ReminderTransaction, String> mStatusTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, LocalDate> mDueDateTableColumn;
    @FXML
    private TableColumn<ReminderTransaction, Boolean> autoColumn;
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
        // edit a reminder
        final ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        final Reminder reminder = reminderModel.getReminder(rt.getReminderId());
        if (reminder != null) {
            // create a copy of the reminder for editing
            final Reminder reminderCopy = new Reminder(reminder);
            // put the next due date as the start date for the reminder to be edited
            reminderCopy.getDateSchedule().setStartDate(rt.getDueDate());
            showEditReminderDialog(reminderCopy);
        } else {
            // we shouldn't be here.  But if for some reason we are here, display an error message
            logger.warn("Cannot get reminder with id = " + rt.getReminderId());
            DialogUtil.showWarningDialog(getStage(), "Edit Reminder", "Failed to retrieve reminder",
                    "Cannot edit reminder");
        }
    }

    @FXML
    private void handleDelete() {
        final int rId = mReminderTransactionTableView.getSelectionModel().getSelectedItem().getReminderId();
        try {
            reminderModel.deleteReminder(rId);
        } catch (DaoException e) {
            logger.error("Delete Reminder failed: " + e.getErrorCode(), e);
            DialogUtil.showExceptionDialog(getStage(),"Database Error",
                    "delete Reminder failed", e.getErrorCode() + "", e);
        }
    }

    /**
     * enter a reminder transaction
     */
    @FXML
    private void handleEnter() {
        final ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();

        try {
            final Transaction transaction = reminderModel.getReminderTransactionTemplate(rt);
            final MainModel mainModel = reminderModel.getMainModel();
            int tid = DialogUtil.showEditTransactionDialog(mainModel, getStage(), transaction,
                    mainModel.getAccountList(a ->
                            (!a.getHiddenFlag() && a.getType().isGroup(Account.Type.Group.SPENDING))),
                    mainModel.getAccount(a -> a.getID() == transaction.getAccountID()).orElse(null),
                    Collections.singletonList(transaction.getTradeAction()));
            if (tid >= 0) {
                rt.setTransactionID(tid);
                reminderModel.insertReminderTransaction(rt);
            }
        } catch (ModelException | DaoException | IOException e) {
            final String msg = e.getClass().getName() + " when opening EditTransactionDialog";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.getMessage(), e);
        }
    }

    @FXML
    private void handleSkip() {
        final ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
        if (reminderModel.getReminder(rt.getReminderId()).getType() == Reminder.Type.LOAN_PAYMENT) {
            if (!DialogUtil.showConfirmationDialog(getStage(), "Skip a loan payment",
                    "Are you sure to skip a loan payment?",
                    "Skipping a loan payment may lose track of loan payment sequences.  "
                    + "Do you want to proceed?"))
                return;
        }

        final int oldTid = rt.getTransactionID();
        rt.setTransactionID(0);
        try {
            reminderModel.insertReminderTransaction(rt);
        } catch (DaoException | ModelException e) {
            rt.setTransactionID(oldTid);
            final String msg = e.getClass().getName() + " exception when insert ReminderTransaction";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleNew() { showEditReminderDialog(new Reminder()); }

    void setMainModel(MainModel mainModel) throws DaoException, ModelException {

        reminderModel = new ReminderModel(mainModel);

        final FilteredList<ReminderTransaction> filteredList = new FilteredList<>(reminderModel.getReminderTransactions(),
                rt -> !rt.isCompletedOrSkipped() || mShowCompletedTransactionsCheckBox.isSelected());

        filteredList.predicateProperty().bind(Bindings.createObjectBinding(() -> rt ->
                mShowCompletedTransactionsCheckBox.isSelected() || !rt.isCompletedOrSkipped(),
                mShowCompletedTransactionsCheckBox.selectedProperty()));

        SortedList<ReminderTransaction> sortedList = new SortedList<>(filteredList,
                Comparator.comparing(ReminderTransaction::getDueDate)
                        .thenComparing(ReminderTransaction::getReminderId));

        mReminderTransactionTableView.setItems(sortedList);

        // uncheck the showCompletedTransactionCheckBox
        mShowCompletedTransactionsCheckBox.setSelected(false);
    }

    // edit a reminder.
    private void showEditReminderDialog(Reminder reminder) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/view/EditReminderDialog.fxml"));

            Stage dialogStage = new Stage();
            dialogStage.setTitle(reminder.getID() > 0 ? "Edit Reminder:" : "Create Reminder");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(getStage());
            dialogStage.setScene(new Scene(loader.load()));

            EditReminderDialogController controller = loader.getController();
            controller.setMainModel(reminderModel, reminder);
            dialogStage.showAndWait();
        } catch (IOException e) {
            final String msg = e.getClass().getName() + " when opening EditReminderDialog";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }

    private Stage getStage() { return (Stage) mReminderTransactionTableView.getScene().getWindow(); }

    void close() { getStage().close(); }

    @FXML
    private void handleCheckbox() {
        // scroll to the first reminder transactions which is not completed nor skipped
        for (int i = 0; i < mReminderTransactionTableView.getItems().size(); i++) {
            if (!mReminderTransactionTableView.getItems().get(i).isCompletedOrSkipped()) {
                mReminderTransactionTableView.scrollTo(i);
                break;
            }
        }
    }

    @FXML
    private void handleClose() { close(); }

    @FXML
    private void initialize() {
        // setup table columns
        mDueDateTableColumn.setCellValueFactory(cellData -> cellData.getValue().getDueDateProperty());
        mTypeTableColumn.setCellValueFactory(cellData ->
                reminderModel.getReminder(cellData.getValue().getReminderId()).getTypeProperty());
        mPayeeTableColumn.setCellValueFactory(cellData ->
                reminderModel.getReminder(cellData.getValue().getReminderId()).getPayeeProperty());
        mPayeeTableColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        mAmountTableColumn.setCellValueFactory(cellData ->
                reminderModel.getReminderTransactionAmountProperty(cellData.getValue()));
        mAmountTableColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText("");
                } else {
                    // format
                    setText(ConverterUtil.getDollarCentFormatInstance().format(item));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });

        mStatusTableColumn.setCellValueFactory(cellData -> Bindings.createStringBinding(() -> {
            final ReminderTransaction rt = cellData.getValue();
            final int id = rt.getTransactionID();
            if (id > 0)
                return COMPLETED;
            if (id == 0)
                return SKIPPED;

            // id < 0, reminder transaction is not executed
            final LocalDate today = MainApp.CURRENT_DATE_PROPERTY.get();
            final LocalDate dueDate = rt.getDueDate();
            if (dueDate.isBefore(today))
                return OVERDUE;
            final Reminder reminder = reminderModel.getReminder(rt.getReminderId());
            final int alertDays = reminder.getAlertDays();
            if (!dueDate.isAfter(today.plusDays(alertDays)))
                return DUE_SOON;

            // nothing special, return an empty string
            return "";
        }, reminderModel.getReminder(cellData.getValue().getReminderId()).getAlertDaysProperty(),
                cellData.getValue().getDueDateProperty(),
                cellData.getValue().getTransactionIDProperty(),
                MainApp.CURRENT_DATE_PROPERTY));
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
                        case OVERDUE:
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
            DateSchedule ds = reminderModel.getReminder(cellData.getValue().getReminderId()).getDateSchedule();
            return new SimpleStringProperty("Every " + ds.getNumPeriod() + " "
                    + ds.getBaseUnit().toString().toLowerCase());
        });

        mTagTableColumn.setCellValueFactory(cd -> reminderModel.getReminder(cd.getValue().getReminderId())
                .getTagIDProperty());
        mTagTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    setText(reminderModel.getMainModel().getTag(tag -> tag.getID() == item).map(Tag::getName)
                            .orElse(null));
                }
            }
        });

        autoColumn.setCellValueFactory(cd -> {
            final ReminderTransaction rt = cd.getValue();
            final Reminder reminder = reminderModel.getReminder(rt.getReminderId());
            return reminder.getIsAutoProperty();
        });
        autoColumn.setCellFactory(c -> new CheckBoxTableCell<>());

        mAccountTableColumn.setCellValueFactory(cellData -> {
                    final ReminderTransaction rt = cellData.getValue();
                    final MainModel mainModel = reminderModel.getMainModel();
                    final Reminder reminder = reminderModel.getReminder(rt.getReminderId());
                    final int accountID = mainModel.getTransaction(t -> t.getID() == rt.getTransactionID())
                            .map(Transaction::getAccountID).orElse(reminder.getAccountID());
                    return mainModel.getAccount(a -> a.getID() == accountID).map(Account::getNameProperty)
                            .orElse(new ReadOnlyStringWrapper(""));
        });
        BooleanBinding visibility = Bindings.createBooleanBinding(() -> {
                    ReminderTransaction rt = mReminderTransactionTableView.getSelectionModel().getSelectedItem();
                    return (rt != null) && !rt.isCompletedOrSkipped();
                }, mReminderTransactionTableView.getSelectionModel().selectedItemProperty(),
                mReminderTransactionTableView.getSelectionModel().getSelectedItems());

        mEditButton.visibleProperty().bind(visibility);
        mDeleteButton.visibleProperty().bind(visibility);
        mEnterButton.visibleProperty().bind(visibility);
        mSkipButton.visibleProperty().bind(visibility);
    }
}
