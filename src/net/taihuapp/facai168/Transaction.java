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
    private int mAccountID = -1;
    private final ObjectProperty<LocalDate> mTDateProperty = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> mADateProperty = new SimpleObjectProperty<>(null);
    //private StringProperty mTradeActionProperty = new SimpleStringProperty("BUY");
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
    private final ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mOldQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final StringProperty mDescriptionProperty = new SimpleStringProperty("");
    private int mMatchID = -1; // transfer transaction id
    private int mMatchSplitID = -1;

    private final List<SplitTransaction> mSplitTransactionList = new ArrayList<>();

    // getters
    int getID() { return mID; }
    int getAccountID() { return mAccountID; }
    ObjectProperty<LocalDate> getTDateProperty() { return mTDateProperty; }
    ObjectProperty<LocalDate> getADateProperty() { return mADateProperty; }
    StringProperty getReferenceProperty() { return mReferenceProperty; }
    String getReference() { return getReferenceProperty().get(); }
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
                    System.err.println("TradingAction " + getTradeAction() + " not implement yet");
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
                    System.err.println("TradingAction " + getTradeAction() + " not implement yet");
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

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }

    LocalDate getTDate() { return mTDateProperty.get(); }
    LocalDate getADate() { return mADateProperty.get(); }
    BigDecimal getPrice() { return mPriceProperty.get(); }
    BigDecimal getQuantity() { return mQuantityProperty.get(); }
    BigDecimal getOldQuantity() { return getOldQuantityProperty().get(); }
    BigDecimal getCommission() { return mCommissionProperty.get(); }
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
                System.err.println("TradingAction " + getTradeAction() + " not implement yet");
                return BigDecimal.ZERO;
        }
    }

    BigDecimal getSignedQuantity() {
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
                return getQuantity();
            case XFRSHRS:
            case STKSPLIT:
            default:
                System.err.println("getSignedQuantity not implemented for " + getTradeAction());
                return getQuantity();
        }
    }

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
                mInvestAmountProperty.setValue(ta == TradeAction.RTRNCAP ? amount.negate() : BigDecimal.ZERO);
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
    private void setTagID(int tid) { mTagIDProperty.set(tid); }
    void setSplitTransactionList(List<SplitTransaction> stList) {
        mSplitTransactionList.clear();
        mSplitTransactionList.addAll(stList);
    }

    // minimum constructor
    Transaction(int accountID, LocalDate date, TradeAction ta, int categoryID) {
        mAccountID = accountID;
        mTDateProperty.set(date);
        mTradeActionProperty.set(ta);
        setCategoryID(categoryID);
        mTagIDProperty.set(0); // unused for now.

        bindProperties();
        // bind description property now
        bindDescriptionProperty();
    }

    // Trade Transaction constructor
    // for all transactions, the amount is the notional amount, either 0 or positive
    // tradeAction can not be null
    public Transaction(int id, int accountID, LocalDate tDate, LocalDate aDate, TradeAction ta, String securityName,
                       String reference, String payee, BigDecimal price, BigDecimal quantity, BigDecimal oldQuantity,
                       String memo, BigDecimal commission, BigDecimal amount, int categoryID, int tagID, int matchID,
                       int matchSplitID, List<SplitTransaction> stList) {
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
        mCategoryIDProperty.set(categoryID);
        mTagIDProperty.set(tagID);
        mPayeeProperty.set(payee);
        mOldQuantityProperty.set(oldQuantity);
        mQuantityProperty.set(quantity);
        mTradeActionProperty.set(ta);
        mAmountProperty.set(amount);
        if (stList != null)
            mSplitTransactionList.addAll(stList);

        bindProperties();
        // bind description property now
        bindDescriptionProperty();
    }

    // copy constructor
    public Transaction(Transaction t0) {
        this(t0.getID(), t0.getAccountID(), t0.getTDate(), t0.getADate(), t0.getTradeAction(), t0.getSecurityName(),
                t0.getReference(), t0.getPayeeProperty().get(), t0.getPrice(), t0.getQuantity(),
                t0.getOldQuantity(), t0.getMemo(), t0.getCommission(), t0.getAmount(), t0.getCategoryID(),
                t0.getTagID(), t0.getMatchID(), t0.getMatchSplitID(), t0.getSplitTransactionList());
    }

    // return false if this is NOT a transfer
    // also return false if this is a transfer to exAccountID
    // return true if this transaction is a transfer transaction to aother account
    boolean isTransfer() {
        int cid = getCategoryID();

        return !(cid >= -MainApp.MIN_ACCOUNT_ID || cid == -getAccountID());
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
}