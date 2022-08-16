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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;
import org.controlsfx.control.textfield.TextFields;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EditReminderDialogController {

    private static final Logger logger = Logger.getLogger(EditReminderDialogController.class);

    private ReminderModel reminderModel;
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

    void setMainModel(ReminderModel reminderModel, Reminder reminder) {
        this.reminderModel = reminderModel;
        this.mReminder = reminder;

        if (reminder.getType() == Reminder.Type.LOAN_PAYMENT) {
            // we want user to select loan account in the main windows, even though the info is stored
            // in the first split transaction.  Extra care is taken to maintain the consistency of the two fields
            mReminder.getCategoryIDProperty().set(mReminder.getSplitTransactionList().get(0).getCategoryID());
        }

        // setup visibility/editable/disable update rules and populate choice boxes
        mTypeChoiceBox.getItems().setAll(Reminder.Type.values());
        mTypeChoiceBox.getSelectionModel().selectFirst();
        mTypeChoiceBox.valueProperty().addListener((obs, o, n) ->
                mCategoryTransferAccountIDComboBoxWrapper.setFilter(categoryIDPredicate()));

        final Currency currency = Currency.getInstance("USD"); // hard code usd for now
        final TextFormatter<BigDecimal> amountTextFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency), null,
                c -> RegExUtil.getCurrencyInputRegEx(currency).matcher(c.getControlNewText()).matches() ? c : null);
        mAmountTextField.setTextFormatter(amountTextFormatter);
        amountTextFormatter.valueProperty().bindBidirectional(mReminder.getAmountProperty());
        mAmountTextField.editableProperty().bind(mFixedAmountRadioButton.selectedProperty());

        final TextFormatter<Integer> estimateNumOccurrenceTextFormatter = new TextFormatter<>(
                new IntegerStringConverter(), null,
                c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        mEstimateNumOccurrenceTextField.setTextFormatter(estimateNumOccurrenceTextFormatter);
        estimateNumOccurrenceTextFormatter.valueProperty().bindBidirectional(mReminder.getEstimateCountProperty());
        mEstimateNumOccurrenceTextField.editableProperty().bind(mEstimateAmountRadioButton.selectedProperty());

        mAmountTextField.visibleProperty().bind(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT));
        fixedAmountHBox.visibleProperty().bind(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT));
        estimateHBox.visibleProperty().bind(mTypeChoiceBox.valueProperty().isNotEqualTo(Reminder.Type.LOAN_PAYMENT));

        final MainModel mainModel = reminderModel.getMainModel();
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
                    final int loanAccountId = -mCategoryIDComboBox.getValue();
                    final Loan loan = mainModel.getLoan(loanAccountId)
                            .orElseThrow(() -> new ModelException(ModelException.ErrorCode.LOAN_NOT_FOUND,
                                    "Missing loan with account id = " + loanAccountId, null));
                    List<Loan.PaymentItem> paymentItemList = loan.getPaymentSchedule().stream()
                            .filter(pi -> !pi.getIsPaidProperty().get()).collect(Collectors.toList());
                    if (paymentItemList.isEmpty()) {
                        throw new ModelException(ModelException.ErrorCode.LOAN_PAYMENT_NOT_FOUND,
                                "No more payment scheduled", null);
                    }
                    mStartDatePicker.setValue(paymentItemList.get(0).getDate());
                    mEndDatePicker.setValue(paymentItemList.get(paymentItemList.size() - 1).getDate());
                    // set up the amounts for transactions in mReminder
                    List<SplitTransaction> stList = mReminder.getSplitTransactionList();
                    stList.clear();
                    // add the principal payment first
                    stList.add(new SplitTransaction(-1, n, 0, "principal payment",
                            paymentItemList.get(0).getPrincipalAmount().negate(), 0));
                    // add the interest payment
                    int cid = mainModel.getCategory(c -> c.getName().equals("Interest Exp"))
                            .map(Category::getID).orElse(0);
                    stList.add(new SplitTransaction(-1, cid, 0, "interest payment",
                            paymentItemList.get(0).getInterestAmount().negate(), 0));
                } catch (DaoException | ModelException e) {
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

        final TextFormatter<Integer> numPeriodFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        mNumPeriodTextField.setTextFormatter(numPeriodFormatter);
        numPeriodFormatter.valueProperty().bindBidirectional(mReminder.getDateSchedule().getNumPeriodProperty());

        final TextFormatter<Integer> alertDaysFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> RegExUtil.NON_NEGATIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        mAlertDayTextField.setTextFormatter(alertDaysFormatter);
        alertDaysFormatter.valueProperty().bindBidirectional(mReminder.getAlertDaysProperty());

        domRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsDOMBasedProperty());
        fwdRadioButton.selectedProperty().bindBidirectional(mReminder.getDateSchedule().getIsForwardProperty());
        mDSDescriptionLabel.textProperty().bind(mReminder.getDateSchedule().getDescriptionProperty());

        mEstimateAmountRadioButton.setSelected(mReminder.getEstimateCount() > 0);
    }

    private Predicate<Integer> categoryIDPredicate() {
        if (mTypeChoiceBox.getValue() == Reminder.Type.LOAN_PAYMENT) {
            // the set of negative loan account numbers already have a reminder
            MainModel mainModel = reminderModel.getMainModel();

            // negative ID set for all loan accounts
            final Set<Integer> loanAccountIdSet = mainModel.getAccountList(a -> !a.getHiddenFlag()
                    && a.getType() == Account.Type.LOAN).stream().map(a -> -a.getID()).collect(Collectors.toSet());
            // remove the ids for loan accounts already has a reminder
            loanAccountIdSet.removeAll(reminderModel.getLoanReminderLoanAccountIdSet().stream().map(i -> -i)
                    .collect(Collectors.toSet()));

            // only show Loan type accounts in loanAccountIDSet. Add i < 0 should improve performance
            return i -> i < 0 && loanAccountIdSet.contains(i);
        }
        // show all categories and accounts, except the account in AccountID combobox.
        // if account set not set, then return true for everything
        return i -> (mAccountIDComboBox.getValue() == null) || (i != -mAccountIDComboBox.getValue());
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
            List<SplitTransaction> outputSplitTransactionList =
                    DialogUtil.showSplitTransactionsDialog(reminderModel.getMainModel(),
                    getStage(), mAccountIDComboBox.getValue(), stList, message, netAmount);

            if (outputSplitTransactionList != null) {  // splitTransactionList changed
                // check loan account
                if (mReminder.getType() == Reminder.Type.LOAN_PAYMENT) {
                    final String title = "Loan Payment Reminder Warning";
                    final String header = "Inconsistent Split Transactions for Loan Payment Reminder";
                    final String msg;
                    if (outputSplitTransactionList.size() < 2) {
                        msg = "Loan payment reminder should have at least two split transactions, "
                                + "first for principal payment and second interest payment.";
                    } else if (!outputSplitTransactionList.get(0).getCategoryID().equals(mReminder.getCategoryID())) {
                        msg = "Principal payment transfer account should be the same as the Loan account.";
                    } else {
                        msg = "";
                    }
                    if (!msg.isEmpty()) {
                        DialogUtil.showWarningDialog(getStage(), title, header, msg);
                    } else {
                        mReminder.setSplitTransactionList(outputSplitTransactionList);
                    }
                } else {
                    mReminder.setSplitTransactionList(outputSplitTransactionList);
                }
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
        if (mEstimateAmountRadioButton.isSelected()) {
            if (mReminder.getEstimateCount() == null) {
                DialogUtil.showWarningDialog(getStage(), "Estimate count Not Set",
                        "Please set estimate count",
                        "Estimate count has to be an positive integer");
                return;
            }
        } else {
            mReminder.setEstimateCount(0);
        }

        if (mReminder.getAlertDays() == null) {
            DialogUtil.showWarningDialog(getStage(), "Alert Days Not Set", "Please set alert days",
                    "Alert days has to be an non-negative integer");
            return;
        }

        final List<SplitTransaction> stList = mReminder.getSplitTransactionList();
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

            // if there are split transactions, the category id of reminder should not be used
            mCategoryIDComboBox.valueProperty().unbindBidirectional(mReminder.getCategoryIDProperty());
            mReminder.getCategoryIDProperty().set(0);
        }

        try {
            reminderModel.insertUpdateReminder(mReminder);
            close();
        } catch (DaoException | ModelException e) {
            final String msg = ((mReminder.getID() > 0) ? "Update" : "Insert") + " Reminder error";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.getMessage(), e);
        }
    }

    @FXML
    private void handleClose() {
        close();
    }

    private void close() { getStage().close(); }
}
