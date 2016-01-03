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

    public enum TradeAction {
        BUY, BUYX, CASH, CGLONG, CGLONGX, CGMID, CGMIDX,
        CGSHORT, CGSHORTX, CVTSHRT, CVTSHRTX, DIV, DIVX, INTINC, INTINCX,
        MARGINT, MARGINTX, MISCEXP, MISCEXPX, MISCINC, MISCINCX,
        REINVDIV, REINVINT, REINVLG, REINVMD, REINVSH, RTRNCAP, RTRNCAPX,
        SELL, SELLX, SHRSIN, SHRSOUT, SHTSELL, SHTSELLX, STKSPLIT, STOCKDIV,
        XFRSHRS, XIN, XOUT, BUYBOND, BUYBONDX
    }

    private int mID = -1;
    private int mAccountID = -1;
    private final ObjectProperty<LocalDate> mDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private StringProperty mTradeActionProperty = new SimpleStringProperty("BUY");
    private StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private final StringProperty mReferenceProperty = new SimpleStringProperty("");
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    // amount property,
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);  // this is amount
    // cash amount, derived from total amount
    private final ObjectProperty<BigDecimal> mCashAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mPaymentProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mDepositeProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");
    private final StringProperty mCategoryProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // investment amount, derived from total amount
    private final ObjectProperty<BigDecimal> mInvestAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mCommissionProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mDescriptionProperty = new SimpleStringProperty("");
    private int mMatchID = -1;
    private int mMatchSplitID = -1;
    // we use a Transaction object for holding a split transaction
    private final List<Transaction> mSplitTransactionList = new ArrayList<>();

    // getters
    public int getID() { return mID; }
    public int getAccountID() { return mAccountID; }
    public ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
    public StringProperty getReferenceProperty() { return mReferenceProperty; }
    public StringProperty getPayeeProperty() { return mPayeeProperty; }
    public StringProperty getMemoProperty() { return mMemoProperty; }
    public StringProperty getCategoryProperty() { return mCategoryProperty; }

    public ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    public ObjectProperty<BigDecimal> getInvestAmountProperty() { return mInvestAmountProperty; }
    public ObjectProperty<BigDecimal> getCashAmountProperty() { return mCashAmountProperty; }
    public ObjectProperty<BigDecimal> getPaymentProperty() { return mPaymentProperty; }
    public ObjectProperty<BigDecimal> getDepositeProperty() { return mDepositeProperty; }
    public ObjectProperty<BigDecimal> getCommissionProperty() { return mCommissionProperty; }
    public ObjectProperty<BigDecimal> getBalanceProperty() { return mBalanceProperty; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }

    public StringProperty getTradeActionProperty() { return mTradeActionProperty; }
    public StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }

    public StringProperty getDescriptionProperty() {
        switch (TradeAction.valueOf(mTradeActionProperty.get())) {
            case BUY:
            case BUYX:
            case CVTSHRT:
            case CVTSHRTX:
            case SELL:
            case SELLX:
            case SHTSELL:
            case SHTSELLX:
                mDescriptionProperty.set("" + mQuantityProperty.get() + " @ " + mPriceProperty.get());
                break;
            default:
                mDescriptionProperty.set("description for this type Transaction not implemented yet.");
                break;
        }
        return mDescriptionProperty;
    }

    public LocalDate getDate() { return mDateProperty.get(); }
    public String getMemo() { return mMemoProperty.get(); }
    public BigDecimal getPrice() { return mPriceProperty.get(); }
    public BigDecimal getQuantity() { return mQuantityProperty.get(); }
    public BigDecimal getCommission() { return mCommissionProperty.get(); }
    public BigDecimal getCostBasis() { return mInvestAmountProperty.get(); }
    public String getSecurityName() { return mSecurityNameProperty.get();}
    public BigDecimal getCashAmount() { return mCashAmountProperty.get(); }
    public List<Transaction> getSplitTransactionList() { return mSplitTransactionList; }
    public BigDecimal getAmount() { return mAmountProperty.get(); }
    public String getTradeAction() { return getTradeActionProperty().get(); }
    public String getCategory() { return getCategoryProperty().get(); }
    public int getMatchID() { return mMatchID; }
    public int getMatchSplitID() { return mMatchSplitID; }

/*
        switch (TradeAction.valueOf(mTradeActionProperty.get())) {
            case BUY:
            case BUYBOND:
            case CVTSHRT:
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
                return mInvestAmountProperty.get();
            case SELL:
            case SHTSELL:
            case SELLX:
            case SHTSELLX:
                return mInvestAmountProperty.get().negate();
            case CASH:
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
            case XOUT:
                return mCashAmountProperty.get();
            case CGLONGX:
            case CGMIDX:
            case CGSHORTX:
            case DIVX:
            case INTINCX:
            case MARGINTX:
            case MISCEXPX:
            case MISCINCX:
            case RTRNCAPX:
                return BigDecimal.ZERO;
            default:
                System.err.println("TradingAction " + mTradeActionProperty.get() + " not implement yet");
                return null;
        }
    }
*/

    public static BigDecimal computeTotalAmount(TradeAction ta, BigDecimal price, BigDecimal quantity,
                                                BigDecimal commission) {
        switch (ta) {
            case BUY:
                return quantity.multiply(price).add(commission);
            case SELL:
                return quantity.multiply(price).subtract(commission);
            default:
                System.err.println("computeTotalAmount: case " + ta.toString() + " not implemented yet");
                return null;
        }
    }

    // setters
    public void setID(int id) { mID = id; }
    public void setMatchID(int mid, int mSplitID) {
        mMatchID = mid;
        mMatchSplitID = mSplitID;
    }

    public void setTradeDetails(TradeAction ta, BigDecimal price, BigDecimal quantity,
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
                break;
            case SELL:
            case SHTSELL:
                mInvestAmountProperty.setValue(amount.negate());
                mCashAmountProperty.setValue(amount);
                mQuantityProperty.set(quantity.negate());
                break;
            case SELLX:
            case SHTSELLX:
                mInvestAmountProperty.setValue(amount.negate());
                mCashAmountProperty.setValue(BigDecimal.ZERO);
                mQuantityProperty.set(quantity.negate());
                break;
            case CASH:
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
            case XOUT:
                mInvestAmountProperty.setValue(BigDecimal.ZERO);
                mCashAmountProperty.setValue(amount);
                mQuantityProperty.set(quantity);
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
                break;
            default:
                System.err.println("TradingAction " + ta.name() + " not implement yet");
                break;
        }
    }

    public void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    public void setPrice(BigDecimal p) { mPriceProperty.set(p); }
    public void setCommission(BigDecimal c) { mCommissionProperty.set(c); }
    public void setSecurityName(String securityName) { mSecurityNameProperty.set(securityName); }
    public void setMemo(String memo) { mMemoProperty.set(memo); }
    public void setBalance(BigDecimal b) { mBalanceProperty.setValue(b); }
    public void setCategory(String c) { mCategoryProperty.setValue(c); }
    public void setSplitTransactionList(List<Transaction> stList) {
        mSplitTransactionList.addAll(stList);
        if (mSplitTransactionList.size() > 0)
            setCategory("--Split--");
    }

    // minimum constractor
    public Transaction(int accountID, LocalDate date) {
        mAccountID = accountID;
        mDateProperty.set(date);
        mTradeActionProperty.set(TradeAction.BUY.name());  // default buy
    }

    // Trade Transaction constructor
    // for cash transactions, the amount can be either positive or negative
    // for other transactions, the amount is the notional amount, either 0 or positive
    public Transaction(int id, int accountID, LocalDate date, TradeAction ta, String securityName,
                       BigDecimal price, BigDecimal quantity, String memo, BigDecimal commission,
                       BigDecimal amount, String categoryString, int matchID, int matchSplitID) {
        mID = id;
        mAccountID = accountID;
        mMatchID = matchID;
        mMatchSplitID = matchSplitID;
        mDateProperty.set(date);
        mSecurityNameProperty.set(securityName);
        mPriceProperty.set(price);
        mCommissionProperty.set(commission);
        mMemoProperty.set(memo);
        mCategoryProperty.set(categoryString);

        setTradeDetails(ta, price, quantity, commission, amount);
        // todo
        // need to finish here
    }


    // Banking Transaction constructors
    public Transaction(int id, int accountID, LocalDate date, String reference, String payee, String memo,
                       String category, BigDecimal amount, int matchID, int matchSplitID) {
        mID = id;
        mAccountID = accountID;
        mMatchID = matchID;
        mMatchSplitID = matchSplitID;
        mDateProperty.setValue(date);
        mReferenceProperty.setValue(reference);
        mPayeeProperty.setValue(payee);
        mMemoProperty.setValue(memo);
        mAmountProperty.set(amount);
        mCategoryProperty.setValue(category);
        mCashAmountProperty.setValue(amount);
        mTradeActionProperty.setValue("");

        mDepositeProperty.setValue(null);
        mPaymentProperty.setValue(null);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            mDepositeProperty.setValue(amount);
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            mPaymentProperty.setValue(amount.negate());
        }

        System.out.println("Transaction constructor: " + "ID = " + id + "; Amount = " + mCashAmountProperty);
    }

    // copy constructor
    public Transaction(Transaction t0) {
        mID = t0.mID;
        mAccountID = t0.mAccountID;
        mDateProperty.set(t0.mDateProperty.get());
        mTradeActionProperty.set(t0.mTradeActionProperty.get());
        mSecurityNameProperty.set(t0.mSecurityNameProperty.get());
        mReferenceProperty.set(t0.mReferenceProperty.get());
        mPayeeProperty.set(t0.mPayeeProperty.get());
        mAmountProperty.set(t0.mAmountProperty.get());
        mCashAmountProperty.set(t0.mCashAmountProperty.get());
        mPaymentProperty.set(t0.mPaymentProperty.get());
        mDepositeProperty.set(t0.mDepositeProperty.get());
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
    }
}
