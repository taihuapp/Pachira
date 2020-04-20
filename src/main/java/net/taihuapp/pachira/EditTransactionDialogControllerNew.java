/*
 * Copyright (C) 2018-2020.  Guangliang He.  All Rights Reserved.
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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;
import org.apache.log4j.Logger;
import org.controlsfx.control.textfield.TextFields;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static net.taihuapp.pachira.Transaction.TradeAction.*;

public class EditTransactionDialogControllerNew {
    private static final Logger mLogger = Logger.getLogger(EditTransactionDialogControllerNew.class);

    private static final BigDecimalStringConverter BIGDECIMALSTRINGCONVERTER = new BigDecimalStringConverter();
    private static final BigDecimalStringConverter DOLLARCENTSTRINGCONVERTER = new BigDecimalStringConverter() {
        public String toString(BigDecimal b) {
            return b == null ? "" : MainApp.DOLLAR_CENT_FORMAT.format(b);
        }
        public BigDecimal fromString(String s) {
            if (s == null)
                return BigDecimal.ZERO;

            try {
                return (BigDecimal) MainApp.DOLLAR_CENT_FORMAT.parse(s);
            } catch (ParseException e) {
                return BigDecimal.ZERO;
            }
        }
    };

    @FXML
    private ChoiceBox<Transaction.TradeAction> mTradeActionChoiceBox;
    @FXML
    private DatePicker mTDatePicker;
    @FXML
    private Label mADatePickerLabel;
    @FXML
    private DatePicker mADatePicker;
    @FXML
    private ComboBox<Account> mAccountComboBox;
    @FXML
    private Label mTransferAccountLabel;
    @FXML
    private ComboBox<Integer> mTransferAccountComboBox;
    @FXML
    private Label mCategoryLabel;
    @FXML
    private ComboBox<Integer> mCategoryComboBox;
    @FXML
    private Label mTagLabel;
    @FXML
    private ComboBox<Integer> mTagComboBox;
    @FXML
    private TextField mMemoTextField;
    @FXML
    private Label mSecurityNameLabel;
    @FXML
    private ComboBox<Security> mSecurityComboBox;
    @FXML
    private Label mReferenceLabel;
    @FXML
    private TextField mReferenceTextField;
    @FXML
    private Label mPayeeLabel;
    @FXML
    private TextField mPayeeTextField;
    @FXML
    private Label mIncomeLabel;
    @FXML
    private TextField mIncomeTextField;
    @FXML
    private Label mSharesLabel;
    @FXML
    private TextField mSharesTextField;
    @FXML
    private Label mOldSharesLabel;
    @FXML
    private TextField mOldSharesTextField;
    @FXML
    private Label mPriceLabel;
    @FXML
    private TextField mPriceTextField;
    @FXML
    private Label mCommissionLabel;
    @FXML
    private TextField mCommissionTextField;
    @FXML
    private Label mAccruedInterestLabel;
    @FXML
    private TextField mAccruedInterestTextField;
    @FXML
    private Label mTotalLabel;
    @FXML
    private TextField mTotalTextField;
    @FXML
    private Button mEnterNewButton;
    @FXML
    private Button mEnterDoneButton;
    @FXML
    private Button mSpecifyLotButton;
    @FXML
    private Button mSplitTransactionButton;

    private MainApp mMainApp;
    private Stage mDialogStage;

    private Transaction mTransactionOrig;  // original copy
    private Transaction mTransaction;  // working copy
    private List<SecurityHolding.MatchInfo> mMatchInfoList = null;
    private boolean mSplitTransactionListChanged = false;

    // allows only numeric input in a textField
    private static void addEventFilterNumericInputOnly(TextField textField) {
        textField.setEditable(true);  // make sure it's editable
        textField.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (!"0123456789.".contains(e.getCharacter()))
                e.consume();
        });
    }

    // clean data contained on invisible controls
    private void cleanInvisibleControlData() {
        if (!mADatePicker.isVisible())
            mTransaction.setADate(mTransaction.getTDate());

        if (!mSplitTransactionButton.isVisible())
            mTransaction.getSplitTransactionList().clear();

        if (!mSecurityComboBox.isVisible())
            mTransaction.setSecurityName("");

        if (!mReferenceTextField.isVisible())
            mTransaction.setReference("");

        if (!mPayeeTextField.isVisible())
            mTransaction.setPayee("");

        if (!mSharesTextField.isVisible())
            mTransaction.setQuantity(BigDecimal.ZERO);

        if (!mOldSharesTextField.isVisible())
            mTransaction.setOldQuantity(BigDecimal.ZERO);

        if (!mCommissionTextField.isVisible())
            mTransaction.setCommission(BigDecimal.ZERO);

        if (!mAccruedInterestTextField.isVisible())
            mTransaction.setAccruedInterest(BigDecimal.ZERO);

        if (!mSpecifyLotButton.isVisible())
            mMatchInfoList.clear();
    }

    private static <T> void autoCompleteComboBox(ComboBox<T> comboBox) {
        List<String> strList = new ArrayList<>();
        for (T t : comboBox.getItems()) {
            strList.add(comboBox.getConverter().toString(t));
        }
        comboBox.setEditable(true);
        TextFields.bindAutoCompletion(comboBox.getEditor(), strList);
    }

    // The edited date in a DatePicker obj is not captured when the datePicker obj
    // goes out of focus.
    private static void captureEditedDate(final DatePicker datePicker) {
        datePicker.getEditor().focusedProperty().addListener((obj, wasFocused, isFocused) -> {
            if (!isFocused) // goes out of focus, save the edited date
                captureEditedDateCore(datePicker);
        });
        datePicker.addEventFilter(KeyEvent.KEY_PRESSED, eh -> {
            if (eh.getCode() == KeyCode.ENTER) // user hit the enter key
                captureEditedDateCore(datePicker);
        });
    }

    // method used by captureEditedDate
    private static void captureEditedDateCore(final DatePicker datePicker) {
        try {
            datePicker.setValue(datePicker.getConverter().fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException e) {
            datePicker.getEditor().setText(datePicker.getConverter().toString(datePicker.getValue()));
        }
    }

    private static void showWarningDialog(String header, String content) {
        MainApp.showWarningDialog("Warning", header, content);
    }

    // return the original transaction ID or -1
    int getTransactionID() {
        return mTransactionOrig == null ? -1 : mTransactionOrig.getID();
    }

    // The input transaction is not altered
    // defaultAccount is the default account for the transaction
    // accountList is all accounts shown at the account comboBox
    // defaultAccount should be in accountList
    // taList is
    void setMainApp(MainApp mainApp, Transaction transaction, Stage stage,
                    List<Account> accountList, Account defaultAccount,
                    List<Transaction.TradeAction> taList) {
        mMainApp = mainApp;
        mDialogStage = stage;

        mTransactionOrig = transaction;
        mTransaction = Objects.requireNonNullElseGet(transaction, () -> new Transaction(defaultAccount.getID(),
                LocalDate.now(), defaultAccount.getType() == Account.Type.INVESTING ? BUY : WITHDRAW, 0));
        mMatchInfoList = mainApp.getMatchInfoList(mTransaction.getID());

        // setup controls

        // trade action always visible
        mTradeActionChoiceBox.getItems().setAll(taList);
        mTradeActionChoiceBox.valueProperty().bindBidirectional(mTransaction.getTradeActionProperty());

        // trade date always visible
        captureEditedDate(mTDatePicker);
        mTDatePicker.valueProperty().bindBidirectional(mTransaction.getTDateProperty());

        // ADatePicker visible only for shares in.
        captureEditedDate(mADatePicker);
        mADatePicker.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(SHRSIN));
        mADatePickerLabel.visibleProperty().bind(mADatePicker.visibleProperty());
        mADatePicker.valueProperty().bindBidirectional(mTransaction.getADateProperty());

        // account always visible
        mAccountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getName();
            }

            @Override
            public Account fromString(String s) {
                return mainApp.getAccountByName(s);
            }
        });
        mAccountComboBox.getItems().setAll(accountList);
        if (accountList.size() > 1) {
            // setup autocompletion if more than one account
            autoCompleteComboBox(mAccountComboBox);
        }
        // mAccountComboBox is NOT bind to transaction accountId property because
        // transaction doesn't have account
        mAccountComboBox.getSelectionModel().select(defaultAccount);

        // Transfer account comboBox
        mTransferAccountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer integer) {
                Account account = mainApp.getAccountByID(-integer); // negative
                return account == null ? "" : account.getName();
            }

            @Override
            public Integer fromString(String s) {
                Account account = mainApp.getAccountByName(s);
                return account == null ? 0 : -account.getID();  // negative
            }
        });
        mTransferAccountComboBox.getItems().add(0); // add an blank account first
        for (Account account : mainApp.getAccountList(null, false, true)) {
            if (account.getID() != defaultAccount.getID())
                mTransferAccountComboBox.getItems().add(-account.getID());  // negative
        }
        autoCompleteComboBox(mTransferAccountComboBox);
        mTransferAccountComboBox.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta != STKSPLIT && ta != DEPOSIT && ta != WITHDRAW && ta != SHRSIN && ta != SHRSOUT
                    && ta != REINVDIV && ta != REINVINT && ta != REINVLG && ta != REINVMD && ta != REINVSH);
        }, mTradeActionChoiceBox.valueProperty()));
        mTransferAccountLabel.visibleProperty().bind(mTransferAccountComboBox.visibleProperty());
        mTransferAccountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            switch (mTradeActionChoiceBox.getValue()) {
                case XOUT:
                    return "Transfer Cash To:";
                case XIN:
                    return "Transfer Cash From:";
                case BUY:
                case CVTSHRT:
                case MISCEXP:
                case MARGINT:
                    return "Use Cash From:";
                default:
                    return "Put Cash Into:";
            }
        }, mTradeActionChoiceBox.valueProperty()));
        mTransferAccountComboBox.valueProperty().bindBidirectional(mTransaction.getCategoryIDProperty().asObject());

        // category comboBox
        mCategoryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer integer) {
                Category category = mainApp.getCategoryByID(integer);
                return category == null ? "" : category.getName();
            }

            @Override
            public Integer fromString(String s) {
                Category category = mainApp.getCategoryByName(s);
                return category == null ? 0 : category.getID();
            }
        });
        mCategoryComboBox.getItems().add(0); // add a blank at the beginning.
        for (Category category : mainApp.getCategoryList())
            mCategoryComboBox.getItems().addAll(category.getID());
        autoCompleteComboBox(mCategoryComboBox);
        mCategoryComboBox.visibleProperty().bind(mTransferAccountComboBox.visibleProperty().not());
        mCategoryLabel.visibleProperty().bind(mCategoryComboBox.visibleProperty());
        mCategoryComboBox.valueProperty().bindBidirectional(mTransaction.getCategoryIDProperty().asObject());

        // memo always visible
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        // split transaction button, visible for X*, Deposit and Withdraw
        mSplitTransactionButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == XIN || ta == XOUT || ta == DEPOSIT || ta == WITHDRAW;
        }, mTradeActionChoiceBox.valueProperty()));

        // tag
        mTagComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer integer) {
                Tag tag = mainApp.getTagByID(integer);
                return tag == null ? "" : tag.getName();
            }

            @Override
            public Integer fromString(String s) {
                Tag tag = mainApp.getTagByName(s);
                return tag == null ? 0 : tag.getID();
            }
        });
        mTagComboBox.getItems().add(0);
        for  (Tag tag : mainApp.getTagList())
            mTagComboBox.getItems().add(tag.getID());
        autoCompleteComboBox(mTagComboBox);
        mTagComboBox.setVisible(defaultAccount.getType() != Account.Type.INVESTING);
        mTagLabel.setVisible(defaultAccount.getType() != Account.Type.INVESTING);
        mTagComboBox.valueProperty().bindBidirectional(mTransaction.getTagIDProperty().asObject());

        // reference
        mReferenceTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == DEPOSIT || ta == WITHDRAW || ta == XIN || ta == XOUT);
        }, mTradeActionChoiceBox.valueProperty()));
        mReferenceLabel.visibleProperty().bind(mReferenceTextField.visibleProperty());
        mReferenceTextField.textProperty().bindBidirectional(mTransaction.getReferenceProperty());

        // security combobox
        mSecurityComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Security security) {
                return security == null ? "" : security.getName();
            }

            @Override
            public Security fromString(String s) {
                return mainApp.getSecurityByName(s);
            }
        });
        TreeSet<Security> allSecuritySet = new TreeSet<>(Comparator.comparing(Security::getName));
        allSecuritySet.addAll(mainApp.getSecurityList());
        allSecuritySet.removeAll(defaultAccount.getCurrentSecurityList()); // remove account security list
        mSecurityComboBox.getItems().add(null); // add a blank one first
        mSecurityComboBox.getItems().addAll(defaultAccount.getCurrentSecurityList());
        mSecurityComboBox.getItems().addAll(allSecuritySet);
        autoCompleteComboBox(mSecurityComboBox);
        mSecurityComboBox.visibleProperty().bind(mReferenceTextField.visibleProperty().not());
        mSecurityNameLabel.visibleProperty().bind(mSecurityComboBox.visibleProperty());
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(), mSecurityComboBox.valueProperty(),
                mSecurityComboBox.getConverter());

        // payee, same visibility as reference
        TextFields.bindAutoCompletion(mPayeeTextField, mMainApp.getPayeeSet());
        mPayeeTextField.visibleProperty().bind(mReferenceTextField.visibleProperty());
        mPayeeLabel.visibleProperty().bind(mPayeeTextField.visibleProperty());
        mPayeeTextField.textProperty().bindBidirectional(mTransaction.getPayeeProperty());

        // Income
        addEventFilterNumericInputOnly(mIncomeTextField);  // only allowing numeric input
        mIncomeTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == REINVDIV || ta == REINVINT || ta == REINVLG || ta == REINVMD || ta == REINVSH
                    || ta == DIV || ta == INTINC || ta == CGLONG || ta == CGMID || ta == CGSHORT
                    || ta == MARGINT || ta == MISCINC || ta == MISCEXP || ta == RTRNCAP);
        }, mTradeActionChoiceBox.valueProperty()));
        mIncomeLabel.visibleProperty().bind(mIncomeTextField.visibleProperty());
        mIncomeLabel.textProperty().bind(mTradeActionChoiceBox.valueProperty().asString());
        mIncomeTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                DOLLARCENTSTRINGCONVERTER);

        // shares
        addEventFilterNumericInputOnly(mSharesTextField);
        mSharesTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == BUY || ta == SELL || ta == REINVDIV || ta == REINVINT || ta == REINVLG
                    || ta == REINVMD || ta == REINVSH || ta == STKSPLIT || ta == SHRSIN
                    || ta == SHRSOUT || ta == SHTSELL || ta == CVTSHRT || ta == XFRSHRS);
        }, mTradeActionChoiceBox.valueProperty()));
        mSharesLabel.visibleProperty().bind(mSharesTextField.visibleProperty());
        mSharesLabel.textProperty().bind(Bindings.createStringBinding(() ->
                        mTradeActionChoiceBox.getValue() == STKSPLIT ? "New Shares" : "Number of Shares",
                mTradeActionChoiceBox.valueProperty()));
        mSharesTextField.textProperty().bindBidirectional(mTransaction.getQuantityProperty(),
                BIGDECIMALSTRINGCONVERTER);

        // old shares
        addEventFilterNumericInputOnly(mOldSharesTextField);
        mOldSharesTextField.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(STKSPLIT));
        mOldSharesLabel.visibleProperty().bind(mOldSharesTextField.visibleProperty());
        mOldSharesTextField.textProperty().bindBidirectional(mTransaction.getOldQuantityProperty(),
                BIGDECIMALSTRINGCONVERTER);


        // price is always calculated
        mPriceTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == BUY || ta == CVTSHRT || ta == SELL || ta == SHTSELL || ta == SHRSIN
                    || ta == REINVDIV || ta == REINVINT || ta == REINVLG || ta == REINVMD || ta == REINVSH);
        }, mTradeActionChoiceBox.valueProperty()));
        mPriceTextField.textProperty().bindBidirectional(mTransaction.getPriceProperty(), BIGDECIMALSTRINGCONVERTER);
        // todo this logic should be moved to Transaction class
        mPriceTextField.textProperty().bind(Bindings.createStringBinding(() -> {
                    if (!mPriceTextField.isVisible())
                        return "";  // no need to do any calculation

                    final BigDecimal amount = mTransaction.getAmount();
                    final BigDecimal quantity = mTransaction.getQuantity();
                    final BigDecimal commission = mTransaction.getCommission();
                    final BigDecimal accruedInterest = mTransaction.getAccruedInterest();
                    if (quantity.signum() == 0)
                        return "";

                    final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
                    final BigDecimal subTotal;
                    if (ta == SELL || ta == SHTSELL)
                        subTotal = amount.add(commission).add(accruedInterest);
                    else
                        subTotal = amount.subtract(commission).subtract(accruedInterest);
                    final BigDecimal price = subTotal.divide(quantity, MainApp.PRICE_FRACTION_LEN, RoundingMode.HALF_UP);
                    return BIGDECIMALSTRINGCONVERTER.toString(price);
                }, mTradeActionChoiceBox.valueProperty(), mTotalTextField.textProperty(), mSharesTextField.textProperty(),
                mCommissionTextField.textProperty(), mAccruedInterestTextField.textProperty()));
        mPriceLabel.visibleProperty().bind(mPriceTextField.visibleProperty());

        // commission, same visibility as price
        addEventFilterNumericInputOnly(mCommissionTextField);
        mCommissionTextField.visibleProperty().bind(mPriceTextField.visibleProperty());
        mCommissionLabel.visibleProperty().bind(mCommissionTextField.visibleProperty());
        mCommissionTextField.textProperty().bindBidirectional(mTransaction.getCommissionProperty(),
                DOLLARCENTSTRINGCONVERTER);

        // accrued interest, same visibility as commission
        addEventFilterNumericInputOnly(mAccruedInterestTextField);
        mAccruedInterestTextField.visibleProperty().bind(mCommissionTextField.visibleProperty());
        mAccruedInterestLabel.visibleProperty().bind(mAccruedInterestTextField.visibleProperty());
        mAccruedInterestTextField.textProperty().bindBidirectional(mTransaction.getAccruedInterestProperty(),
                DOLLARCENTSTRINGCONVERTER);

        // specify lots button
        mSpecifyLotButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == SELL || ta == CVTSHRT || ta == SHRSOUT;
        }, mTradeActionChoiceBox.valueProperty()));

        // total cost
        addEventFilterNumericInputOnly(mTotalTextField);
        mTotalTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == BUY || ta == SELL || ta == SHRSIN || ta == SHTSELL || ta == CVTSHRT
                    || ta == XIN || ta == XOUT || ta == DEPOSIT || ta == WITHDRAW;
        }, mTradeActionChoiceBox.valueProperty()));
        mTotalLabel.visibleProperty().bind(mTotalTextField.visibleProperty());
        mTotalLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            switch (mTradeActionChoiceBox.getValue()) {
                case XIN:
                case XOUT:
                    return "Transfer Amount:";
                case BUY:
                case CVTSHRT:
                case SHRSIN:
                    return "Total Cost:";
                case SELL:
                case SHTSELL:
                    return "Total Sale:";
                default:
                    return "Amount:";
            }
        }, mTradeActionChoiceBox.valueProperty()));
        mTotalTextField.textProperty().bindBidirectional(mTransaction.getAmountProperty(),
                DOLLARCENTSTRINGCONVERTER);

        // set default button
        // if transaction != null, it is editing an existing transaction
        // or entering a reminder transaction, in this case, set default
        // to enter-done, don't even show enter-new button.
        // either we are editing an existing transaction or
        // enter an reminder transaction, don't do enter-new
        boolean defaultEnterDone = mTransactionOrig != null;
        mEnterDoneButton.setDefaultButton(defaultEnterDone);
        mEnterNewButton.setDefaultButton(!defaultEnterDone);
        mEnterNewButton.setVisible(!defaultEnterDone);

        Platform.runLater(() -> mTradeActionChoiceBox.requestFocus());
    }

    private boolean enterTransaction() {
        // clean data attached to invisible controls
        cleanInvisibleControlData();

        final int accountID = mAccountComboBox.getSelectionModel().getSelectedItem().getID();
        mTransaction.setAccountID(accountID);
        if (!validateTransaction())
            return false;

        final int categoryID = mTransaction.getCategoryID();
        final Transaction.TradeAction ta = mTransaction.getTradeAction();

        // setup transfer transaction if needed
        // we don't need another transaction to handle a self transferring transaction
        Transaction.TradeAction xferTA = null;
        switch (ta) {
            case BUY:
            case CVTSHRT:
            case XIN:
            case MARGINT:
            case MISCEXP:
                if (categoryID <= -MainApp.MIN_ACCOUNT_ID && categoryID != -accountID)
                    xferTA = Transaction.TradeAction.XOUT;
                break;
            case DIV:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case INTINC:
            case SELL:
            case SHTSELL:
            case XOUT:
            case MISCINC:
                if (categoryID <= -MainApp.MIN_ACCOUNT_ID && categoryID != -accountID)
                    xferTA = Transaction.TradeAction.XIN;
                break;
            case STKSPLIT:
            case DEPOSIT:
            case WITHDRAW:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case SHRSOUT:
            case SHRSIN:
                // nothing to transfer
                break;
            default:
                mLogger.error("enterTransaction: Trade Action " + ta + " not implemented yet.");
                return false;
        }

        // setup payee
        final String payee = (ta == XIN || ta == XOUT || ta == DEPOSIT || ta == WITHDRAW) ?
                mTransaction.getPayee() : mTransaction.getSecurityName();

        // todo we should get rid of dbCopyT.
        // we don't want to insert mTransaction into master transaction list in memory
        // make a copy of mTransaction
        final Transaction dbCopyT = new Transaction(mTransaction);

        // this is a list of transactions need to be updated in master list
        final List<Transaction> updateTList = new ArrayList<>();
        updateTList.add(dbCopyT);

        // this is a list of transaction id to be deleted in master list
        final List<Integer> deleteList = new ArrayList<>();

        // this is a set of account ids which involved in this transactions and all linked transactions.
        final Set<Integer> accountSet = new HashSet<>();
        accountSet.add(accountID);

        Transaction linkedT = null;  // linked Transaction if any
        if (xferTA != null && dbCopyT.getAmount().signum() != 0) {
            // we need another transaction to handle the transfer
            linkedT = new Transaction(-categoryID, dbCopyT.getTDate(), xferTA, -accountID);
            linkedT.setID(dbCopyT.getMatchID());
            linkedT.setAmount(dbCopyT.getAmount());
            linkedT.setMemo(dbCopyT.getMemo());
            linkedT.setPayee(payee);

            updateTList.add(linkedT);
            accountSet.add(linkedT.getAccountID());
        }

        // database work here
        try {
            // save save point so we can roll back to it if something goes wrong
            if (!mMainApp.setDBSavepoint()) {
                mLogger.error("DBSavepoint unexpectedly set.");
                showWarningDialog("Database save point unexpectedly set", "Please restart application");
                return false;
            }

            // insert dbCopyT to database, and update ID for mTransaction
            mTransaction.setID(mMainApp.insertUpdateTransactionToDB(dbCopyT));

            // for new transaction, tid in elements of mMatchInfo is -1, need to update
            mMainApp.putMatchInfoList(dbCopyT.getID(), mMatchInfoList);

            // handle linked transactions here
            if (linkedT != null) {
                linkedT.setMatchID(dbCopyT.getID(), 0);
                mMainApp.insertUpdateTransactionToDB(linkedT);
                dbCopyT.setMatchID(linkedT.getID(), 0);
                accountSet.add(linkedT.getAccountID());
            } else {
                dbCopyT.setMatchID(-1, -1);
            }

            // handle split transactions
            for (SplitTransaction st : dbCopyT.getSplitTransactionList()) {
                if (st.getCategoryID() <= -MainApp.MIN_ACCOUNT_ID && st.getAmount().compareTo(BigDecimal.ZERO) != 0) {
                    // this is a non-zero amount transfer
                    Transaction stLinkedT = new Transaction(-st.getCategoryID(), dbCopyT.getTDate(),
                            st.getAmount().compareTo(BigDecimal.ZERO) > 0 ? XOUT : XIN, -categoryID);
                    stLinkedT.setID(st.getMatchID());
                    stLinkedT.setAmount(st.getAmount().abs()); // st amount carries sign, stLinedT doesn't
                    stLinkedT.setPayee(st.getPayee());
                    stLinkedT.setMemo(st.getMemo());
                    stLinkedT.setMatchID(dbCopyT.getID(), st.getID());

                    mMainApp.insertUpdateTransactionToDB(stLinkedT);
                    st.setMatchID(stLinkedT.getID());

                    updateTList.add(stLinkedT);
                    accountSet.add(stLinkedT.getAccountID());
                } else {
                    st.setMatchID(0);  // not linked to any other transaction
                }
            }

            // update MatchID Info for dbCopyT
            mMainApp.insertUpdateTransactionToDB(dbCopyT);

            // modify original transaction if necessary
            if (mTransactionOrig != null) {
                for (SplitTransaction st : mTransactionOrig.getSplitTransactionList()) {
                    if (st.getMatchID() > 0) {
                        // has a matchID, let's check if it needs to be deleted
                        boolean delete = true;
                        for (Transaction t : updateTList) {
                            if (t.getID() == st.getMatchID()) {
                                delete = false;
                                break;
                            }
                        }

                        // todo should we move this block inside the if block above?
                        Transaction t = mMainApp.getTransactionByID(st.getMatchID());
                        // this transaction might have been changed, let's update account balance
                        accountSet.add(t.getAccountID());
                        if (delete) {
                            deleteList.add(st.getMatchID());
                            mLogger.debug("deleteTransactionFromDB(" + st.getMatchID()+")");
                            mMainApp.deleteTransactionFromDB(st.getMatchID());
                        }
                    }
                }

                // we should update balance for the transferring account if there is one
                if (mTransactionOrig.getCategoryID() <= -MainApp.MIN_ACCOUNT_ID)
                    accountSet.add(-mTransactionOrig.getCategoryID());

                if (mTransactionOrig.getMatchID() > 0 && mTransactionOrig.getMatchID() != dbCopyT.getMatchID()) {
                    Transaction t = mMainApp.getTransactionByID(mTransactionOrig.getMatchID());
                    if (t != null) {
                        accountSet.add(t.getAccountID());
                        deleteList.add(mTransactionOrig.getMatchID());
                        mLogger.debug("deleteTransactionFromDB(" + mTransactionOrig.getMatchID()+")");
                        mMainApp.deleteTransactionFromDB(mTransactionOrig.getMatchID());
                    }
                }
            }

            // done with database work
            mMainApp.commitDB();

            // update master list here
            for (Integer deleteID : deleteList)
                mMainApp.deleteTransactionFromMasterList(deleteID);

            for (Transaction t : updateTList)
                mMainApp.insertUpdateTransactionToMasterList(t);

        } catch (SQLException e) {
            try {
                mMainApp.rollbackDB();
            } catch (SQLException e1) {
                mMainApp.showExceptionDialog("Database error", "Unable to rollback to savepoint",
                        MainApp.SQLExceptionToString(e1), e1);
            }
        } finally {
            try {
                mMainApp.releaseDBSavepoint();
            } catch (SQLException e) {
                mMainApp.showExceptionDialog("Database error",
                        "Unable to release savepoint and set DB autocommit",
                        MainApp.SQLExceptionToString(e), e);
            }
        }

        // update prices first
        final BigDecimal price = dbCopyT.getPrice();
        Security security = mMainApp.getSecurityByName(dbCopyT.getSecurityName());
        if (security != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            int securityID = security.getID();
            LocalDate date = dbCopyT.getTDate();

            mMainApp.insertUpdatePriceToDB(securityID, date, price, 0);

            mMainApp.updateAccountBalance(security);
        }

        for (Integer aID : accountSet) {
            mMainApp.updateAccountBalance(aID);
        }

        // we're done
        return true;
    }

    // maybe this logic should be moved to Transaction class
    private boolean validateTransaction() {

        final Transaction.TradeAction ta = mTransaction.getTradeAction();
        final int accountID = mTransaction.getAccountID();

        // check transfer account
        if (ta == XIN || ta == XOUT) {
            final int categoryID = mTransaction.getCategoryID();
            if (accountID == -categoryID) {
                mLogger.warn("Self Transfer Transaction not allowed");
                showWarningDialog("Self transfer transaction not allowed",
                        "Please make sure transfer account differs from originating account");
                return false;
            }
            if (mMainApp.getAccountByID(-categoryID) == null
                    && mTransaction.getSplitTransactionList().isEmpty()) {
                mLogger.warn("Invalid transfer account");
                showWarningDialog("Invalid transfer account",
                        "Please select a valid transfer account");
                return false;
            }
        }

        // check split transactions amounts
        // also check self transferring
        if (!mTransaction.getSplitTransactionList().isEmpty()) {
            BigDecimal netAmount = mTransaction.getPayment().subtract(mTransaction.getDeposit());
            for (SplitTransaction st : mTransaction.getSplitTransactionList()) {
                netAmount = netAmount.add(st.getAmount());
                if (st.getCategoryID() == -accountID) {
                    mLogger.warn("Split transaction transfer to the original account.");
                    showWarningDialog("Self transfer in split transactions",
                            "Please make sure account is different from the transfer account");
                    return false;
                }
            }
            if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
                mLogger.warn("Net Difference = " + netAmount + ", mismatch");
                showWarningDialog("Split transaction amount not matching total amount.",
                        "Please check split transaction and total amount");
                return false;
            }
        }

        // check to see if enough existing position to sell or cover
        if (ta == SELL || ta == SHRSOUT || ta == CVTSHRT) {
            List<SecurityHolding> securityHoldingList =
                    mMainApp.updateAccountSecurityHoldingList(mMainApp.getAccountByID(accountID),
                            mTransaction.getTDate(), mTransaction.getID());
            boolean hasEnough = false;
            for (SecurityHolding sh : securityHoldingList) {
                if (sh.getSecurityName().equals(mTransaction.getSecurityName())) {
                    // we have matching security position, check
                    // sh.getQuantity is signed, mTransaction getQuantity is always positive
                    if (((ta == SELL || ta == SHRSOUT) && sh.getQuantity().compareTo(mTransaction.getQuantity()) >= 0)
                        || (ta == CVTSHRT && sh.getQuantity().compareTo(mTransaction.getQuantity().negate()) <= 0)) {
                        hasEnough = true;
                        break;
                    }
                }
            }
            if (!hasEnough) {
                String header;
                if (ta == SELL)
                    header = "Sell quantity exceeded existing holding quantity";
                else if (ta == SHRSOUT)
                    header = "Transferring quantity exceeded existing holding quantity";
                else
                    header ="Short cover quantity exceeded existing short quantity";
                String content = "Please check trade quantity";
                showWarningDialog(header, content);
                return false;
            }
        }

        // check ADate for SHRSIN and XFRSHRS
        if (ta == SHRSIN || ta == XFRSHRS) {
            LocalDate aDate = mTransaction.getADate();
            String header = null;
            String content = "Please select a valid acquisition date";
            if (aDate == null)
                header = "Acquisition date cannot be empty";
            else if (aDate.isAfter(mTransaction.getTDate()))
                header = "Acquisition date cannot be after trade date";

            if (header != null) {
                mLogger.warn(header);
                showWarningDialog(header, content);
                return false;
            }
        }

        // will this ever happen?
        String securityName = mTransaction.getSecurityName();
        Security security = mMainApp.getSecurityByName(securityName);
        if (security == null) {
            if (!securityName.isEmpty()) {
                String heading = "Invalid security name " + securityName;
                mLogger.warn(heading);
                showWarningDialog(heading, "Please select a valid security");
                return false;
            }

            // some trade action allows null security, others doesn't
            if (!(ta == DIV || ta == INTINC || ta == XIN || ta == XOUT || ta == DEPOSIT || ta == WITHDRAW
                    || ta == MARGINT)) {
                String header = "Empty security";
                mLogger.warn(header);
                showWarningDialog(header, "Please select a valid security");
                return false;
            }
        }

        // so far so good
        return true;
    }

    @FXML
    private void handleSpecifyLots() {
        mMainApp.showSpecifyLotsDialog(mDialogStage, mTransaction, mMatchInfoList);
    }

    @FXML
    private void handleSplitTransactions() {
        List<SplitTransaction> outSplitTransactionList = mMainApp.showSplitTransactionsDialog(mDialogStage,
                mTransaction.getSplitTransactionList(), mTransaction.getPayment().subtract(mTransaction.getDeposit()));

        if (outSplitTransactionList != null) {
            mSplitTransactionListChanged = true;
            mTransaction.setSplitTransactionList(outSplitTransactionList);
        }

        if (!mTransaction.getSplitTransactionList().isEmpty()) {
            // has split, unset category or transfer
            if (mCategoryComboBox.isVisible())
                mCategoryComboBox.getSelectionModel().select(Integer.valueOf(0));
            if (mTransferAccountComboBox.isVisible())
                mTransferAccountComboBox.getSelectionModel().select(Integer.valueOf(0));
        }
    }

    @FXML
    private void handleCancel() {
        if (mSplitTransactionListChanged) {
            // ask if user want to save changed splitTransaction
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Split Transactions were changed.");
            alert.setHeaderText("Are you sure to discard the change?");
            alert.setContentText("OK to discard change, Cancel to go back.");
            Optional<ButtonType> result = alert.showAndWait();
            if (!(result.isPresent() && result.get() == ButtonType.OK))
                return; // do nothing
        }

        mDialogStage.close();
    }

    @FXML
    private void handleClear() {
        mPayeeTextField.setText("");
        mReferenceTextField.setText("");
        mMemoTextField.setText("");
        mIncomeTextField.setText("0.00");
        mSharesTextField.setText("0.000");
        mOldSharesTextField.setText("0.000");
        mCommissionTextField.setText("0.00");
        mAccruedInterestTextField.setText("0.00");
        mTotalTextField.setText("0.00");
    }

    @FXML
    private void handleEnterDone() {
        if (enterTransaction())
            mDialogStage.close();
    }

    @FXML
    private void handleEnterNew() {
        if (enterTransaction()) {
            mTransaction.setID(0);
            mTransaction.setMatchID(-1, -1);
            mTransactionOrig = null;
            handleClear();
            mTradeActionChoiceBox.requestFocus();
        }
    }
}
