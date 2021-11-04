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
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;
import org.controlsfx.control.textfield.TextFields;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EditReminderDialogController {

    private static final Logger logger = Logger.getLogger(EditReminderDialogController.class);

    private MainModel mainModel;
    private Reminder mReminder;

    private boolean updated = false;

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

    // return true if the reminder is updated
    boolean isUpdated() { return updated; }

    void setMainModel(MainModel mainModel, Reminder reminder) {
        this.mainModel = mainModel;
        this.mReminder = reminder;

        // setup visibility/editable/disable update rules and populate choice boxes
        mTypeChoiceBox.getItems().setAll(Reminder.Type.values());
        mTypeChoiceBox.getSelectionModel().selectFirst();
        mTypeChoiceBox.valueProperty().addListener((obs, o, n) ->
                mCategoryTransferAccountIDComboBoxWrapper.setFilter(categoryIDPredicate()));

        mFixedAmountRadioButton.disableProperty().bind(mTypeChoiceBox.valueProperty()
                .isEqualTo(Reminder.Type.LOAN_PAYMENT));

        mAmountTextField.editableProperty().bind(mFixedAmountRadioButton.selectedProperty()
                        .and(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT)));
        estimateHBox.visibleProperty().bind(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT));

        mEstimateNumOccurrenceTextField.editableProperty().bind(mEstimateAmountRadioButton.selectedProperty()
                        .and(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT)));

        mAccountIDComboBox.setConverter(new ConverterUtil.AccountIDConverter(mainModel));
        mAccountIDComboBox.getItems().setAll(mainModel.getAccountList(a -> !a.getHiddenFlag()
                && a.getType().isGroup(Account.Type.Group.SPENDING)
                && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME)).stream()
                .map(Account::getID).collect(Collectors.toList()));
        mAccountIDComboBox.valueProperty().addListener((obs, ov, nv) -> {
            if (nv == null || mCategoryTransferAccountIDComboBoxWrapper == null)
                return;
            mCategoryTransferAccountIDComboBoxWrapper.setFilter(categoryIDPredicate());
        });

        mCategoryIDLabel.textProperty().bind(Bindings.createStringBinding(() ->
                        mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT ? "Loan Account" : "Category",
                mTypeChoiceBox.valueProperty()));
        mCategoryTransferAccountIDComboBoxWrapper = new EditTransactionDialogControllerNew
                .CategoryTransferAccountIDComboBoxWrapper(mCategoryIDComboBox, mainModel);
        mCategoryIDComboBox.valueProperty().addListener((obs, o, n) -> {
            if (n == null)
                return;

            // setup principal/interest payment in split transactions
            if (mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT) {
                try {
                    Optional<Loan> loanOptional = mainModel.getLoan(-mCategoryIDComboBox.getValue());
                    if (loanOptional.isPresent()) {
                        List<Loan.PaymentItem> paymentItemList = loanOptional.get().getPaymentSchedule();
                        mStartDatePicker.setValue(paymentItemList.get(0).getDate());
                        mEndDatePicker.setValue(paymentItemList.get(paymentItemList.size()-1).getDate());
                        BigDecimal principal = BigDecimal.ZERO;
                        BigDecimal interest = BigDecimal.ZERO;
                        if (!paymentItemList.isEmpty()) {
                            principal = paymentItemList.get(0).getPrincipalAmountProperty().get().negate();
                            interest = paymentItemList.get(0).getInterestAmountProperty().get().negate();
                        }
                        List<SplitTransaction> stList = mReminder.getSplitTransactionList();
                        stList.clear();
                        // add the principal payment first
                        stList.add(new SplitTransaction(-1, n, 0, "principal payment",
                                principal, 0));
                        // add the interest payment
                        int cid = mainModel.getCategory(c -> c.getName().equals("Interest Exp")).map(Category::getID)
                                .orElse(0);
                        stList.add(new SplitTransaction(-1, cid, 0, "interest payment",
                                interest, 0));
                    }
                } catch (DaoException e) {
                    final String msg = "DaoException when get loan";
                    logger.error(msg, e);
                    DialogUtil.showExceptionDialog(getStage(), "DaoException", msg, e.toString(), e);
                }
            }
        });

        mTagIDComboBox.setConverter(new ConverterUtil.TagIDConverter(mainModel));
        mTagIDComboBox.getItems().setAll(mainModel.getTagList().stream().map(Tag::getID).collect(Collectors.toList()));

        mStartDatePicker.disableProperty().bind(mTypeChoiceBox.valueProperty().isEqualTo(Reminder.Type.LOAN_PAYMENT));
        // javafx DatePicker control doesn't aware of the edited value in its TextField, this is a work around
        DatePickerUtil.captureEditedDate(mStartDatePicker);

        mNumPeriodTextField.editableProperty().bind(mTypeChoiceBox.valueProperty()
                .isNotEqualTo(Reminder.Type.LOAN_PAYMENT));

        mBaseUnitChoiceBox.disableProperty().bind(mTypeChoiceBox.valueProperty().isEqualTo(Reminder.Type.LOAN_PAYMENT));
        mBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());
        mBaseUnitChoiceBox.getSelectionModel().selectFirst();

        // domRadioButton, dowRadioButton, fwdRadioButton, revRadioButton
        // are only visible for MONTH/QUARTER/YEAR for PAYMENT and DEPOSIT type.
        BooleanBinding booleanBinding = Bindings.createBooleanBinding(() -> {
            if (mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT)
                return false;
            switch (mBaseUnitChoiceBox.getValue()) {
                case DAY:
                case WEEK:
                case HALF_MONTH:
                    return false;
                case MONTH:
                case QUARTER:
                case YEAR:
                    return true;
                default:
                    throw new IllegalArgumentException(mReminder.getDateSchedule().getBaseUnit() + " not implemented");
            }
        }, mTypeChoiceBox.valueProperty(), mBaseUnitChoiceBox.valueProperty());
        domRadioButton.visibleProperty().bind(booleanBinding);
        dowRadioButton.visibleProperty().bind(booleanBinding);
        fwdRadioButton.visibleProperty().bind(booleanBinding);
        revRadioButton.visibleProperty().bind(booleanBinding);

        domRadioButton.textProperty().bind(Bindings.createStringBinding(
                () -> "Count days of " + mBaseUnitChoiceBox.valueProperty().get().toString().toLowerCase(),
                mBaseUnitChoiceBox.valueProperty()));

        mEndDatePicker.disableProperty().bind(mTypeChoiceBox.valueProperty().isEqualTo(Reminder.Type.LOAN_PAYMENT));
        // javafx DatePicker control doesn't aware of the edited value in its TextField, this is a work around
        DatePickerUtil.captureEditedDate(mEndDatePicker);

        // bind the values of the controls to reminder fields
        mTypeChoiceBox.valueProperty().bindBidirectional(mReminder.getTypeProperty());
        mPayeeTextField.textProperty().bindBidirectional(mReminder.getPayeeProperty());
        TextFields.bindAutoCompletion(mPayeeTextField, mainModel.getPayeeSet());

        mAmountTextField.textProperty().bindBidirectional(mReminder.getAmountProperty(),
                new BigDecimalStringConverter());
        mEstimateNumOccurrenceTextField.textProperty().bindBidirectional(mReminder.getEstimateCountProperty(),
                new IntegerStringConverter());

        Bindings.bindBidirectional(mAccountIDComboBox.valueProperty(), mReminder.getAccountIDProperty());
        if (mAccountIDComboBox.getSelectionModel().isEmpty())
            mAccountIDComboBox.getSelectionModel().selectFirst(); // if no account selected, default the first.

        mCategoryIDComboBox.valueProperty().bindBidirectional(reminder.getCategoryIDProperty());

        mTagIDComboBox.valueProperty().bindBidirectional(mReminder.getTagIDProperty());

        mMemoTextField.textProperty().bindBidirectional(mReminder.getMemoProperty());

        // bind properties for DateSchedule fields
        mBaseUnitChoiceBox.valueProperty().bindBidirectional(mReminder.getDateSchedule().getBaseUnitProperty());
        mStartDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getStartDateProperty());
        mEndDatePicker.valueProperty().bindBidirectional(mReminder.getDateSchedule().getEndDateProperty());

        TextFormatter<Integer> numPeriodFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        mNumPeriodTextField.setTextFormatter(numPeriodFormatter);
        numPeriodFormatter.valueProperty().bindBidirectional(mReminder.getDateSchedule().getNumPeriodProperty());

        TextFormatter<Integer> alertDaysFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        mAlertDayTextField.setTextFormatter(alertDaysFormatter);
        alertDaysFormatter.valueProperty().bindBidirectional(mReminder.getAlertDaysProperty());

        domRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsDOMBasedProperty());
        fwdRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsForwardProperty());
        mDSDescriptionLabel.textProperty().bind(mReminder.getDateSchedule().getDescriptionProperty());

        mEstimateAmountRadioButton.setSelected(mReminder.getEstimateCount() > 0);
    }

    private Predicate<Integer> categoryIDPredicate() {
        if (mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT) {
            try {
                // the set of negative loan account numbers already have a reminder
                Set<Integer> excludeAccountIDSet = mainModel.getReminderList().stream()
                        .filter(r -> r.getType() == Reminder.Type.LOAN_PAYMENT)
                        .map(Reminder::getCategoryID).collect(Collectors.toSet());

                // the set negative loan account numbers not included in excludeAccountIDSet.
                Set<Integer> loanAccountIdSet = mainModel.getAccountList(a -> !a.getHiddenFlag()
                        && a.getType() == Account.Type.LOAN).stream().map(a -> -a.getID()).collect(Collectors.toSet());
                loanAccountIdSet.removeAll(excludeAccountIDSet);

                // only show Loan type accounts in loanAccountIDSet. Add i < 0 should improve performance
                return i -> i < 0 && loanAccountIdSet.contains(i);
            } catch (DaoException e) {
                final String msg = "DaoException when get reminder/loan";
                logger.error(msg, e);
                DialogUtil.showExceptionDialog(getStage(), "DaoException", msg, e.toString(), e);
            }
        }
        // show all categories and accounts, except the account in AccountID combobox.
        return i -> i != -mAccountIDComboBox.getValue();
    }

    @FXML
    private void handleSplit() {
        final BigDecimal netAmount;
        if (!mFixedAmountRadioButton.isSelected() || mReminder.getType() == Reminder.Type.LOAN_PAYMENT) {
            netAmount = null;
        } else {
            netAmount = mReminder.getType() == Reminder.Type.DEPOSIT ?
                    mReminder.getAmount().negate() : mReminder.getAmount();
        }

        try {
            final String message;
            final List<SplitTransaction> stList = mReminder.getSplitTransactionList();
            if (mReminder.getType() == Reminder.Type.LOAN_PAYMENT) {
                message = "The 1st split transaction should always be the principal payment. "
                        + "The second should always be the interest payment. "
                        + "The principal and interest payment amount will be automatically updated. "
                        + "Choose the desired category for the interest payment. "
                        + "Add any other payment below.";
            } else {
                message = "";
            }
            List<SplitTransaction> outputSplitTransactionList = DialogUtil.showSplitTransactionsDialog(mainModel,
                    getStage(), mAccountIDComboBox.getValue(), stList, message, netAmount);

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
        if (mReminder.getType() == Reminder.Type.LOAN_PAYMENT) {
            if (stList.size() < 2 || !stList.get(0).getCategoryID().equals(mReminder.getCategoryID())) {
                final String title = "Warning";
                final String header = "Split transaction list not properly setup for loan payment";
                final String content = "Need at least two split transactions, the 1st for principal payment "
                        + "and the 2nd for interest payment.";
                DialogUtil.showWarningDialog(getStage(), title, header, content);
                return;
            } else {
                // amount is always positive
                mReminder.setAmount(stList.stream().map(SplitTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add).abs());
            }
        }
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
            updated = true;
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
