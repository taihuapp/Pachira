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

import impl.org.controlsfx.autocompletion.AutoCompletionTextFieldBinding;
import impl.org.controlsfx.autocompletion.SuggestionProvider;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;

import static net.taihuapp.pachira.Transaction.TradeAction.*;

public class EditTransactionDialogControllerNew {
    private static final Logger mLogger = Logger.getLogger(EditTransactionDialogControllerNew.class);

    private static final BigDecimalStringConverter BIGDECIMALSTRINGCONVERTER = new BigDecimalStringConverter() {
        public BigDecimal fromString(String s) {
            BigDecimal b = super.fromString(s);
            return b == null ? BigDecimal.ZERO : b;
        }
    };
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

    static class CategoryTransferAccountIDComboBoxWrapper {
        private final ComboBox<Integer> mComboBox;
        private final FilteredList<Integer> mFilteredCTIDList;
        private final SuggestionProvider<String> mProvider;

        // the input combobox should be a naked one.
        CategoryTransferAccountIDComboBoxWrapper(final ComboBox<Integer> comboBox, final MainApp mainApp) {
            mComboBox = comboBox;

            // set converter first
            mComboBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(Integer id) {
                    if (id == null)
                        return "";
                    return mainApp.mapCategoryOrAccountIDToName(id);
                }

                @Override
                public Integer fromString(String name) {
                    return mainApp.mapCategoryOrAccountNameToID(name);
                }
            });

            // populate combobox items
            ObservableList<Integer> idList = FXCollections.observableArrayList();
            idList.add(0);
            for (Category category : mainApp.getCategoryList())
                idList.add(category.getID());
            for (Account account : mainApp.getAccountList(null, false, true))
                idList.add(-account.getID());

            mFilteredCTIDList = new FilteredList<>(idList);
            mComboBox.getItems().setAll(mFilteredCTIDList);

            // set autocompletion
            mComboBox.setEditable(true);
            List<String> strList = new ArrayList<>();
            for (Integer integer : mFilteredCTIDList) {
                strList.add(mComboBox.getConverter().toString(integer));
            }

            // setup autocompletion
            mProvider = SuggestionProvider.create(strList);
            new AutoCompletionTextFieldBinding<>(comboBox.getEditor(), mProvider);
        }

        void setFilter(boolean excludeCategory, int excludeAID) {
            Integer selectedValue = mComboBox.getSelectionModel().getSelectedItem();
            Predicate<Integer> p;
            if (excludeCategory) {
                if (excludeAID < 0)
                    p = i -> i <= 0;
                else
                    p = i -> i <= 0 && i != -excludeAID;
            } else {
                if (excludeAID < 0)
                    p = i -> true;
                else
                    p = i -> i != -excludeAID;
            }
            mFilteredCTIDList.setPredicate(p);
            mComboBox.getItems().setAll(mFilteredCTIDList);
            List<String> strList = new ArrayList<>();
            for (Integer integer : mFilteredCTIDList) {
                strList.add(mComboBox.getConverter().toString(integer));
            }
            mProvider.clearSuggestions();
            mProvider.addPossibleSuggestions(strList);

            if (selectedValue == null || !p.test(selectedValue) )
                mComboBox.getSelectionModel().select(Integer.valueOf(0));
            else
                mComboBox.getSelectionModel().select(selectedValue);
        }
    }

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
    private Label mCategoryLabel;
    @FXML
    private ComboBox<Integer> mCategoryComboBox;
    private CategoryTransferAccountIDComboBoxWrapper mCategoryComboBoxWrapper;
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

        if ((!mCategoryComboBox.isVisible()))
            mTransaction.setCategoryID(0);

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
        else {
            // even if specify lot button is visible, but if the lot info is not
            // valid, still clear is.
            boolean isOK = true;
            BigDecimal totalQuantity = BigDecimal.ZERO;
            for (SecurityHolding.MatchInfo mi : mMatchInfoList) {
                Transaction transaction = mMainApp.getTransactionByID(mi.getMatchTransactionID());
                if (!transaction.getTDate().isBefore(mTransaction.getTDate())
                        || !transaction.getSecurityName().equals(mTransaction.getSecurityName())) {
                    isOK = false;  // this is not good.
                    break;
                }
                totalQuantity = totalQuantity.add(mi.getMatchQuantity());
            }
            if (totalQuantity.compareTo(mTransaction.getQuantity()) != 0)
                isOK = false;

            if (!isOK)
                mMatchInfoList.clear();
        }
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

    // return ID for mTransaction
    int getTransactionID() {
        return mTransaction.getID();
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

        if (transaction == null) {
            mTransactionOrig = null;
            mTransaction = new Transaction(defaultAccount.getID(), LocalDate.now(),
                    defaultAccount.getType() == Account.Type.INVESTING ? BUY : WITHDRAW, 0);
        } else {
            mTransactionOrig = transaction.getID() > 0 ? transaction : null;
            mTransaction = new Transaction(transaction);
        }
        mMatchInfoList = mainApp.getMatchInfoList(mTransaction.getID());

        // setup controls

        // trade action always visible
        mTradeActionChoiceBox.getItems().setAll(taList);
        mTradeActionChoiceBox.valueProperty().bindBidirectional(mTransaction.getTradeActionProperty());
        mTradeActionChoiceBox.valueProperty().addListener((obs, ov, nv) -> {
            final Account account = mAccountComboBox.getValue();
            if (nv == null || account == null || mCategoryComboBoxWrapper == null)
                return;

            mCategoryComboBoxWrapper.setFilter(nv != DEPOSIT && nv != WITHDRAW, account.getID());
        });

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
        mAccountComboBox.valueProperty().addListener((obs, ov, nv) -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            if (nv == null || ta == null || mCategoryComboBoxWrapper == null)
                return;

            mCategoryComboBoxWrapper.setFilter(ta != DEPOSIT && ta != WITHDRAW, nv.getID());
        });
        // mAccountComboBox is NOT bind to transaction accountId property because
        // transaction doesn't have account
        mAccountComboBox.getSelectionModel().select(defaultAccount);

        // category comboBox
        mCategoryComboBoxWrapper = new CategoryTransferAccountIDComboBoxWrapper(mCategoryComboBox, mainApp);
        mCategoryComboBoxWrapper.setFilter(mTransaction.getTradeAction() != DEPOSIT
                && mTransaction.getTradeAction() != WITHDRAW, defaultAccount.getID());

        // category combobox visibility
        mCategoryComboBox.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta != STKSPLIT && ta != SHRSIN && ta != SHRSOUT && ta != REINVDIV && ta != REINVINT
                    && ta != REINVLG && ta != REINVMD && ta != REINVSH);
        }, mTradeActionChoiceBox.valueProperty()));
        mCategoryLabel.visibleProperty().bind(mCategoryComboBox.visibleProperty());
        mCategoryLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            switch (mTradeActionChoiceBox.getValue()) {
                case DEPOSIT:
                    return "Category/Transfer from";
                case WITHDRAW:
                    return "Category/Transfer to";
                case BUY:
                case MISCEXP:
                case CVTSHRT:
                case MARGINT:
                    return "Use cash from";
                default:
                    return "Put cash to";
            }
        }, mTradeActionChoiceBox.valueProperty()));
        mCategoryComboBox.valueProperty().bindBidirectional(mTransaction.getCategoryIDProperty());

        // memo always visible
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        // split transaction button, visible for X*, Deposit and Withdraw
        mSplitTransactionButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == DEPOSIT || ta == WITHDRAW;
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
        mTagComboBox.valueProperty().bindBidirectional(mTransaction.getTagIDProperty());

        // reference
        mReferenceTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == DEPOSIT || ta == WITHDRAW);
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
        Security currentSecurity = mMainApp.getSecurityByName(mTransaction.getSecurityName());
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(), mSecurityComboBox.valueProperty(),
                mSecurityComboBox.getConverter());
        mSecurityComboBox.getSelectionModel().select(currentSecurity);

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
                }, mTransaction.getTradeActionProperty(), mTransaction.getAmountProperty(),
                mTransaction.getQuantityProperty(), mTransaction.getCommissionProperty(),
                mTransaction.getAccruedInterestProperty()));
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
                    || ta == DEPOSIT || ta == WITHDRAW;
        }, mTradeActionChoiceBox.valueProperty()));
        mTotalLabel.visibleProperty().bind(mTotalTextField.visibleProperty());
        mTotalLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            switch (mTradeActionChoiceBox.getValue()) {
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
        boolean defaultEnterDone = transaction != null;
        mEnterDoneButton.setDefaultButton(defaultEnterDone);
        mEnterNewButton.setDefaultButton(!defaultEnterDone);
        mEnterNewButton.setVisible(!defaultEnterDone);

        Platform.runLater(() -> mTradeActionChoiceBox.requestFocus());
    }

    // enter transaction to database and master list
    // return true of successful, false otherwise
    private boolean enterTransaction() {
        // accountID is not automatically updated, update now
        mTransaction.setAccountID(mAccountComboBox.getSelectionModel().getSelectedItem().getID());

        // clean data attached to invisible controls
        cleanInvisibleControlData();

        // validate transaction now
        if (!validateTransaction())
            return false;

        // most work is done by alterTransaction method
        return mMainApp.alterTransaction(mTransactionOrig, mTransaction, mMatchInfoList);
    }

    // maybe this logic should be moved to Transaction class
    private boolean validateTransaction() {

        final Transaction.TradeAction ta = mTransaction.getTradeAction();
        final int accountID = mTransaction.getAccountID();
        final int categoryID = mTransaction.getCategoryID();

        // check transfer account
        if (categoryID < 0) {
            if (accountID == -categoryID) {
                mLogger.warn("Self Transfer Transaction not allowed");
                showWarningDialog("Self transfer transaction not allowed",
                        "Please make sure transfer account differs from originating account");
                return false;
            }
            if (mMainApp.getAccountByID(-categoryID) == null
                    && mTransaction.getSplitTransactionList().isEmpty()) {
                mLogger.warn("Invalid transfer account, ID = " + (-categoryID));
                showWarningDialog("Invalid transfer account, ID = " + (-categoryID),
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
            if (!(ta == DIV || ta == INTINC  || ta == DEPOSIT || ta == WITHDRAW || ta == MARGINT)) {
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
        final Account account = mAccountComboBox.getValue();
        final int accountID = account == null ? -1 : account.getID();
        List<SplitTransaction> outSplitTransactionList = mMainApp.showSplitTransactionsDialog(mDialogStage,
                accountID, mTransaction.getSplitTransactionList(),
                mTransaction.getPayment().subtract(mTransaction.getDeposit()));

        if (outSplitTransactionList == null) {
            // it was cancelled. do nothing
            return;
        }

        if (outSplitTransactionList.size() == 1) {
            // we really need at least 2 to split
            SplitTransaction st = outSplitTransactionList.get(0);
            mTransaction.getCategoryIDProperty().set(st.getCategoryID());
            mTransaction.setMemo(st.getMemo());
            mTransaction.setPayee(st.getPayee());
            mTransaction.setMatchID(st.getMatchID(),-1);

            if (st.getAmount().compareTo(BigDecimal.ZERO) >= 0)
                mTransaction.getTradeActionProperty().set(DEPOSIT);
            else
                mTransaction.getTradeActionProperty().set(WITHDRAW);

            // do really need split
            outSplitTransactionList.clear();
        }

        mSplitTransactionListChanged = true;
        mTransaction.setSplitTransactionList(outSplitTransactionList);

        if (!mTransaction.getSplitTransactionList().isEmpty() && mCategoryComboBox.isVisible()) {
            // has split, unset category or transfer
            mCategoryComboBox.getSelectionModel().select(Integer.valueOf(0));
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
            mTransaction.getSplitTransactionList().clear();
            mMatchInfoList.clear();
            mSplitTransactionListChanged = false;
            mTransactionOrig = null;
            handleClear();
            Platform.runLater(() -> mTradeActionChoiceBox.requestFocus());
        }
    }
}
