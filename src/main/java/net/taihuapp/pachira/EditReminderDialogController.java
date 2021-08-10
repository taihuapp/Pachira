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

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.NumberStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;
import org.controlsfx.control.textfield.TextFields;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class EditReminderDialogController {

    private static final Logger logger = Logger.getLogger(EditReminderDialogController.class);

    private MainModel mainModel;
    private Reminder mReminder;

    @FXML
    private ChoiceBox<Reminder.Type> mTypeChoiceBox;
    @FXML
    private TextField mPayeeTextField;
    @FXML
    private Label amountLabel;
    @FXML
    private HBox fixedAmountHBox;
    @FXML
    private TextField mAmountTextField;
    @FXML
    private HBox estimateHBox;
    @FXML
    private TextField mEstimateNumOccurrenceTextField;
    @FXML
    private Label occurrenceLabel;
    @FXML
    private Label mAccountIDLabel;
    @FXML
    private ComboBox<Integer> mAccountIDComboBox;
    @FXML
    private Label mCategoryIDLabel;
    @FXML
    private ComboBox<Integer> mCategoryIDComboBox;
    private EditTransactionDialogControllerNew.CategoryTransferAccountIDComboBoxWrapper
            mCategoryTransferAccountIDComboBoxWrapper;
    @FXML
    private ComboBox<Integer> mTagIDComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private ChoiceBox<DateSchedule.BaseUnit> mBaseUnitChoiceBox;
    @FXML
    private DatePicker mStartDatePicker;
    @FXML
    private DatePicker mEndDatePicker;
    @FXML
    private TextField mNumPeriodTextField;
    @FXML
    private TextField mAlertDayTextField;
    @FXML
    private RadioButton domRadioButton;
    @FXML
    private RadioButton dowRadioButton;
    @FXML
    private RadioButton fwdRadioButton;
    @FXML
    private RadioButton revRadioButton;
    @FXML
    private Label mDSDescriptionLabel;
    @FXML
    private RadioButton mFixedAmountRadioButton;
    @FXML
    private RadioButton mEstimateAmountRadioButton;

    private Stage getStage() { return (Stage) mPayeeTextField.getScene().getWindow(); }

    @FXML
    private void initialize() {
        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());


        // todo need a ChangeListener for mCountBeforeEndTextField

        mTypeChoiceBox.getItems().setAll(Reminder.Type.values());
    }

    void setMainModel(MainModel mainModel, Reminder reminder) {
        this.mainModel = mainModel;
        this.mReminder = reminder;

        // visibility
        amountLabel.visibleProperty().bind(mTypeChoiceBox.valueProperty()
                .isEqualTo(Reminder.Type.LOAN_PAYMENT).not());
        fixedAmountHBox.visibleProperty().bind(amountLabel.visibleProperty());
        estimateHBox.visibleProperty().bind(fixedAmountHBox.visibleProperty());
        mAmountTextField.visibleProperty().bind(mFixedAmountRadioButton.selectedProperty());
        mEstimateNumOccurrenceTextField.visibleProperty().bind(mEstimateAmountRadioButton.selectedProperty());

        mStartDatePicker.disableProperty().bind(fixedAmountHBox.visibleProperty().not());
        mEndDatePicker.disableProperty().bind(fixedAmountHBox.visibleProperty().not());

        final Callable<Boolean> converter = () -> {
            if (mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT)
                return false;
            switch (mReminder.getDateSchedule().getBaseUnit()) {
                case DAY:
                case WEEK:
                    return false;
                case MONTH:
                case QUARTER:
                case YEAR:
                default:
                    return true;
            }
        };
        domRadioButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty(), mTypeChoiceBox.valueProperty()));
        dowRadioButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty(), mTypeChoiceBox.valueProperty()));
        fwdRadioButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty(), mTypeChoiceBox.valueProperty()));
        revRadioButton.visibleProperty().bind(Bindings.createBooleanBinding(converter,
                mReminder.getDateSchedule().getBaseUnitProperty(), mTypeChoiceBox.valueProperty()));

        mTypeChoiceBox.valueProperty().bindBidirectional(mReminder.getTypeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mReminder.getPayeeProperty());
        TextFields.bindAutoCompletion(mPayeeTextField, mainModel.getPayeeSet());

        mAmountTextField.textProperty().bindBidirectional(mReminder.getAmountProperty(),
                new BigDecimalStringConverter());
        mEstimateNumOccurrenceTextField.textProperty().bindBidirectional(mReminder.getEstimateCountProperty(),
                new IntegerStringConverter());

        mAccountIDComboBox.setConverter(new ConverterUtil.AccountIDConverter(mainModel));
        mAccountIDComboBox.getItems().clear();
        for (Account a : mainModel.getAccountList(account ->
                    account.getType().isGroup(Account.Type.Group.SPENDING) && !account.getHiddenFlag()
                    && !account.getName().equals(MainModel.DELETED_ACCOUNT_NAME)))
            mAccountIDComboBox.getItems().add(a.getID());

        Bindings.bindBidirectional(mAccountIDComboBox.valueProperty(), mReminder.getAccountIDProperty());
        if (mAccountIDComboBox.getSelectionModel().isEmpty())
            mAccountIDComboBox.getSelectionModel().selectFirst(); // if no account selected, default the first.
        mAccountIDComboBox.valueProperty().addListener((obs, ov, nv) -> {
            if (nv == null || mCategoryTransferAccountIDComboBoxWrapper == null)
                return;
            mCategoryTransferAccountIDComboBoxWrapper.setFilter(false, nv);
        });

        mCategoryTransferAccountIDComboBoxWrapper =
                new EditTransactionDialogControllerNew.CategoryTransferAccountIDComboBoxWrapper(mCategoryIDComboBox,
                        mainModel);
        mCategoryIDComboBox.valueProperty().bindBidirectional(reminder.getCategoryIDProperty());

        mTagIDComboBox.setConverter(new ConverterUtil.TagIDConverter(mainModel));
        mTagIDComboBox.getItems().clear();
        for (Tag t : mainModel.getTagList())
            mTagIDComboBox.getItems().add(t.getID());
        Bindings.bindBidirectional(mTagIDComboBox.valueProperty(), mReminder.getTagIDProperty());

        mMemoTextField.textProperty().bindBidirectional(mReminder.getMemoProperty());

        // javafx DatePicker control doesn't aware of the edited value in its TextField
        // this is a work around
        DatePickerUtil.captureEditedDate(mStartDatePicker);
        DatePickerUtil.captureEditedDate(mEndDatePicker);

        // bind properties for DateSchedule fields
        mBaseUnitChoiceBox.valueProperty().bindBidirectional(mReminder.getDateSchedule().getBaseUnitProperty());
        mStartDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getStartDateProperty());
        mEndDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getEndDateProperty());
        mNumPeriodTextField.textProperty().bindBidirectional(mReminder.getDateSchedule().getNumPeriodProperty(),
                new NumberStringConverter("#"));
        mAlertDayTextField.textProperty().bindBidirectional(mReminder.getDateSchedule().getAlertDayProperty(),
                new NumberStringConverter("#"));

        domRadioButton.textProperty().bind(Bindings.createStringBinding(
                () -> "Count days of " + mBaseUnitChoiceBox.valueProperty().get().toString().toLowerCase(),
                mBaseUnitChoiceBox.valueProperty()));


        // we don't have anything to bind mCountBeforeEndTextField, but we have a TextChangeListener for it
        // set in initialization

        domRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsDOMBasedProperty());
        fwdRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsForwardProperty());
        mDSDescriptionLabel.textProperty().bind(mReminder.getDateSchedule().getDescriptionProperty());

        mEstimateAmountRadioButton.setSelected(mReminder.getEstimateCount() > 0);

        mTypeChoiceBox.valueProperty().addListener((obs, o, n) -> {
            if (n == Reminder.Type.LOAN_PAYMENT) {

                try {
                    // the set of negative account numbers of loans already have a reminder
                    Set<Integer> excludeAccountIDSet = mainModel.getReminderList().stream()
                            .filter(r -> r.getType()== Reminder.Type.LOAN_PAYMENT)
                            .map(Reminder::getCategoryID).collect(Collectors.toSet());
                    // the set of loans not included in excludeAccountIDSet.
                    Set<Integer> loanAccountIDSet = mainModel.getLoanList().stream().map(l -> -l.getAccountID())
                            .filter(i -> !excludeAccountIDSet.contains(i)).collect(Collectors.toSet());
                    mCategoryTransferAccountIDComboBoxWrapper.setFilter(loanAccountIDSet::contains);
                    mCategoryIDLabel.setText("Loan Account");
                } catch (DaoException e) {
                    final String msg = "DaoException when get reminder/loan";
                    logger.error(msg, e);
                    DialogUtil.showExceptionDialog(getStage(), "DaoException", msg, e.toString(), e);
                }
            } else {
                mCategoryTransferAccountIDComboBoxWrapper.setFilter(i -> true);
                mCategoryIDLabel.setText("Category");
            }
        });
    }

    @FXML
    private void handleSplit() {
        final BigDecimal netAmount;
        if (mFixedAmountRadioButton.isSelected()) {
            netAmount = mReminder.getType() == Reminder.Type.DEPOSIT ?
                    mReminder.getAmount().negate() : mReminder.getAmount();
        } else {
            netAmount = null;
        }

        try {
            List<SplitTransaction> outputSplitTransactionList = DialogUtil.showSplitTransactionsDialog(mainModel,
                    getStage(), mAccountIDComboBox.getValue(), mReminder.getSplitTransactionList(), "", netAmount);

            if (outputSplitTransactionList != null) {
                // splitTransactionList changed
                mReminder.setSplitTransactionList(outputSplitTransactionList);
            }
        } catch (IOException e) {
            logger.error("ShowSplitTransactionsDialog IOException", e);
            DialogUtil.showExceptionDialog(getStage(),
                    "Exception", "IOException", "showSplitTransactionsDialog IOException", e);
        }
    }

    @FXML
    private void handleSave() {
        // validation
        // todo

        List<SplitTransaction> stList = mReminder.getSplitTransactionList();
        if (!stList.isEmpty()) {
            BigDecimal netAmount = mReminder.getAmount();
            if (mReminder.getType() == Reminder.Type.DEPOSIT)
                netAmount = netAmount.negate();
            for (SplitTransaction st : stList) {
                netAmount = netAmount.add(st.getAmount());
            }
            if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
                DialogUtil.showWarningDialog(getStage(),
                        "Warning", "SplitTransaction amount not match with total amount.",
                        "Please recheck split");
                return;
            }
        }

        if (!mEstimateAmountRadioButton.isSelected())
            mReminder.setEstimateCount(0);

        try {
            if (mReminder.getID() > 0)
                mainModel.updateReminder(mReminder);
            else
                mainModel.insertReminder(mReminder);

            close();
        } catch (DaoException e) {
            final String action = mReminder.getID() > 0 ? "Update" : "Insert";
            logger.error(action + " Reminder error", e);
            DialogUtil.showExceptionDialog(getStage(),
                    "Exception", "DaoException",
                    "DaoException " + e.getErrorCode() + " on " + action + " reminder", e);
        }
    }

    @FXML
    private void handleClose() {
        close();
    }

    private void close() { getStage().close(); }
}
