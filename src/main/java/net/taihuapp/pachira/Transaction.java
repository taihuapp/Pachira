/*
 * Copyright (C) 2018.  Guangliang He.  All Rights Reserved.
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
import javafx.beans.property.*;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public class Transaction {

    // a class object returned by validate() method
    //
    static class ValidationStatus {
        private final boolean mIsValid;
        private final String mMessage;

        ValidationStatus(boolean p, String m) {
            mIsValid = p;
            mMessage = m;
        }

        boolean isValid() { return mIsValid; }
        String getMessage() { return mMessage; }
    }

    private static final Logger mLogger = Logger.getLogger(Transaction.class);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return ((mID > 0) && (that.mID > 0) && (mID == that.mID));

    }

    @Override
    public int hashCode() {

        return Objects.hash(mID);
    }

    enum Status {
        UNCLEARED, CLEARED, RECONCILED;

        public char toChar() {
            switch (this) {
                case CLEARED:
                    return 'c';
                case RECONCILED:
                    return 'R';
                default:
                    return ' ';
            }
        }

        @Override
        public String toString() {
            switch (this) {
                case CLEARED:
                    return "Cleared";
                case RECONCILED:
                    return "Reconciled";
                default:
                    return "Uncleared";
            }
        }
    }

    enum TradeAction {
        BUY("Buy Shares"), SELL("Sell Shares"), DIV("Dividend"), REINVDIV("Reinvest Dividend"),
        INTINC("Interest"), REINVINT("Reinvest Interest"),
        CGLONG("Long-term Cap Gain"), CGMID("Mid-term Cap Gain"), CGSHORT("Short-term Cap Gain"),
        REINVLG("Reinvest LT Cap Gain"), REINVMD("Reinvest Mid-term Cap Gain"), REINVSH("Reinvest Short-term Cap Gain"),
        STKSPLIT("Stock Split"), SHRSIN("Shares Transferred In"), SHRSOUT("Shares Transferred Out"),
        MISCEXP("Misc Expense"), MISCINC("Misc Income"), RTRNCAP("Return Capital"),
        SHTSELL("Short Sell"), CVTSHRT("Cover Short Sell"), MARGINT("Margin Interest"),
        XFRSHRS("Shares Transferred"), XIN("Cash Transferred In"), XOUT("Cash Transferred Out"),
        DEPOSIT("Deposit"), WITHDRAW("Withdraw");

        private String mValue;
        TradeAction(String v) { mValue = v; }

        @Override
        public String toString() { return mValue; }
    }

    private int mID = -1;
    private int mAccountID;
    private final ObjectProperty<LocalDate> mTDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> mADateProperty = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Status> mStatusProperty = new SimpleObjectProperty<>(Status.UNCLEARED);
    private final ObjectProperty<TradeAction> mTradeActionProperty = new SimpleObjectProperty<>(TradeAction.BUY);
    private final StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private final StringProperty mReferenceProperty = new SimpleStringProperty("");
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    // amount property,
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);  // this is amount
    // cash amount, derived from total amount
    private final ObjectProperty<BigDecimal> mCashAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mPaymentProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mDepositProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTagIDProperty = new SimpleIntegerProperty(0);
    private final ObjectProperty<BigDecimal> mBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // investment amount, derived from total amount
    private final ObjectProperty<BigDecimal> mInvestAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mCommissionProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mAccuedInterestProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mSignedQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mOldQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mDescriptionProperty = new SimpleStringProperty("");
    private final StringProperty mFITIDProperty = new SimpleStringProperty("");
    private int mMatchID = -1; // transfer transaction id
    private int mMatchSplitID = -1;

    private final List<SplitTransaction> mSplitTransactionList = new ArrayList<>();

    // getters
    int getID() { return mID; }
    int getAccountID() { return mAccountID; }
    ObjectProperty<LocalDate> getADateProperty() { return mADateProperty; }
    StringProperty getPayeeProperty() { return mPayeeProperty; }
    String getPayee() { return getPayeeProperty().get(); }
    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    IntegerProperty getTagIDProperty() { return mTagIDProperty; }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    ObjectProperty<BigDecimal> getInvestAmountProperty() { return mInvestAmountProperty; }
    ObjectProperty<BigDecimal> getCashAmountProperty() { return mCashAmountProperty; }
    ObjectProperty<BigDecimal> getPaymentProperty() { return mPaymentProperty; }
    ObjectProperty<BigDecimal> getDepositeProperty() { return mDepositProperty; }
    ObjectProperty<BigDecimal> getCommissionProperty() { return mCommissionProperty; }
    ObjectProperty<BigDecimal> getAccruedInterestProperty() { return mAccuedInterestProperty; }
    ObjectProperty<BigDecimal> getBalanceProperty() { return mBalanceProperty; }
    ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    ObjectProperty<BigDecimal> getSignedQuantityProperty() { return mSignedQuantityProperty; }
    ObjectProperty<BigDecimal> getOldQuantityProperty() { return mOldQuantityProperty; }
    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }

    ObjectProperty<LocalDate> getTDateProperty() { return mTDateProperty; }
    LocalDate getTDate() { return getTDateProperty().get(); }
    private void setTDate(LocalDate td) { getTDateProperty().set(td); }

    StringProperty getReferenceProperty() { return mReferenceProperty; }
    String getReference() { return getReferenceProperty().get(); }
    void setReference(String r) { getReferenceProperty().set(r); }

    ObjectProperty<Status> getStatusProperty() { return mStatusProperty; }
    Status getStatus() { return getStatusProperty().get(); }
    void setStatus(Status s) { getStatusProperty().set(s); }

    private StringProperty getFITIDProperty() { return mFITIDProperty; }
    String getFITID() { return getFITIDProperty().get(); }
    void setFIDID(String fitid) { getFITIDProperty().set(fitid); }

    ObjectProperty<TradeAction> getTradeActionProperty() { return mTradeActionProperty; }
    TradeAction getTradeAction() { return getTradeActionProperty().get();}
    StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }

    private void bindDescriptionProperty() {
        // first build a converter
        final Callable<String> converter = () -> {
            switch (getTradeAction()) {
                case BUY:
                case CVTSHRT:
                case SELL:
                case SHTSELL:
                case SHRSIN:
                case REINVDIV:
                case REINVINT:
                case REINVLG:
                case REINVMD:
                case REINVSH:
                    return (getQuantity() == null ? "" : getQuantity().stripTrailingZeros().toPlainString())
                            + " shares @ "
                            + (getPrice() == null ? "" : getPrice().stripTrailingZeros().toPlainString());
                case STKSPLIT:
                    return (getQuantity() == null ? "" : getQuantity().stripTrailingZeros().toPlainString())
                            + " for "
                            + (getOldQuantity() == null ? "" : getOldQuantity().stripTrailingZeros().toPlainString())
                            + " split";
                case MARGINT:
                case MISCEXP:
                case MISCINC:
                case DEPOSIT:
                case WITHDRAW:
                case DIV:
                case CGLONG:
                case CGMID:
                case CGSHORT:
                case INTINC:
                case RTRNCAP:
                    return mAmountProperty.get().stripTrailingZeros().toPlainString();
                case XIN:
                case XOUT:
                    return getPayee();
                case SHRSOUT:
                    return getQuantity().stripTrailingZeros().toPlainString() + " shares";
                case XFRSHRS:
                default:
                    return "description for [" + getTradeAction() + "] Transaction not implemented yet.";
            }
        };

        // bind now
        mDescriptionProperty.bind(Bindings.createStringBinding(converter,
                mTradeActionProperty, mQuantityProperty, mPriceProperty, mOldQuantityProperty));
    }

    private void bindProperties() {
        // SignedQuantity depends on TradeAction and Quantity
        mSignedQuantityProperty.bind(Bindings.createObjectBinding(() -> {
            switch (getTradeAction()) {
                case SELL:
                case SHTSELL:
                case SHRSOUT:
                    return getQuantity().negate();
                case BUY:
                case CVTSHRT:
                case DEPOSIT:
                case DIV:
                case CGLONG:
                case CGMID:
                case CGSHORT:
                case INTINC:
                case MISCINC:
                case MISCEXP:
                case REINVDIV:
                case REINVINT:
                case REINVSH:
                case REINVMD:
                case REINVLG:
                case RTRNCAP:
                case SHRSIN:
                case WITHDRAW:
                case XIN:
                case XOUT:
                case MARGINT:
                case STKSPLIT:
                    return getQuantity();
                case XFRSHRS:
                default:
                    mLogger.error("getSignedQuantity not implemented for " + getTradeAction());
                    return getQuantity();
            }
        }, mTradeActionProperty, mQuantityProperty));

        // mCashAmountProperty and mInvestAmountProperty depends on mTradeActionProperty and mAmountProperty
        mCashAmountProperty.bind(Bindings.createObjectBinding(() -> {
            switch (getTradeAction()) {
                case BUY:
                case CVTSHRT:
                case MARGINT:
                case MISCEXP:
                    return isTransfer() ? BigDecimal.ZERO : getAmount().negate();
                case SELL:
                case SHTSELL:
                case CGLONG:
                case CGMID:
                case CGSHORT:
                case DIV:
                case INTINC:
                case MISCINC:
                case RTRNCAP:
                    return isTransfer() ? BigDecimal.ZERO : getAmount();
                case REINVDIV:
                case REINVINT:
                case REINVLG:
                case REINVMD:
                case REINVSH:
                case STKSPLIT:
                case SHRSIN:
                case SHRSOUT:
                case XFRSHRS:
                    return BigDecimal.ZERO;
                case XIN:
                case DEPOSIT:
                    return getAmount();
                case XOUT:
                case WITHDRAW:
                    return getAmount().negate();
                default:
                    mLogger.error("TradingAction " + getTradeAction() + " not implement yet");
                    return BigDecimal.ZERO;
            }
        }, getTradeActionProperty(), getAmountProperty(), getCategoryIDProperty()));

        mInvestAmountProperty.bind(Bindings.createObjectBinding(() -> {
            switch (getTradeAction()) {
                case BUY:
                case CVTSHRT:
                case REINVDIV:
                case REINVINT:
                case REINVLG:
                case REINVMD:
                case REINVSH:
                case SHRSIN:
                    return getAmount();
                case SELL:
                case SHTSELL:
                case SHRSOUT:
                case RTRNCAP:
                    return getAmount().negate();
                case CGLONG:
                case CGMID:
                case CGSHORT:
                case DIV:
                case INTINC:
                case MARGINT:
                case MISCEXP:
                case MISCINC:
                case STKSPLIT:
                case XFRSHRS:
                case XIN:
                case DEPOSIT:
                case XOUT:
                case WITHDRAW:
                    return BigDecimal.ZERO;
                default:
                    mLogger.error("TradingAction " + getTradeAction() + " not implement yet");
                    return BigDecimal.ZERO;
            }
        }, getTradeActionProperty(), getAmountProperty(), getCategoryIDProperty()));

        mPaymentProperty.bind(Bindings.createObjectBinding(() -> {
            switch (getTradeAction()) {
                case XOUT:
                case WITHDRAW:
                    return getAmount();
                default:
                    return BigDecimal.ZERO;
            }
        }, getTradeActionProperty(), getAmountProperty()));

        mDepositProperty.bind(Bindings.createObjectBinding(() -> {
            switch (getTradeAction()) {
                case XIN:
                case DEPOSIT:
                    return getAmount();
                default:
                    return BigDecimal.ZERO;
            }
        }, getTradeActionProperty(), getAmountProperty()));
    }

    ValidationStatus validate() {
        // check if it is self transferring
        StringBuilder sb = new StringBuilder();
        boolean valid = true;
        if (getCategoryID() == -getAccountID()) {
            sb.append("Transfer to self: accountID = ").append(getAccountID()).append('\n');
            valid = false;
        }
        for (SplitTransaction st : getSplitTransactionList()) {
            if (st.getCategoryID() == -getAccountID()) {
                sb.append("Split transaction transfer to self: accountID = ").append(getAccountID()).append('\n');
                valid = false;
            }
        }

        return new ValidationStatus(valid, sb.toString());
    }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }

    LocalDate getADate() { return mADateProperty.get(); }
    BigDecimal getPrice() { return mPriceProperty.get(); }
    BigDecimal getQuantity() { return mQuantityProperty.get(); }
    BigDecimal getOldQuantity() { return getOldQuantityProperty().get(); }
    BigDecimal getCommission() { return getCommissionProperty().get(); }
    BigDecimal getAccruedInterest() { return getAccruedInterestProperty().get(); }
    BigDecimal getCostBasis() { return mInvestAmountProperty.get(); }
    String getSecurityName() { return mSecurityNameProperty.get();}
    BigDecimal getCashAmount() { return getCashAmountProperty().get(); }
    BigDecimal getInvestAmount() { return getInvestAmountProperty().get(); }
    List<SplitTransaction> getSplitTransactionList() { return mSplitTransactionList; }
    boolean isSplit() { return getSplitTransactionList().size() > 0; }
    BigDecimal getAmount() { return mAmountProperty.get(); }
    BigDecimal getPayment() { return mPaymentProperty.get(); }
    BigDecimal getDeposit() { return mDepositProperty.get(); }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    Integer getTagID() { return getTagIDProperty().get(); }
    int getMatchID() { return mMatchID; }  // this is for linked transactions
    int getMatchSplitID() { return mMatchSplitID; }

    // for a transferring transaction, return the trade action of the matching transaction
    final TradeAction TransferTradeAction() {
        if (-getCategoryID() < MainApp.MIN_ACCOUNT_ID) {
            // this is not a transferring transaction, return null
            return null;
        }

        switch (getTradeAction()) {
            case BUY:
            case CVTSHRT:
            case MISCEXP:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case DEPOSIT:
            case WITHDRAW:
            case XIN:
            case MARGINT:
                return TradeAction.XOUT;
            case DIV:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case INTINC:
            case MISCINC:
            case RTRNCAP:
            case SELL:
            case SHTSELL:
            case XOUT:
                return TradeAction.XIN;
            case SHRSIN:
                return TradeAction.SHRSOUT;
            case SHRSOUT:
                return TradeAction.SHRSIN;
            case STKSPLIT:
            case XFRSHRS:
                return null;
            default:
                mLogger.error("Transaction::TransferTradeAction: " + getTradeAction() + " not implemented yet.");
                return null;
        }
    }

    // return 1 if a tradeAction increase the cash balance in the account
    // return -1 if a tradeAction decrease the cash balance in the account
    // return 0 if a tradeAction has zero impact on cash balance

    BigDecimal cashFlow() {
        switch (getTradeAction()) {
            case BUY:
            case CVTSHRT:
            case MARGINT:
            case MISCEXP:
            case XIN:
            case DEPOSIT:
                return getAmount().negate();
            case DIV:
            case INTINC:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case MISCINC:
            case RTRNCAP:
            case SELL:
            case SHTSELL:
            case XOUT:
            case WITHDRAW:
                return getAmount();
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case STKSPLIT:
            case SHRSIN:
            case SHRSOUT:
            case XFRSHRS:
                return BigDecimal.ZERO;
            default:
                mLogger.error("TradingAction " + getTradeAction() + " not implement yet");
                return BigDecimal.ZERO;
        }
    }

    BigDecimal getSignedQuantity() { return getSignedQuantityProperty().get(); }

    // setters
    void setID(int id) { mID = id; }
    void setAccountID(int aid) { mAccountID = aid; }
    void setMatchID(int mid, int mSplitID) {
        mMatchID = mid;
        mMatchSplitID = mSplitID;
    }
    // use with caution.  amount often is bind with other property.
    void setAmount(BigDecimal amt) {
        getAmountProperty().set(amt);
    }
    void setPayee(String payee) { getPayeeProperty().set(payee); }

    void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    void setPrice(BigDecimal p) { mPriceProperty.set(p); }
    void setCommission(BigDecimal c) { mCommissionProperty.set(c); }
    void setSecurityName(String securityName) { mSecurityNameProperty.set(securityName); }
    void setMemo(String memo) { mMemoProperty.set(memo); }
    void setBalance(BigDecimal b) { mBalanceProperty.setValue(b); }
    private void setCategoryID(int cid) { mCategoryIDProperty.setValue(cid); }
    void setTagID(int tid) { mTagIDProperty.set(tid); }
    void setSplitTransactionList(List<SplitTransaction> stList) {
        mSplitTransactionList.clear();
        if (stList != null)
            for (SplitTransaction st : stList)
                mSplitTransactionList.add(new SplitTransaction(st));
    }

    // minimum constructor
    Transaction(int accountID, LocalDate date, TradeAction ta, int categoryID) {
        mAccountID = accountID;
        mTDateProperty.set(date);
        mTradeActionProperty.set(ta);
        setCategoryID(categoryID);
        mTagIDProperty.set(0); // unused for now.
        mSecurityNameProperty.set("");

        bindProperties();
        // bind description property now
        bindDescriptionProperty();
    }

    // Trade Transaction constructor
    // for all transactions, the amount is the notional amount, either 0 or positive
    // tradeAction can not be null
    public Transaction(int id, int accountID, LocalDate tDate, LocalDate aDate, TradeAction ta, Status s,
                       String securityName, String reference, String payee, BigDecimal price,
                       BigDecimal quantity, BigDecimal oldQuantity, String memo,
                       BigDecimal commission, BigDecimal accruedInterest, BigDecimal amount,
                       int categoryID, int tagID, int matchID, int matchSplitID, List<SplitTransaction> stList,
                       String fitid) {
        mID = id;
        mAccountID = accountID;
        mMatchID = matchID;
        mMatchSplitID = matchSplitID;
        mTDateProperty.set(tDate);
        mADateProperty.set(aDate);
        mSecurityNameProperty.set(securityName);
        mReferenceProperty.set(reference);
        mPriceProperty.set(price);
        mCommissionProperty.set(commission);
        mAccuedInterestProperty.set(accruedInterest);
        mMemoProperty.set(memo);
        mCategoryIDProperty.set(categoryID);
        mTagIDProperty.set(tagID);
        mPayeeProperty.set(payee);
        mOldQuantityProperty.set(oldQuantity);
        mQuantityProperty.set(quantity);
        mTradeActionProperty.set(ta);
        mStatusProperty.set(s);
        mAmountProperty.set(amount);
        if (stList != null) {
            for (SplitTransaction st : stList)
                mSplitTransactionList.add(new SplitTransaction(st));
        }
        mFITIDProperty.set(fitid);

        bindProperties();
        // bind description property now
        bindDescriptionProperty();
    }

    // copy constructor
    public Transaction(Transaction t0) {
        this(t0.getID(), t0.getAccountID(), t0.getTDate(), t0.getADate(), t0.getTradeAction(), t0.getStatus(),
                t0.getSecurityName(),
                t0.getReference(), t0.getPayeeProperty().get(), t0.getPrice(), t0.getQuantity(),
                t0.getOldQuantity(), t0.getMemo(), t0.getCommission(), t0.getAccruedInterest(), t0.getAmount(),
                t0.getCategoryID(), t0.getTagID(), t0.getMatchID(), t0.getMatchSplitID(),
                t0.getSplitTransactionList(), t0.getFITID());
    }

    // return false if this is NOT a transfer
    // also return false if this is a transfer to exAccountID
    // return true if this transaction is a transfer transaction to aother account
    boolean isTransfer() {
        int cid = getCategoryID();

        return !(cid > -MainApp.MIN_ACCOUNT_ID || cid == -getAccountID());
    }

    // return true if it is a cash transaction
    // i.e. mTradeAction is one of XIN, XOUT, DEPOSIT, WITHDRAW
    boolean isCash() {
        switch (getTradeAction()) {
            case XIN:
            case XOUT:
            case DEPOSIT:
            case WITHDRAW:
                return true;
            default:
                return false;
        }
    }

    // return true if the transaction may change quantity, false otherwise
    static boolean hasQuantity(TradeAction ta) {
        switch (ta) {
            case BUY:
            case SELL:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case STKSPLIT:
            case SHRSIN:
            case SHRSOUT:
            case SHTSELL:
            case CVTSHRT:
            case XFRSHRS:
                return true;
            case DIV:
            case INTINC:
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case MISCEXP:
            case MISCINC:
            case RTRNCAP:
            case MARGINT:
            case XIN:
            case XOUT:
            case DEPOSIT:
            case WITHDRAW:
                return false;
            default:
                return false;
        }
    }

    // Merge a downloaded transaction into a mannually entered transaction
    // transactionA is a manually entered transaction (getFITID().isEmpty() == true)
    // transactionB is a downloaded transaction (getFITID().isEmpty() == false)
    // the result transaction will keep the following from transactionA
    //   id, tradeAction
    // the result transaction will keep the following from transactionB
    //   Date, fitID, CHECKNUM (if not empty)
    // all other fields of the result transaction will have merged information
    static Transaction mergeDownloadedTransaction(final Transaction transactionA, final Transaction transactionB) {

        String fitIDA = transactionA.getFITID();
        String fitIDB = transactionB.getFITID();
        if (!fitIDA.isEmpty() || fitIDB.isEmpty())
            throw new IllegalArgumentException("mergeDownloadedTransaction expected "
                            + "a manually entered transaction and "
                            + "a downloaded transaction, but got a "
                            + (fitIDA.isEmpty() ? "manually entered" : "downloaded") + " transactioa "
                            + "and a "
                            + (fitIDB.isEmpty() ? "manually entered" : "downloaded") + " transactioa ");

        TradeAction taA = transactionA.getTradeAction();
        TradeAction taB = transactionB.getTradeAction();
        boolean tradeActionCompatible = false;
        switch (taA) {
            case DEPOSIT:
            case XIN:
                tradeActionCompatible = (taB == TradeAction.DEPOSIT || taB == TradeAction.XIN);
                break;
            case WITHDRAW:
            case XOUT:
                tradeActionCompatible = (taB == TradeAction.WITHDRAW || taB == TradeAction.XOUT);
                break;
            default:
                break;
        }
        if (!tradeActionCompatible)
            throw new IllegalArgumentException("Incompatible TradeActions: "
                    + taA.toString() + "/" + taB.toString());

        // copy over everything from transactionA first
        Transaction mergedTransaction = new Transaction(transactionA);

        // set Date
        mergedTransaction.setTDate(transactionB.getTDate());

        // set fitid
        mergedTransaction.setFIDID(transactionB.getFITID());

        // set category
        if (mergedTransaction.getCategoryID() > -MainApp.MIN_ACCOUNT_ID &&
                mergedTransaction.getCategoryID() < MainApp.MIN_CATEGORY_ID) {
            // mergedTransaction has an empty category field, copy from TransactionB
            mergedTransaction.setCategoryID(transactionB.getCategoryID());
        }

        // set Reference field
        String refStringB = transactionB.getReference();
        if (!(refStringB == null || refStringB.isEmpty())) {
            mergedTransaction.setReference(refStringB);
        }

        // set Payee field
        String payeeA = transactionA.getPayee();
        String payeeB = transactionB.getPayee();
        if (!(payeeB == null || payeeB.isEmpty())) {
            if (payeeA == null || payeeA.isEmpty())
                mergedTransaction.setPayee(payeeB);
            else
                mergedTransaction.setPayee(payeeA + " " + payeeB);
        }

        // set Memo field
        String memoA = transactionA.getMemo();
        String memoB = transactionB.getMemo();
        if (!(memoB == null || memoB.isEmpty())) {
            if (payeeA == null || payeeA.isEmpty())
                mergedTransaction.setMemo(memoB);
            else
                mergedTransaction.setMemo(memoA + " " + memoB);
        }

        return mergedTransaction;
    }
}