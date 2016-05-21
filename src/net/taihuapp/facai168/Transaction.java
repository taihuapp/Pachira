package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ghe on 4/9/15.
 * Transaction Class
 */

public class Transaction {

    enum TradeAction {
        BUY, BUYX, CGLONG, CGLONGX, CGMID, CGMIDX,
        CGSHORT, CGSHORTX, CVTSHRT, CVTSHRTX, DIV, DIVX, INTINC, INTINCX,
        MARGINT, MARGINTX, MISCEXP, MISCEXPX, MISCINC, MISCINCX,
        REINVDIV, REINVINT, REINVLG, REINVMD, REINVSH, RTRNCAP, RTRNCAPX,
        SELL, SELLX, SHRSIN, SHRSOUT, SHTSELL, SHTSELLX, STKSPLIT, STOCKDIV,
        XFRSHRS, XIN, XOUT, DEPOSIT, WITHDRAW, BUYBOND, BUYBONDX
    }

    private int mID = -1;
    private int mAccountID = -1;
    private final ObjectProperty<LocalDate> mTDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> mADateProperty = new SimpleObjectProperty<>(null);
    private StringProperty mTradeActionProperty = new SimpleStringProperty("BUY");
    private StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private final StringProperty mReferenceProperty = new SimpleStringProperty("");
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    // amount property,
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);  // this is amount
    // cash amount, derived from total amount
    private final ObjectProperty<BigDecimal> mCashAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mPaymentProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mDepositProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");
    private final StringProperty mCategoryProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // investment amount, derived from total amount
    private final ObjectProperty<BigDecimal> mInvestAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mCommissionProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mOldQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mDescriptionProperty = new SimpleStringProperty("");
    private int mMatchID = -1;
    private int mMatchSplitID = -1;
    // we use a Transaction object for holding a split transaction
    private final List<Transaction> mSplitTransactionList = new ArrayList<>();

    // getters
    int getID() { return mID; }
    int getAccountID() { return mAccountID; }
    ObjectProperty<LocalDate> getTDateProperty() { return mTDateProperty; }
    ObjectProperty<LocalDate> getADateProperty() { return mADateProperty; }
    StringProperty getReferenceProperty() { return mReferenceProperty; }
    StringProperty getPayeeProperty() { return mPayeeProperty; }
    StringProperty getMemoProperty() { return mMemoProperty; }
    StringProperty getCategoryProperty() { return mCategoryProperty; }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    ObjectProperty<BigDecimal> getInvestAmountProperty() { return mInvestAmountProperty; }
    ObjectProperty<BigDecimal> getCashAmountProperty() { return mCashAmountProperty; }
    ObjectProperty<BigDecimal> getPaymentProperty() { return mPaymentProperty; }
    ObjectProperty<BigDecimal> getDepositeProperty() { return mDepositProperty; }
    ObjectProperty<BigDecimal> getCommissionProperty() { return mCommissionProperty; }
    ObjectProperty<BigDecimal> getBalanceProperty() { return mBalanceProperty; }
    ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    ObjectProperty<BigDecimal> getOldQuantityProperty() { return mOldQuantityProperty; }
    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }

    StringProperty getTradeActionProperty() { return mTradeActionProperty; }
    StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }

    StringProperty getDescriptionProperty() {
        switch (TradeAction.valueOf(mTradeActionProperty.get())) {
            case BUY:
            case BUYX:
            case CVTSHRT:
            case CVTSHRTX:
            case SELL:
            case SELLX:
            case SHTSELL:
            case SHTSELLX:
            case SHRSIN:
            case REINVDIV:
                mDescriptionProperty.set(mQuantityProperty.get().stripTrailingZeros().toPlainString()
                        + " shares @ " + mPriceProperty.get().stripTrailingZeros().toPlainString());
                break;
            case STKSPLIT:
                mDescriptionProperty.set(getQuantity().stripTrailingZeros().toPlainString() + " for "
                        + getOldQuantity().stripTrailingZeros().toPlainString() + " split");
                break;
            case XIN:
            case XOUT:
            case DEPOSIT:
            case WITHDRAW:
            case DIV:
            case DIVX:
            case CGLONG:
            case CGLONGX:
            case CGMID:
            case CGMIDX:
            case CGSHORT:
            case CGSHORTX:
            case INTINC:
            case INTINCX:
                break;  // do nothing here
            default:
                mDescriptionProperty.set("description for this type Transaction not implemented yet.");
                break;
        }
        return mDescriptionProperty;
    }

    LocalDate getTDate() { return mTDateProperty.get(); }
    LocalDate getADate() { return mADateProperty.get(); }
    String getMemo() { return mMemoProperty.get(); }
    BigDecimal getPrice() { return mPriceProperty.get(); }
    BigDecimal getQuantity() { return mQuantityProperty.get(); }
    BigDecimal getOldQuantity() { return getOldQuantityProperty().get(); }
    BigDecimal getCommission() { return mCommissionProperty.get(); }
    BigDecimal getCostBasis() { return mInvestAmountProperty.get(); }
    String getSecurityName() { return mSecurityNameProperty.get();}
    BigDecimal getCashAmount() { return mCashAmountProperty.get(); }
    List<Transaction> getSplitTransactionList() { return mSplitTransactionList; }
    BigDecimal getAmount() { return mAmountProperty.get(); }
    String getTradeAction() { return getTradeActionProperty().get(); }
    String getCategory() { return getCategoryProperty().get(); }
    int getMatchID() { return mMatchID; }  // this is for linked transactions
    int getMatchSplitID() { return mMatchSplitID; }

    BigDecimal getSignedQuantity() {
        switch (TradeAction.valueOf(getTradeAction())) {
            case SELL:
            case SELLX:
            case SHTSELL:
            case SHTSELLX:
            case SHRSOUT:
                return getQuantity().negate();
            case BUY:
            case BUYX:
            case CVTSHRT:
            case CVTSHRTX:
            case SHRSIN:
            case DIV:
            case DIVX:
            case CGLONG:
            case CGLONGX:
            case CGMID:
            case CGMIDX:
            case CGSHORT:
            case CGSHORTX:
            case REINVDIV:
            case REINVINT:
            case REINVSH:
            case REINVMD:
            case REINVLG:
                return getQuantity();
            default:
                System.err.println("getSignedQuantity not implemented for " + getTradeAction());
                return getQuantity();
        }
    }

    // setters
    void setID(int id) { mID = id; }
    void setMatchID(int mid, int mSplitID) {
        mMatchID = mid;
        mMatchSplitID = mSplitID;
    }

    private void setTradeDetails(TradeAction ta, BigDecimal price, BigDecimal quantity,
                                 BigDecimal commission, BigDecimal amount) {
        mTradeActionProperty.set(ta.name());
        mAmountProperty.set(amount);
        mCommissionProperty.set(commission);
        mPriceProperty.set(price);
        switch (ta) {
            // todo
            // need to verify each
            case BUY:
            case BUYBOND:
            case CVTSHRT:
                mInvestAmountProperty.setValue(amount);
                mCashAmountProperty.setValue(amount.negate());
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
            case BUYX:
            case BUYBONDX:
            case CVTSHRTX:
            case REINVDIV:
            case REINVINT:
            case REINVLG:
            case REINVMD:
            case REINVSH:
            case STKSPLIT:
            case SHRSIN:
            case SHRSOUT:
            case STOCKDIV:
            case XFRSHRS:
                mInvestAmountProperty.setValue(amount);
                mCashAmountProperty.setValue(BigDecimal.ZERO);
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
            case SELL:
            case SHTSELL:
                mInvestAmountProperty.setValue(amount.negate());
                mCashAmountProperty.setValue(amount);
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
            case SELLX:
            case SHTSELLX:
                mInvestAmountProperty.setValue(amount.negate());
                mCashAmountProperty.setValue(BigDecimal.ZERO);
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
            case CGLONG:
            case CGMID:
            case CGSHORT:
            case DIV:
            case INTINC:
            case MARGINT:
            case MISCEXP:
            case MISCINC:
            case RTRNCAP:
            case XIN:
            case DEPOSIT:
                mInvestAmountProperty.setValue(BigDecimal.ZERO);
                mCashAmountProperty.setValue(amount);
                mQuantityProperty.set(null);
                mDepositProperty.set(amount);
                mPaymentProperty.set(null);
                break;
            case XOUT:
            case WITHDRAW:
                mInvestAmountProperty.setValue(BigDecimal.ZERO);
                mCashAmountProperty.setValue(amount.negate());
                mQuantityProperty.set(null);
                mDepositProperty.set(null);
                mPaymentProperty.set(amount);
                break;
            case CGLONGX:
            case CGMIDX:
            case CGSHORTX:
            case DIVX:
            case INTINCX:
            case MARGINTX:
            case MISCEXPX:
            case MISCINCX:
            case RTRNCAPX:
                mInvestAmountProperty.setValue(BigDecimal.ZERO);
                mCashAmountProperty.setValue(BigDecimal.ZERO);
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
            default:
                System.err.println("TradingAction " + ta.name() + " not implement yet");
                break;
        }
    }

    void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    void setPrice(BigDecimal p) { mPriceProperty.set(p); }
    void setCommission(BigDecimal c) { mCommissionProperty.set(c); }
    void setSecurityName(String securityName) { mSecurityNameProperty.set(securityName); }
    void setMemo(String memo) { mMemoProperty.set(memo); }
    void setBalance(BigDecimal b) { mBalanceProperty.setValue(b); }
    private void setCategory(String c) { mCategoryProperty.setValue(c); }
    void setSplitTransactionList(List<Transaction> stList) {
        mSplitTransactionList.addAll(stList);
        if (mSplitTransactionList.size() > 0)
            setCategory("--Split--");
    }

    // minimum constractor
    public Transaction(int accountID, LocalDate date, TradeAction ta, String category) {
        System.out.println("Transaction minimum constructor called");
        mAccountID = accountID;
        mTDateProperty.set(date);
        if (ta != null)
            mTradeActionProperty.set(ta.name());
        mCategoryProperty.set(category);
    }

    // Trade Transaction constructor
    // for all transactions, the amount is the notional amount, either 0 or positive
    // tradeAction can not be null
    public Transaction(int id, int accountID, LocalDate tDate, LocalDate aDate, TradeAction ta, String securityName,
                       String reference, String payee, BigDecimal price, BigDecimal quantity, BigDecimal oldQuantity,
                       String memo, BigDecimal commission, BigDecimal amount, String categoryString, int matchID,
                       int matchSplitID) {
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
        mMemoProperty.set(memo);
        mCategoryProperty.set(categoryString);
        mPayeeProperty.set(payee);
        mOldQuantityProperty.set(oldQuantity);
        setTradeDetails(ta, price, quantity, commission, amount);
    }

    static TradeAction mapBankingTransactionTA(String category, BigDecimal signedAmount) {
        if (MainApp.categoryOrTransferTest(category) >= 0)
            return signedAmount.signum() >= 0 ?  Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;

        return signedAmount.signum() >= 0 ? Transaction.TradeAction.XIN : Transaction.TradeAction.XOUT;
    }

    // Banking Transaction constructors
    public Transaction(int id, int accountID, LocalDate date, TradeAction ta, String reference, String payee,
                       String memo, String category, BigDecimal amount, int matchID, int matchSplitID) {
        mID = id;
        mAccountID = accountID;
        mMatchID = matchID;
        mMatchSplitID = matchSplitID;
        mTDateProperty.setValue(date);
        mReferenceProperty.setValue(reference);
        mPayeeProperty.setValue(payee);
        mMemoProperty.setValue(memo);
        mAmountProperty.set(amount);
        mCategoryProperty.setValue(category);

        mTradeActionProperty.setValue(ta.name());

        switch (ta) {
            case XIN:
            case DEPOSIT:
                mDepositProperty.setValue(amount);
                mPaymentProperty.setValue(null);
                mCashAmountProperty.setValue(amount);
                break;
            case XOUT:
            case WITHDRAW:
                mDepositProperty.setValue(null);
                mPaymentProperty.setValue(amount);
                mCashAmountProperty.setValue(amount);
                break;
            default:
                mDepositProperty.setValue(null);
                mPaymentProperty.setValue(null);
                mCashAmountProperty.setValue(null);
                break;
        }
        System.out.println("Banking Transaction constructor: " + "ID = " + id + "; Amount = " + mCashAmountProperty);
    }

    // copy constructor
    public Transaction(Transaction t0) {
        mID = t0.mID;
        mAccountID = t0.mAccountID;
        mTDateProperty.set(t0.mTDateProperty.get());
        mADateProperty.set(t0.mADateProperty.get());
        mTradeActionProperty.set(t0.mTradeActionProperty.get());
        mSecurityNameProperty.set(t0.mSecurityNameProperty.get());
        mReferenceProperty.set(t0.mReferenceProperty.get());
        mPayeeProperty.set(t0.mPayeeProperty.get());
        mAmountProperty.set(t0.mAmountProperty.get());
        mCashAmountProperty.set(t0.mCashAmountProperty.get());
        mPaymentProperty.set(t0.mPaymentProperty.get());
        mDepositProperty.set(t0.mDepositProperty.get());
        mMemoProperty.set(t0.mMemoProperty.get());
        mCategoryProperty.set(t0.mCategoryProperty.get());
        mBalanceProperty.set(t0.mBalanceProperty.get());
        mInvestAmountProperty.set(t0.mInvestAmountProperty.get());
        mCommissionProperty.set(t0.mCommissionProperty.get());
        mQuantityProperty.set(t0.mQuantityProperty.get());
        mPriceProperty.set(t0.mPriceProperty.get());
        mMatchID = t0.mMatchID;
        mMatchSplitID = t0.mMatchSplitID;
        mSplitTransactionList.addAll(t0.mSplitTransactionList);
        mOldQuantityProperty.set(t0.getOldQuantity());
    }
}
