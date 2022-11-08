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

import impl.org.controlsfx.autocompletion.AutoCompletionTextFieldBinding;
import impl.org.controlsfx.autocompletion.SuggestionProvider;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.util.converter.BigDecimalStringConverter;
import net.taihuapp.pachira.dao.DaoException;
import net.taihuapp.pachira.dao.DaoManager;
import org.apache.log4j.Logger;
import org.controlsfx.control.textfield.TextFields;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.taihuapp.pachira.Transaction.TradeAction.*;

public class EditTransactionDialogControllerNew {
    private static final Logger mLogger = Logger.getLogger(EditTransactionDialogControllerNew.class);

    static class CategoryTransferAccountIDComboBoxWrapper {
        private final ComboBox<Integer> mComboBox;
        private final FilteredList<Integer> mFilteredCTIDList;
        private final SuggestionProvider<String> mProvider;

        // the input combobox should be a naked one.
        CategoryTransferAccountIDComboBoxWrapper(final ComboBox<Integer> comboBox, final MainModel mainModel) {
            mComboBox = comboBox;

            // set converter first
            mComboBox.setConverter(new ConverterUtil.CategoryIDConverter(mainModel));

            // populate combobox items
            ObservableList<Integer> idList = FXCollections.observableArrayList();
            idList.add(0);
            for (Category category : mainModel.getCategoryList())
                idList.add(category.getID());
            for (Account account : mainModel.getAccountList(a -> !a.getHiddenFlag()
                            && !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME),
                    Comparator.comparing(Account::getType).thenComparing(Account::getDisplayOrder)))
                idList.add(-account.getID());

            mFilteredCTIDList = new FilteredList<>(idList);
            mComboBox.setItems(mFilteredCTIDList);

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

        void setFilter(Predicate<Integer> predicate) {
            Integer selectedValue = mComboBox.getSelectionModel().getSelectedItem();

            mFilteredCTIDList.setPredicate(predicate);
            mProvider.clearSuggestions();
            mProvider.addPossibleSuggestions(mFilteredCTIDList.stream()
                    .map(i -> mComboBox.getConverter().toString(i)).collect(Collectors.toList()));

            if (selectedValue != null && predicate.test(selectedValue)) {
                mComboBox.getSelectionModel().select(selectedValue);
            } else
                mComboBox.getSelectionModel().selectFirst();
        }

        void setFilter(boolean excludeCategory, int excludeAID) {

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

            setFilter(p);
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
    private Label splitLabel;
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
    private Label mNewSecurityNameLabel;
    @FXML
    private TextField mNewSecurityNameTextField;
    @FXML
    private Label mOldSecurityNameLabel;
    @FXML
    private ComboBox<Security> mOldSecurityComboBox;
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

    private MainModel mainModel;

    private Transaction mTransactionOrig;  // original copy
    private Transaction mTransaction;  // working copy
    private List<MatchInfo> mMatchInfoList = null;
    private boolean mSplitTransactionListChanged = false;

    private Stage getStage() { return (Stage) mTradeActionChoiceBox.getScene().getWindow(); }

    // clean data contained on invisible controls
    private void cleanInvisibleControlData() {
        if (!mADatePicker.isVisible())
            mTransaction.setADate(null);

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
            mTransaction.setQuantity(null);

        if (!mOldSharesTextField.isVisible())
            mTransaction.setOldQuantity(null);

        if (!mCommissionTextField.isVisible())
            mTransaction.setCommission(null);

        if (!mAccruedInterestTextField.isVisible())
            mTransaction.setAccruedInterest(null);

        if (!mSpecifyLotButton.isVisible())
            mMatchInfoList.clear();
        else {
            // even if specify lot button is visible, but if the lotInfo is not
            // valid, still clear is.
            boolean isOK = true;
            BigDecimal totalQuantity = BigDecimal.ZERO;
            for (MatchInfo mi : mMatchInfoList) {
                Transaction transaction = mainModel.getTransaction(t -> t.getID() == mi.getMatchTransactionID())
                        .orElse(null);
                // it's not OK if
                // 1) transaction is null, 2) transaction trade date is after mTransaction trade date
                // 3) transaction trade date is the same as mTransaction trade date
                //    AND transaction id is greater than mTransaction id
                // 4) security name mismatch
                if (transaction == null || transaction.getTDate().isAfter(mTransaction.getTDate())
                        || (transaction.getTDate().isEqual(mTransaction.getTDate())
                            && (transaction.getID() > mTransaction.getID()))
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
    void setMainModel(MainModel mainModel, Transaction transaction, List<Account> accountList, Account defaultAccount,
                      List<Transaction.TradeAction> taList) throws DaoException {
        this.mainModel = mainModel;

        if (transaction == null) {
            mTransactionOrig = null;
            mTransaction = new Transaction(defaultAccount.getID(), LocalDate.now(),
                    defaultAccount.getType().isGroup(Account.Type.Group.INVESTING) ? BUY : WITHDRAW, 0);
        } else {
            mTransactionOrig = transaction.getID() > 0 ? transaction : null;
            mTransaction = new Transaction(transaction);
        }
        mMatchInfoList = mainModel.getMatchInfoList(mTransaction.getID());

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
        DatePickerUtil.captureEditedDate(mTDatePicker);
        mTDatePicker.valueProperty().bindBidirectional(mTransaction.getTDateProperty());

        // ADatePicker visible only for shares in.
        DatePickerUtil.captureEditedDate(mADatePicker);
        mADatePicker.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(SHRSIN));
        mADatePickerLabel.visibleProperty().bind(mADatePicker.visibleProperty());
        mADatePicker.valueProperty().bindBidirectional(mTransaction.getADateProperty());

        // account always visible
        mAccountComboBox.setConverter(new ConverterUtil.AccountConverter(mainModel));
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
        mCategoryComboBoxWrapper = new CategoryTransferAccountIDComboBoxWrapper(mCategoryComboBox, mainModel);
        mCategoryComboBoxWrapper.setFilter(mTransaction.getTradeAction() != DEPOSIT
                && mTransaction.getTradeAction() != WITHDRAW, defaultAccount.getID());

        // category combobox visibility
        mCategoryComboBox.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (!splitLabel.isVisible())
                    && (ta != STKSPLIT && ta != SHRSIN && ta != SHRSOUT && ta != REINVDIV && ta != REINVINT
                    && ta != REINVLG && ta != REINVMD && ta != REINVSH && ta != SHRCLSCVN && ta != CORPSPINOFF);
        }, mTradeActionChoiceBox.valueProperty(), splitLabel.visibleProperty()));
        mCategoryLabel.visibleProperty().bind(mCategoryComboBox.visibleProperty());
        mCategoryLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            switch (mTradeActionChoiceBox.getValue()) {
                case DEPOSIT:
                    return "Category/Transfer from:";
                case WITHDRAW:
                    return "Category/Transfer to:";
                case BUY:
                case MISCEXP:
                case CVTSHRT:
                case MARGINT:
                    return "Use cash from:";
                default:
                    return "Put cash to:";
            }
        }, mTradeActionChoiceBox.valueProperty()));
        mCategoryComboBox.valueProperty().bindBidirectional(mTransaction.getCategoryIDProperty());
        splitLabel.setVisible(!mTransaction.getSplitTransactionList().isEmpty());

        // memo always visible
        mMemoTextField.textProperty().bindBidirectional(mTransaction.getMemoProperty());

        // split transaction button, visible for X*, Deposit and Withdraw
        mSplitTransactionButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            // split is only allowed for cash transaction and if it is not linked to a split transaction
            return (ta == DEPOSIT || ta == WITHDRAW)
                    && (mTransactionOrig == null || mTransactionOrig.getMatchSplitID() <= 0);
        }, mTradeActionChoiceBox.valueProperty()));

        // tag
        mTagComboBox.setConverter(new ConverterUtil.TagIDConverter(mainModel));
        mTagComboBox.getItems().add(0);
        mTagComboBox.getItems().addAll(mainModel.getTagList().stream().map(Tag::getID).collect(Collectors.toList()));
        autoCompleteComboBox(mTagComboBox);
        mTagComboBox.setVisible(!defaultAccount.getType().isGroup(Account.Type.Group.INVESTING));
        mTagLabel.setVisible(defaultAccount.getType().isGroup(Account.Type.Group.INVESTING));
        mTagComboBox.valueProperty().bindBidirectional(mTransaction.getTagIDProperty());

        // reference
        mReferenceTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == DEPOSIT || ta == WITHDRAW);
        }, mTradeActionChoiceBox.valueProperty()));
        mReferenceLabel.visibleProperty().bind(mReferenceTextField.visibleProperty());
        mReferenceTextField.textProperty().bindBidirectional(mTransaction.getReferenceProperty());

        ConverterUtil.SecurityConverter securityConverter = new ConverterUtil.SecurityConverter(mainModel);

        // security combobox
        mSecurityComboBox.setConverter(securityConverter);
        TreeSet<Security> allSecuritySet = new TreeSet<>(Comparator.comparing(Security::getName));
        allSecuritySet.addAll(mainModel.getSecurityList());
        defaultAccount.getCurrentSecurityList().forEach(allSecuritySet::remove); // remove account security list
        mSecurityComboBox.getItems().add(null); // add a blank one first
        mSecurityComboBox.getItems().addAll(defaultAccount.getCurrentSecurityList());
        mSecurityComboBox.getItems().addAll(allSecuritySet);
        autoCompleteComboBox(mSecurityComboBox);
        mSecurityComboBox.visibleProperty().bind(mReferenceTextField.visibleProperty().not());
        mSecurityNameLabel.visibleProperty().bind(mSecurityComboBox.visibleProperty());
        mSecurityNameLabel.textProperty().bind(Bindings.createStringBinding(() ->
                mTradeActionChoiceBox.getValue() == SHRCLSCVN ? "New Security:" : "Security Name:",
                mTradeActionChoiceBox.valueProperty()));
        Security currentSecurity = mainModel.getSecurity(s -> s.getName().equals(mTransaction.getSecurityName()))
                .orElse(null);
        Bindings.bindBidirectional(mTransaction.getSecurityNameProperty(), mSecurityComboBox.valueProperty(),
                mSecurityComboBox.getConverter());
        mSecurityComboBox.getSelectionModel().select(currentSecurity);

        mNewSecurityNameTextField.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(CORPSPINOFF));
        mNewSecurityNameLabel.visibleProperty().bind(mNewSecurityNameTextField.visibleProperty());

        // old security stuff for Share class conversion
        mOldSecurityComboBox.setConverter(securityConverter);
        mOldSecurityComboBox.getItems().addAll(mSecurityComboBox.getItems());
        autoCompleteComboBox(mOldSecurityComboBox);
        mOldSecurityComboBox.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(SHRCLSCVN));
        mOldSecurityNameLabel.visibleProperty().bind(mOldSecurityComboBox.visibleProperty());

        // payee, same visibility as reference
        TextFields.bindAutoCompletion(mPayeeTextField, mainModel.getPayeeSet());
        mPayeeTextField.visibleProperty().bind(mReferenceTextField.visibleProperty());
        mPayeeLabel.visibleProperty().bind(mPayeeTextField.visibleProperty());
        mPayeeTextField.textProperty().bindBidirectional(mTransaction.getPayeeProperty());

        final Currency currency = Currency.getInstance("USD");  // hard code USD for now

        // Income
        final TextFormatter<BigDecimal> incomeTextFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency), null,
                c -> RegExUtil.getCurrencyInputRegEx(currency, false)
                        .matcher(c.getControlNewText()).matches() ? c : null);
        mIncomeTextField.setTextFormatter(incomeTextFormatter);
        mIncomeTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == REINVDIV || ta == REINVINT || ta == REINVLG || ta == REINVMD || ta == REINVSH
                    || ta == DIV || ta == INTINC || ta == CGLONG || ta == CGMID || ta == CGSHORT
                    || ta == MARGINT || ta == MISCINC || ta == MISCEXP || ta == RTRNCAP);
        }, mTradeActionChoiceBox.valueProperty()));
        mIncomeLabel.visibleProperty().bind(mIncomeTextField.visibleProperty());
        mIncomeLabel.textProperty().bind(mTradeActionChoiceBox.valueProperty().asString());
        incomeTextFormatter.valueProperty().bindBidirectional(mTransaction.getAmountProperty());

        // shares
        final TextFormatter<BigDecimal> sharesTextFormatter = new TextFormatter<>(
                ConverterUtil.getPriceQuantityStringConverterInstance(), null,
                c -> RegExUtil.getPriceQuantityInputRegEx().matcher(c.getControlNewText()).matches() ? c : null);
        mSharesTextField.setTextFormatter(sharesTextFormatter);
        sharesTextFormatter.valueProperty().bindBidirectional(mTransaction.getQuantityProperty());
        mSharesTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return (ta == BUY || ta == SELL || ta == REINVDIV || ta == REINVINT || ta == REINVLG
                    || ta == REINVMD || ta == REINVSH || ta == STKSPLIT || ta == SHRSIN || ta == SHRCLSCVN
                    || ta == SHRSOUT || ta == SHTSELL || ta == CVTSHRT || ta == CORPSPINOFF);
        }, mTradeActionChoiceBox.valueProperty()));
        mSharesLabel.visibleProperty().bind(mSharesTextField.visibleProperty());
        mSharesLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            if (ta == STKSPLIT || ta == SHRCLSCVN)
                return "New Shares:";
            if (ta == CORPSPINOFF)
                return "New Shares/Old Share:";
            return "Number of Shares:";
        }, mTradeActionChoiceBox.valueProperty()));

        // old shares
        final TextFormatter<BigDecimal> oldSharesTextFormatter = new TextFormatter<>(
                ConverterUtil.getPriceQuantityStringConverterInstance(), null,
                c -> RegExUtil.getPriceQuantityInputRegEx().matcher(c.getControlNewText()).matches() ? c : null);
        mOldSharesTextField.setTextFormatter(oldSharesTextFormatter);
        oldSharesTextFormatter.valueProperty().bindBidirectional(mTransaction.getOldQuantityProperty());
        mOldSharesTextField.visibleProperty().bind(mTradeActionChoiceBox.valueProperty().isEqualTo(STKSPLIT)
                .or(mTradeActionChoiceBox.valueProperty().isEqualTo(SHRCLSCVN)));
        mOldSharesLabel.visibleProperty().bind(mOldSharesTextField.visibleProperty());


        // price is always calculated
        mPriceTextField.visibleProperty().bind(Bindings.createBooleanBinding(()
                -> Transaction.hasQuantity(mTradeActionChoiceBox.getValue()), mTradeActionChoiceBox.valueProperty()));
        mPriceTextField.textProperty().bind(Bindings.createStringBinding(() ->
                        ConverterUtil.getPriceQuantityFormatInstance().format(mTransaction.getPrice()),
                mTransaction.getPriceProperty()));
        mPriceLabel.visibleProperty().bind(mPriceTextField.visibleProperty());

        // commission, same visibility as price, except in Share Class Conversion and Corp Spin Off
        // For Corp Spin Off, this field is for input Old Share Price
        final TextFormatter<BigDecimal> commissionTextFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency), null,
                c -> RegExUtil.getCurrencyInputRegEx(currency, false)
                        .matcher(c.getControlNewText()).matches() ? c : null);
        mCommissionTextField.setTextFormatter(commissionTextFormatter);
        commissionTextFormatter.valueProperty().bindBidirectional(mTransaction.getCommissionProperty());
        mCommissionTextField.visibleProperty().bind(mPriceTextField.visibleProperty()
                .and(mTradeActionChoiceBox.valueProperty().isNotEqualTo(SHRCLSCVN))
                .or(mTradeActionChoiceBox.valueProperty().isEqualTo(CORPSPINOFF)));
        mCommissionLabel.visibleProperty().bind(mCommissionTextField.visibleProperty());
        mCommissionLabel.textProperty().bind(Bindings.createStringBinding(() ->
                mTradeActionChoiceBox.getValue() == CORPSPINOFF ?
                        "Old Share Price:" : "Commission:", mTradeActionChoiceBox.valueProperty()));

        // accrued interest, same visibility as commission, except corp spin off
        // For Corp Spin Off, accrued interest field is for input New Share Price
        final TextFormatter<BigDecimal> accruedInterestTextFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency), null,
                c -> RegExUtil.getCurrencyInputRegEx(currency, false)
                        .matcher(c.getControlNewText()).matches() ? c : null);
        mAccruedInterestTextField.setTextFormatter(accruedInterestTextFormatter);
        accruedInterestTextFormatter.valueProperty().bindBidirectional(mTransaction.getAccruedInterestProperty());
        mAccruedInterestTextField.visibleProperty().bind(mCommissionTextField.visibleProperty());
        mAccruedInterestLabel.visibleProperty().bind(mAccruedInterestTextField.visibleProperty());
        mAccruedInterestLabel.textProperty().bind(Bindings.createStringBinding(() ->
                mTradeActionChoiceBox.getValue() == CORPSPINOFF ?
                        "New Share Price:" : "Accrued Interest:", mTradeActionChoiceBox.valueProperty()));

        // specify lots button
        mSpecifyLotButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == SELL || ta == CVTSHRT || ta == SHRSOUT;
        }, mTradeActionChoiceBox.valueProperty()));

        // total cost
        final TextFormatter<BigDecimal> totalTextFormatter = new TextFormatter<>(
                ConverterUtil.getCurrencyAmountStringConverterInstance(currency), null,
                c -> RegExUtil.getCurrencyInputRegEx(currency, false)
                        .matcher(c.getControlNewText()).matches() ? c : null);
        mTotalTextField.setTextFormatter(totalTextFormatter);
        totalTextFormatter.valueProperty().bindBidirectional(mTransaction.getAmountProperty());
        mTotalTextField.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            final Transaction.TradeAction ta = mTradeActionChoiceBox.getValue();
            return ta == BUY || ta == SELL || ta == SHRSIN || ta == SHTSELL || ta == CVTSHRT
                    || ta == DEPOSIT || ta == WITHDRAW || ta == SHRCLSCVN;
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

        // set default button
        // if transaction != null, it is editing an existing transaction
        // or entering a reminder transaction, in this case, set default
        // to enter-done, don't even show enter-new button.
        // either we are editing an existing transaction or
        // enter a reminder transaction, don't do enter-new
        boolean defaultEnterDone = transaction != null;
        mEnterDoneButton.setDefaultButton(defaultEnterDone);
        mEnterNewButton.setDefaultButton(!defaultEnterDone);
        mEnterNewButton.setVisible(!defaultEnterDone);

        Platform.runLater(() -> mTradeActionChoiceBox.requestFocus());
    }

    private boolean enterCorpSpinOffTransaction() {
        final Security oldSecurity = mSecurityComboBox.getValue();
        if (oldSecurity == null) {
            DialogUtil.showWarningDialog(getStage(), "Empty Security",
                    "Corporate Spin-Off Needs A Security", "Please select a valid security.");
            return false;
        }

        final LocalDate date = mTDatePicker.getValue();
        final String newSecurityName = mNewSecurityNameTextField.getText().trim();
        if (newSecurityName.isEmpty()) {
            DialogUtil.showWarningDialog(getStage(), "Empty New Security Name",
                    "Corporate spin-off needs a name for the new security",
                    "Please enter a valid name");
            return false;
        }

        final Optional<Security> newSecurity = mainModel.getSecurity(s -> s.getName().equals(newSecurityName));
        if (newSecurity.isPresent()) {
            final boolean isOK = DialogUtil.showConfirmationDialog(getStage(), "Spin-Off Security Exist",
                    "The spin-off security already exist.",
                    "    Security Name: " + newSecurityName + System.lineSeparator() +
                            "    Ticker Symbol: " + newSecurity.get().getTicker() + System.lineSeparator() +
                    "Is this correct?");
            if (!isOK)
                return false;
        }

        final BigDecimalStringConverter pqStringConverter = ConverterUtil.getPriceQuantityStringConverterInstance();
        final BigDecimal newShares = pqStringConverter.fromString(mSharesTextField.getText());
        final BigDecimal oldSharePrice = pqStringConverter.fromString(mCommissionTextField.getText());
        final BigDecimal newSharePrice = pqStringConverter.fromString(mAccruedInterestTextField.getText());

        if (newShares == null || newShares.compareTo(BigDecimal.ZERO) <= 0) {
            DialogUtil.showWarningDialog(getStage(), "Invalid New Share Quantity",
                    "Number of new shares per old shares should be positive.",
                    "Please enter correct number of new shares issued per old share.");
            return false;
        }

        if (oldSharePrice == null || oldSharePrice.compareTo(BigDecimal.ZERO) <= 0) {
            DialogUtil.showWarningDialog(getStage(), "Invalid Price",
                    "Price for old security should be positive",
                    "Please enter correct price for the old security.");
            return false;
        }

        if (newSharePrice == null || newSharePrice.compareTo(BigDecimal.ZERO) <= 0) {
            DialogUtil.showWarningDialog(getStage(), "Invalid Price",
                    "Price for new security should be positive",
                    "Please enter correct price for the new security.");
            return false;
        }

        final String memo = mMemoTextField.getText().trim();
        try {
            return mainModel.enterCorpSpinOffTransaction(date, oldSecurity, newSecurityName, newShares,
                    oldSharePrice, newSharePrice, memo);
        } catch (DaoException e) {
            final String msg = "DaoException " + e.getErrorCode();
            mLogger.error(msg, e);
            DialogUtil.showExceptionDialog(getStage(), e.getClass().getName(), msg, e.toString(), e);
            return false;
        }
    }

    // special code to handle entering Share Conversion transactions
    // An Share Conversion transaction is break into a Shares out and
    // a set of share in transactions.
    private boolean enterShareClassConversionTransaction() {
        String header;
        // validate input data
        Security newSecurity = mSecurityComboBox.getValue();
        if (newSecurity == null) {
            header = "Empty new security";
            mLogger.warn(header);
            showWarningDialog(header, "Please select a valid New Security.");
            return false;
        }
        Security oldSecurity = mOldSecurityComboBox.getValue();
        if (oldSecurity == null) {
            header = "Empty old security";
            mLogger.warn(header);
            showWarningDialog(header, "Please select a valid Old Security.");
            return false;
        }

        if (oldSecurity.getID() == newSecurity.getID()) {
            header = "Save Securities";
            mLogger.warn(header);
            showWarningDialog(header, "Old security cannot be the same as the new security.");
            return false;
        }

        final BigDecimalStringConverter pqStringConverter = ConverterUtil.getPriceQuantityStringConverterInstance();
        final BigDecimal newShares = pqStringConverter.fromString(mSharesTextField.getText());
        if (newShares == null || newShares.compareTo(BigDecimal.ZERO) <= 0) {
            header = "Non positive new shares";
            mLogger.warn(header);
            showWarningDialog(header, "Please enter a positive number for New Shares");
            return false;
        }
        final BigDecimal oldShares = pqStringConverter.fromString(mOldSharesTextField.getText());
        if (oldShares == null || oldShares.compareTo(BigDecimal.ZERO) <= 0) {
            header = "Non positive old shares";
            mLogger.warn(header);
            showWarningDialog(header, "Please enter a positive number for Old Shares");
            return false;
        }

        final List<Transaction> transactionList = new ArrayList<>();  // this is the list transaction to be entered
        Account account = mAccountComboBox.getValue();
        LocalDate tDate = mTransaction.getTDate();
        BigDecimal newPrice = mTransaction.getPrice();
        int categoryID = 0;
        int tagID = 0;
        int matchID = 0;
        int matchSplitID = 0;

        final String memo = mTransaction.getMemo().isEmpty() ? "Share class conversion" : mTransaction.getMemo();
        List<SecurityHolding> shList;
        try {
            shList = mainModel.computeSecurityHoldings(account.getTransactionList(), tDate, mTransaction.getID());
        } catch (DaoException e) {
            mLogger.error("Failed to computer Security holdings for Account " + account.getName(), e);
            return false;
        }
        for (SecurityHolding sh : shList) {
            if (sh.getSecurityName().equals(oldSecurity.getName())) {
                // we have holdings for old security
                BigDecimal oldQuantity = sh.getQuantity();
                for (SecurityLot li : sh.getSecurityLotList()) {
                    BigDecimal costBasis = li.getCostBasis();
                    BigDecimal oldLotQuantity = li.getQuantity();

                    final BigDecimal newLotQuantity = (oldLotQuantity.signum() != 0) ?
                            oldLotQuantity.multiply(newShares).divide(oldShares,
                                    MainModel.PRICE_QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    transactionList.add(new Transaction(-1, account.getID(), tDate, li.getDate(), SHRSIN,
                            Transaction.Status.UNCLEARED, newSecurity.getName(), "", "",
                            newLotQuantity, BigDecimal.ZERO, memo, BigDecimal.ZERO,
                            BigDecimal.ZERO, costBasis, categoryID, tagID, matchID, matchSplitID, null, ""));
                }
                transactionList.add(new Transaction(-1, account.getID(), tDate, tDate, SHRSOUT,
                        Transaction.Status.UNCLEARED, oldSecurity.getName(), "", "",
                        oldQuantity, BigDecimal.ZERO, memo, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, categoryID, tagID, matchID, matchSplitID, null, ""));
            }
        }

        // database work here, this really should be encapsulated in model class
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();

            // insert to DB first
            for (Transaction t : transactionList) {
                mainModel.insertTransaction(t, MainModel.InsertMode.DB_ONLY);
            }

            // delete original transaction from DB and Master list
            mainModel.alterTransaction(null, mTransactionOrig, mMatchInfoList);

            // save price for the new security if there isn't a price for the same day
            if (mainModel.getSecurityPrice(new Pair<>(newSecurity, tDate)).isEmpty()) {
                mainModel.insertSecurityPrice(new Pair<>(newSecurity, new Price(tDate, newPrice)));
            }

            daoManager.commit();

            for (Transaction t : transactionList) {
                mainModel.insertTransaction(t, MainModel.InsertMode.MEM_ONLY);
            }

            // we are done
            return true;
        } catch (DaoException | ModelException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }

            mLogger.error(e.getMessage(), e);
            DialogUtil.showExceptionDialog(getStage(), "Exception", "Failed to insert transaction",
                    e.toString(), e);
        }
        return false;
    }

    // enter transaction to database and master list
    // return true of successful, false otherwise
    private boolean enterTransaction() {
        if (mTradeActionChoiceBox.getValue() == SHRCLSCVN)
            return enterShareClassConversionTransaction();

        if (mTradeActionChoiceBox.getValue() == CORPSPINOFF)
            return enterCorpSpinOffTransaction();

        // accountID is not automatically updated, update now
        mTransaction.setAccountID(mAccountComboBox.getSelectionModel().getSelectedItem().getID());

        // clean data attached to invisible controls
        cleanInvisibleControlData();

        // validate transaction now
        if (!validateTransaction())
            return false;

        // most work is done by alterTransaction method
        try {
            mainModel.alterTransaction(mTransactionOrig, mTransaction, mMatchInfoList);
            return true;
        } catch (DaoException | ModelException e) {
            mLogger.error("Failed to enter transaction", e);
            DialogUtil.showExceptionDialog(getStage(),"Exception", "Failed to enter transaction",
                    e.getMessage(), e);
            return false;
        }
    }

    // maybe this logic should be moved to Transaction class
    private boolean validateTransaction() {

        final Transaction.TradeAction ta = mTransaction.getTradeAction();
        final int accountID = mTransaction.getAccountID();
        final int categoryID = mTransaction.getCategoryID();

        if (mTotalTextField.isVisible() && mTransaction.getAmount() == null) {
            mLogger.warn("Amount cannot be empty.");
            showWarningDialog("Amount cannot be empty",
                    "Please enter a valid amount");
            return false;
        }

        // check transfer account
        if (categoryID < 0) {
            if (accountID == -categoryID) {
                mLogger.warn("Self Transfer Transaction not allowed");
                showWarningDialog("Self transfer transaction not allowed",
                        "Please make sure transfer account differs from originating account");
                return false;
            }
            if (mainModel.getAccount(a -> a.getID() == -categoryID).isEmpty()
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
            Account account = mainModel.getAccount(a -> a.getID() == accountID).orElse(null);
            if (account == null) {
                showWarningDialog("Invalid Account ID", "Account ID " + accountID + " is not valid");
                return false;
            }

            try {
                List<SecurityHolding> securityHoldingList =
                        mainModel.computeSecurityHoldings(account.getTransactionList(),
                                mTransaction.getTDate(), mTransaction.getID());

                boolean hasEnough = false;
                for (SecurityHolding sh : securityHoldingList) {
                    if (sh.getSecurityName().equals(mTransaction.getSecurityName())) {
                        // we have matching security position, check
                        // sh.getQuantity is signed, mTransaction getQuantity is always positive
                        if (((ta == SELL || ta == SHRSOUT) && sh.getQuantity()
                                .compareTo(mTransaction.getQuantity()) >= 0)
                                || (ta == CVTSHRT && sh.getQuantity()
                                .compareTo(mTransaction.getQuantity().negate()) <= 0)) {
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
                        header = "Short cover quantity exceeded existing short quantity";
                    String content = "Please check trade quantity";
                    showWarningDialog(header, content);
                    return false;
                }
            } catch (DaoException e) {
                mLogger.error("DaoException " + e.getErrorCode(), e);
                DialogUtil.showExceptionDialog(getStage(), "Exception", "DaoException " + e.getErrorCode(),
                        e.toString(), e);
                return false;
            }
        }

        // check ADate for SHRSIN
        if (ta == SHRSIN) {
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
        Security security = mainModel.getSecurity(s -> s.getName().equals(securityName)).orElse(null);
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
        Stage stage = getStage();
        try {
            DialogUtil.showSpecifyLotsDialog(mainModel, stage, mTransaction, mMatchInfoList);
        } catch (IOException e) {
            mLogger.error("IOException on showSpecifyLotsDialog", e);
            DialogUtil.showExceptionDialog(stage, "IOException",
                    "IOException encountered on showSpecifyLotsDialog", e.getMessage(), e);
        } catch (DaoException e) {
            mLogger.error("DaoException " + e.getErrorCode() + " on showSpecifyLotsDialog", e);
            DialogUtil.showExceptionDialog(stage, "DaoException",
                    e.getErrorCode() + " on showSpecifyLotsDialog", e.toString(), e);
        }
    }

    @FXML
    private void handleSplitTransactions() {
        final Account account = mAccountComboBox.getValue();
        final int accountID = account == null ? -1 : account.getID();
        Stage stage = getStage();
        try {
            List<SplitTransaction> outSplitTransactionList = DialogUtil.showSplitTransactionsDialog(mainModel,
                    stage, accountID, mTransaction.getSplitTransactionList(), "",
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
                mTransaction.setMatchID(st.getMatchID(), -1);

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

            splitLabel.setVisible(!mTransaction.getSplitTransactionList().isEmpty());
        } catch (IOException e) {
            DialogUtil.showExceptionDialog(stage, "IOException",
                    "IOException encountered when opening SplitTransactionDialog", e.getMessage(), e);
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

        getStage().close();
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
            getStage().close();
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
