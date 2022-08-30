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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class EditLoanDialogController {

    private static final Logger logger = Logger.getLogger(EditLoanDialogController.class);

    private MainModel mainModel;
    private Loan loan;
    private final BooleanProperty readOnlyProperty = new SimpleBooleanProperty(false);
    private ObservableList<Loan> existingLoans;

    @FXML
    private RadioButton newAccountRadioButton;
    @FXML
    private RadioButton availableAccountRadioButton;
    @FXML
    private TextField newAccountNameTextField;
    @FXML
    private ComboBox<Account> availableAccountComboBox;
    @FXML
    private TextField descriptionTextField;
    @FXML
    private TextField originalAmountTextField;
    @FXML
    private TextField interestRateTextField;
    @FXML
    private ChoiceBox<DateSchedule.BaseUnit> compoundingBaseUnitChoiceBox;
    @FXML
    private TextField compoundingBaseUnitRepeatTextField;
    @FXML
    private ChoiceBox<DateSchedule.BaseUnit> paymentBaseUnitChoiceBox;
    @FXML
    private TextField paymentBaseUnitRepeatTextField;
    @FXML
    private TextField numberOfPaymentsTextField;
    @FXML
    private DatePicker loanDateDatePicker;
    @FXML
    private DatePicker firstPaymentDatePicker;
    @FXML
    private TextField paymentAmountTextField;
    @FXML
    private RadioButton calcPaymentRadioButton;
    @FXML
    private RadioButton setPaymentRadioButton;
    @FXML
    private TableView<Loan.PaymentItem> paymentScheduleTableView;
    @FXML
    private TableColumn<Loan.PaymentItem, Integer> seqNumTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, LocalDate> paymentDateTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, BigDecimal> principalPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, BigDecimal> interestPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, BigDecimal> balanceTableColumn;
    @FXML
    private Button saveButton;
    @FXML
    private Button editPaymentButton;
    @FXML
    private Button rateChangeButton;
    @FXML
    private Button makePaymentButton;

    void setMainModel(MainModel mainModel, Loan loan, ObservableList<Loan> existingLoans) {
        this.mainModel = mainModel;
        this.loan = loan;
        this.existingLoans = existingLoans;

        // we can either create a new loan (loan.getAccountID() <= 0), or show details
        // of an existing loan (loan.getAccountID() > 0).
        readOnlyProperty.bind(Bindings.createBooleanBinding(() -> loan.getAccountID() > 0,
                loan.getAccountIDProperty()));
        if (readOnlyProperty.get())
            loan.updatePaymentSchedule();

        // these accounts are already linked with a loan.
        final Set<Integer> occupiedLoanAccountIdList = existingLoans.stream().map(Loan::getAccountID)
                .collect(Collectors.toSet());

        // loan can be linked to these accounts
        final ObservableList<Account> availableAccounts;
        if (loan.getAccountID() > 0) {
            availableAccounts = mainModel.getAccountList(a -> a.getID() == loan.getAccountID());
        } else {
            availableAccounts = mainModel.getAccountList(a -> a.getType() == Account.Type.LOAN
                    && !a.getHiddenFlag() && !occupiedLoanAccountIdList.contains(a.getID()));
        }

        newAccountRadioButton.disableProperty().bind(readOnlyProperty);
        newAccountNameTextField.editableProperty().bind(readOnlyProperty.not()
                .and(newAccountRadioButton.selectedProperty()));
        descriptionTextField.editableProperty().bind(newAccountNameTextField.editableProperty());

        availableAccountComboBox.setConverter(new ConverterUtil.AccountConverter(mainModel));
        availableAccountComboBox.disableProperty().bind(availableAccountRadioButton.selectedProperty().not());
        availableAccountComboBox.valueProperty().addListener((obs, o, n) ->
                descriptionTextField.setText(n != null && availableAccountRadioButton.isSelected() ?
                        n.getDescription() : ""));
        availableAccountRadioButton.selectedProperty().addListener((obs, o, n) ->
                descriptionTextField.setText(n && (availableAccountComboBox.getValue() != null) ?
                        availableAccountComboBox.getValue().getDescription() : ""));
        availableAccountRadioButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        readOnlyProperty.get() || availableAccounts.isEmpty(),
                readOnlyProperty, Bindings.size(availableAccounts)));
        setupAccountSection();

        // hard code usd here
        final Currency currency = Currency.getInstance("USD");
        // two digits dollar and cents
        final BigDecimalStringConverter currencyAmountStringConverter =
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency);
        final Pattern currencyPattern = RegExUtil.getCurrencyInputRegEx(currency, false);
        final TextFormatter<BigDecimal> originalAmountFormatter = new TextFormatter<>(currencyAmountStringConverter,
                null, c -> currencyPattern.matcher(c.getControlNewText()).matches() ? c : null);
        originalAmountTextField.setTextFormatter(originalAmountFormatter);
        originalAmountFormatter.valueProperty().bindBidirectional(this.loan.getOriginalAmountProperty());
        originalAmountTextField.editableProperty().bind(readOnlyProperty.not());

        TextFormatter<BigDecimal> interestRateFormatter = new TextFormatter<>(new BigDecimalStringConverter(), null,
                c -> RegExUtil.INTEREST_RATE_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        interestRateTextField.setTextFormatter(interestRateFormatter);
        interestRateFormatter.valueProperty().bindBidirectional(this.loan.getInterestRateProperty());
        interestRateTextField.editableProperty().bind(readOnlyProperty.not());

        compoundingBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());
        compoundingBaseUnitChoiceBox.valueProperty().bindBidirectional(this.loan.getCompoundBaseUnitProperty());
        compoundingBaseUnitChoiceBox.disableProperty().bind(readOnlyProperty);

        TextFormatter<Integer> compoundingBaseUnitRepeatFormatter =
                new TextFormatter<>(new IntegerStringConverter(), null,
                        c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        compoundingBaseUnitRepeatTextField.setTextFormatter(compoundingBaseUnitRepeatFormatter);
        compoundingBaseUnitRepeatFormatter.valueProperty().bindBidirectional(this.loan
                .getCompoundBURepeatProperty());
        compoundingBaseUnitRepeatTextField.editableProperty().bind(readOnlyProperty.not());

        paymentBaseUnitChoiceBox.getItems().setAll(DateSchedule.BaseUnit.values());
        paymentBaseUnitChoiceBox.valueProperty().bindBidirectional(this.loan.getPaymentBaseUnitProperty());
        paymentBaseUnitChoiceBox.disableProperty().bind(readOnlyProperty);

        TextFormatter<Integer> paymentBaseUnitRepeatFormatter =
                new TextFormatter<>(new IntegerStringConverter(), null,
                        c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        paymentBaseUnitRepeatTextField.setTextFormatter(paymentBaseUnitRepeatFormatter);
        paymentBaseUnitRepeatFormatter.valueProperty().bindBidirectional(this.loan.getPaymentBURepeatProperty());
        paymentBaseUnitRepeatTextField.editableProperty().bind(readOnlyProperty.not());

        TextFormatter<Integer> numberOfPaymentsFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> RegExUtil.POSITIVE_INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        numberOfPaymentsTextField.setTextFormatter(numberOfPaymentsFormatter);
        numberOfPaymentsFormatter.valueProperty().bindBidirectional(this.loan.getNumberOfPaymentsProperty());
        numberOfPaymentsTextField.editableProperty().bind(readOnlyProperty.not());

        loanDateDatePicker.valueProperty().bindBidirectional(this.loan.getLoanDateProperty());
        // javafx DatePicker control doesn't aware of the edited value in its TextField, this is a work around
        DatePickerUtil.captureEditedDate(loanDateDatePicker);
        loanDateDatePicker.disableProperty().bind(readOnlyProperty);

        firstPaymentDatePicker.valueProperty().bindBidirectional(this.loan.getFirstPaymentDateProperty());
        // javafx DatePicker control doesn't aware of the edited value in its TextField, this is a work around
        DatePickerUtil.captureEditedDate(firstPaymentDatePicker);
        firstPaymentDatePicker.disableProperty().bind(readOnlyProperty);

        final TextFormatter<BigDecimal> paymentAmountFormatter = new TextFormatter<>(currencyAmountStringConverter,
                null, c -> currencyPattern.matcher(c.getControlNewText()).matches() ? c : null);
        paymentAmountTextField.setTextFormatter(paymentAmountFormatter);
        paymentAmountFormatter.valueProperty().bindBidirectional(this.loan.getPaymentAmountProperty());
        paymentAmountTextField.editableProperty().bind(setPaymentRadioButton.selectedProperty()
                .and(readOnlyProperty.not()));

        calcPaymentRadioButton.selectedProperty().bindBidirectional(this.loan.getCalcPaymentAmountProperty());
        calcPaymentRadioButton.disableProperty().bind(readOnlyProperty);
        setPaymentRadioButton.disableProperty().bind(readOnlyProperty);

        paymentScheduleTableView.getStylesheets().add(
                Objects.requireNonNull(MainApp.class.getResource("/css/TransactionTableView.css"))
                .toExternalForm());
        paymentScheduleTableView.setItems(loan.getPaymentSchedule());

        paymentScheduleTableView.setRowFactory(tv -> {
            final PseudoClass paid = PseudoClass.getPseudoClass("reconciled");
            final TableRow<Loan.PaymentItem> row = new TableRow<>();
            final ChangeListener<Boolean> changeListener = (obs, ov, nv) -> row.pseudoClassStateChanged(paid, nv);
            row.itemProperty().addListener((obs, ov, nv) -> {
                if (nv != null) {
                    row.pseudoClassStateChanged(paid, nv.getIsPaidProperty().get());
                    nv.getIsPaidProperty().addListener(changeListener);
                } else {
                    row.pseudoClassStateChanged(paid, false);
                }
                if (ov != null) {
                    ov.getIsPaidProperty().removeListener(changeListener);
                }
            });
            return row;
        });
        seqNumTableColumn.setCellValueFactory(cd -> cd.getValue().getSequenceIDProperty());
        paymentDateTableColumn.setCellValueFactory(cd -> cd.getValue().getDateProperty());

        principalPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getPrincipalAmountProperty());
        principalPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(currencyAmountStringConverter.toString(item));
            }
        });
        interestPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getInterestAmountProperty());
        interestPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(currencyAmountStringConverter.toString(item));
            }
        });
        balanceTableColumn.setCellValueFactory(cd -> cd.getValue().getBalanceAmountProperty());
        balanceTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(currencyAmountStringConverter.toString(item));
            }
        });

        saveButton.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        ((newAccountRadioButton.isSelected() && newAccountNameTextField.getText().isBlank())
                                || paymentScheduleTableView.getItems().isEmpty()) || readOnlyProperty.get(),
                newAccountRadioButton.selectedProperty(), newAccountNameTextField.textProperty(),
                Bindings.size(paymentScheduleTableView.getItems()), readOnlyProperty));


        editPaymentButton.disableProperty().bind(readOnlyProperty.not());
        rateChangeButton.disableProperty().bind(readOnlyProperty.not());
        makePaymentButton.disableProperty().bind(readOnlyProperty.not());
    }

    // set up the section for either new account or existing account
    private void setupAccountSection() {
        // these accounts are already linked with a loan.
        final Set<Integer> occupiedLoanAccountIdList = existingLoans.stream().map(Loan::getAccountID)
                .collect(Collectors.toSet());

        final ObservableList<Account> availableAccounts;
        if (loan.getAccountID() > 0) {
            availableAccounts = mainModel.getAccountList(a -> a.getID() == loan.getAccountID());
            newAccountNameTextField.setText("");
            availableAccountRadioButton.setSelected(true);
        } else {
            availableAccounts = mainModel.getAccountList(a -> a.getType() == Account.Type.LOAN
                    && !a.getHiddenFlag() && !occupiedLoanAccountIdList.contains(a.getID()));
        }
        availableAccountComboBox.getItems().setAll(availableAccounts);

        if (!availableAccountComboBox.getItems().isEmpty())
            availableAccountComboBox.getSelectionModel().selectFirst();
    }

    private Stage getStage() { return (Stage) newAccountNameTextField.getScene().getWindow(); }

    @FXML
    private void handleEditPayment() {
        System.err.println("edit payment");
    }

    @FXML
    private void handleRateChange() {
        System.err.println("rate change");
    }

    @FXML
    private void handleMakePayment() {
        // check if there is a reminder set for the loan already
        try {
            final ReminderModel reminderModel = new ReminderModel(mainModel);
            final Set<Integer> loanAccountIdSet = reminderModel.getLoanReminderLoanAccountIdSet();
            if (loanAccountIdSet.contains(loan.getAccountID())) {
                // there is a reminder for this loan.
                DialogUtil.showInformationDialog(getStage(), "Reminder exist for loan",
                        "Do not make payment here",
                        "A reminder already exists for this loan.  Please use reminder to make loan payment");
                return;
            }
        } catch (DaoException | ModelException e) {
            final String msg = "DaoException when create ReminderModel";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
            return;
        }

        // find the first unpaid payment item
        final Optional<Loan.PaymentItem> paymentItemOptional = loan.getPaymentSchedule()
                .stream().filter(pi -> !pi.getIsPaidProperty().get()).findFirst();

        if (paymentItemOptional.isEmpty()) {
            DialogUtil.showInformationDialog(getStage(), "All payments are paid", "No payment needed",
                    "All payments for this loan has already been paid, nothing more to pay.");
            return;
        }

        final Loan.PaymentItem paymentItem = paymentItemOptional.get();
        final List<Account> accountList = mainModel.getAccountList(a ->
                (!a.getHiddenFlag() && a.getType().isGroup(Account.Type.Group.SPENDING)));
        // transaction has split, set 0 for category id
        final Transaction transaction = new Transaction(accountList.get(0).getID(), paymentItem.getDate(),
                    Transaction.TradeAction.WITHDRAW, 0);
        final List<SplitTransaction> stList = new ArrayList<>();
        // add principal payment and interest payment
        stList.add(new SplitTransaction(-1, -loan.getAccountID(), 0, "principal payment",
                paymentItem.getPrincipalAmount().negate(), 0));
        stList.add(new SplitTransaction(-1,
                mainModel.getCategory(c -> c.getName().equals("Interest Exp")).map(Category::getID).orElse(0),
                0, "interest payment", paymentItem.getInterestAmount().negate(), 0));
        transaction.setSplitTransactionList(stList);
        transaction.setAmount(stList.stream().map(SplitTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate());
        try {
            int tid = DialogUtil.showEditTransactionDialog(mainModel, getStage(), transaction, accountList,
                    accountList.get(0), Collections.singletonList(transaction.getTradeAction()));

            int ltId = mainModel.insertLoanTransaction(new LoanTransaction(-1, LoanTransaction.Type.REGULAR_PAYMENT,
                    loan.getAccountID(), tid, paymentItem.getDate(), BigDecimal.ZERO, BigDecimal.ZERO));
            loan.addLoanTransaction(new LoanTransaction(ltId, LoanTransaction.Type.REGULAR_PAYMENT,
                    loan.getAccountID(), tid, paymentItem.getDate(), BigDecimal.ZERO, BigDecimal.ZERO));
        } catch (IOException | DaoException e) {
            final String msg = e.getClass().getName() + " when opening EditTransactionDialog";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.getMessage(), e);
        }
    }

    @FXML
    private void handleSave() {
        try {
            if (availableAccountRadioButton.isSelected()) {
                loan.setAccountID(availableAccountComboBox.getValue().getID());
            }
            mainModel.insertLoan(loan, newAccountNameTextField.getText().trim(),
                        descriptionTextField.getText().trim());
            existingLoans.add(loan);
            setupAccountSection();
        } catch (DaoException e) {
            final String msg = "insert loan failed";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(),"DaoException", msg, e.toString(), e);
        }

        // create a transaction of the loan initiation
        final Transaction t = new Transaction(loan.getAccountID(), loan.getLoanDate(),
                Transaction.TradeAction.WITHDRAW, 0);
        t.setAmount(loan.getOriginalAmount());
        t.setMemo("Loan initiation");
        try {
            mainModel.alterTransaction(null, t, new ArrayList<>());
        } catch (DaoException | ModelException e) {
            final String msg = "failed adding loan initiation transaction";
            logger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
        }
    }

    @FXML
    private void handleClose() { getStage().close(); }
}
