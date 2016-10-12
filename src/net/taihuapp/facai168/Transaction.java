package net.taihuapp.facai168;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by ghe on 4/9/15.
 * Transaction Class
 */

public class Transaction {

    enum TradeAction {
        BUY, SELL, DIV, REINVDIV, INTINC, REINVINT, CGLONG, CGMID, CGSHORT,
        REINVLG, REINVMD, REINVSH, STKSPLIT, SHRSIN, SHRSOUT, MISCEXP, MISCINC, STOCKDIV,
        RTRNCAP, SHTSELL, CVTSHRT, MARGINT,
        XFRSHRS, XIN, XOUT, BUYBOND, DEPOSIT, WITHDRAW
    }

    private int mID = -1;
    private int mAccountID = -1;
    private final ObjectProperty<LocalDate> mTDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> mADateProperty = new SimpleObjectProperty<>(null);
    //private StringProperty mTradeActionProperty = new SimpleStringProperty("BUY");
    private final ObjectProperty<TradeAction> mTradeActionProperty = new SimpleObjectProperty<>(TradeAction.BUY);
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
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);
    private final ObjectProperty<BigDecimal> mBalanceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    // investment amount, derived from total amount
    private final ObjectProperty<BigDecimal> mInvestAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mCommissionProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mOldQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mDescriptionProperty = new SimpleStringProperty("");
    private int mMatchID = -1; // transfer transaction id
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
    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }

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

    //TradeAction getTradeActionEnum() { return TradeAction.valueOf(getTradeAction()); }
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
                    return mQuantityProperty.get().stripTrailingZeros().toPlainString() + " shares @ "
                            + mPriceProperty.get().stripTrailingZeros().toPlainString();
                case STKSPLIT:
                    return mQuantityProperty.get().stripTrailingZeros().toPlainString() + " for "
                            + mOldQuantityProperty.get().stripTrailingZeros().toPlainString() + " split";
                case MARGINT:
                case MISCEXP:
                case MISCINC:
                case XIN:
                case XOUT:
                case DEPOSIT:
                case WITHDRAW:
                case DIV:
                case CGLONG:
                case CGMID:
                case CGSHORT:
                case INTINC:
                    return mAmountProperty.get().stripTrailingZeros().toPlainString();
                case RTRNCAP:
                case SHRSOUT:
                case STOCKDIV:
                case XFRSHRS:
                case BUYBOND:
                default:
                    return "description for [" + getTradeAction() + "] Transaction not implemented yet.";
            }
        };

        // bind now
        mDescriptionProperty.bind(Bindings.createStringBinding(converter,
                mTradeActionProperty, mQuantityProperty, mPriceProperty, mOldQuantityProperty));
    }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }

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
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    int getMatchID() { return mMatchID; }  // this is for linked transactions
    int getMatchSplitID() { return mMatchSplitID; }

    BigDecimal getSignedQuantity() {
        switch (getTradeAction()) {
            case SELL:
            case SHTSELL:
            case SHRSOUT:
                return getQuantity().negate();
            case BUY:
            case BUYBOND:
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
                                 BigDecimal commission, BigDecimal amount, boolean isXfer) {
        mTradeActionProperty.set(ta);
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
                mCashAmountProperty.setValue(isXfer ? BigDecimal.ZERO : amount.negate());
                mQuantityProperty.set(quantity);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
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
                mCashAmountProperty.setValue(isXfer ? BigDecimal.ZERO : amount);
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
                mInvestAmountProperty.setValue(BigDecimal.ZERO);
                mCashAmountProperty.setValue(isXfer ? BigDecimal.ZERO : amount);
                mQuantityProperty.set(null);
                mDepositProperty.set(null);
                mPaymentProperty.set(null);
                break;
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
    private void setCategoryID(int cid) { mCategoryIDProperty.setValue(cid); }
    void setSplitTransactionList(List<Transaction> stList) {
        mSplitTransactionList.addAll(stList);
    }

    // minimum constractor
    public Transaction(int accountID, LocalDate date, TradeAction ta, int categoryID) {
        System.out.println("Transaction minimum constructor called");
        mAccountID = accountID;
        mTDateProperty.set(date);
        mTradeActionProperty.set(ta);
        setCategoryID(categoryID);

        // bind description property now
        bindDescriptionProperty();
    }

    // Trade Transaction constructor
    // for all transactions, the amount is the notional amount, either 0 or positive
    // tradeAction can not be null
    public Transaction(int id, int accountID, LocalDate tDate, LocalDate aDate, TradeAction ta, String securityName,
                       String reference, String payee, BigDecimal price, BigDecimal quantity, BigDecimal oldQuantity,
                       String memo, BigDecimal commission, BigDecimal amount, int categoryID, int matchID,
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
        setCategoryID(categoryID);
        mPayeeProperty.set(payee);
        mOldQuantityProperty.set(oldQuantity);

        boolean isXfer = categoryID <= -MainApp.MIN_ACCOUNT_ID && categoryID != -accountID;
        setTradeDetails(ta, price, quantity, commission, amount, isXfer);

        // bind description property now
        bindDescriptionProperty();
    }

    static TradeAction mapBankingTransactionTA(int cid, BigDecimal signedAmount) {
        if (MainApp.categoryOrTransferTest(cid) >= 0)
            return signedAmount.signum() >= 0 ?  Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;

        return signedAmount.signum() >= 0 ? Transaction.TradeAction.XIN : Transaction.TradeAction.XOUT;
    }

    static TradeAction mapBankingTransactionTA(String category, BigDecimal signedAmount) {
        if (MainApp.categoryOrTransferTest(category) >= 0)
            return signedAmount.signum() >= 0 ?  Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;

        return signedAmount.signum() >= 0 ? Transaction.TradeAction.XIN : Transaction.TradeAction.XOUT;
    }

    // Banking Transaction constructors
    public Transaction(int id, int accountID, LocalDate date, TradeAction ta, String reference, String payee,
                       String memo, int categoryID, BigDecimal amount, int matchID, int matchSplitID) {
        mID = id;
        mAccountID = accountID;
        mMatchID = matchID;
        mMatchSplitID = matchSplitID;
        mTDateProperty.setValue(date);
        mReferenceProperty.setValue(reference);
        mPayeeProperty.setValue(payee);
        mMemoProperty.setValue(memo);
        mAmountProperty.set(amount);
        setCategoryID(categoryID);

        mTradeActionProperty.set(ta);

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

        // bind description property now
        bindDescriptionProperty();
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
        setCategoryID(t0.getCategoryID());
        mBalanceProperty.set(t0.mBalanceProperty.get());
        mInvestAmountProperty.set(t0.mInvestAmountProperty.get());
        mCommissionProperty.set(t0.mCommissionProperty.get());
        mQuantityProperty.set(t0.mQuantityProperty.get());
        mPriceProperty.set(t0.mPriceProperty.get());
        mMatchID = t0.mMatchID;
        mMatchSplitID = t0.mMatchSplitID;
        mSplitTransactionList.addAll(t0.mSplitTransactionList);
        mOldQuantityProperty.set(t0.getOldQuantity());

        // bind description property now
        bindDescriptionProperty();
    }
}