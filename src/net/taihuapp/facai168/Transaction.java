package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 4/9/15.
 */

public class Transaction {

    public enum TradeAction { BUY, BUYX, CASH, CGLONG, CGLONGX, CGMID, CGMIDX,
        CGSHORT, CGSHORTX, CVTSHRT, CVTSHRTX, DIV, DIVX, INTINC, INTINCX,
        MARGINT, MARGINTX, MISCEXP, MISCEXPX, MISCINC, MISCINCX,
        REINVDIV, REINVINT, REINVLG, REINVMD, REINVSH, RTRNCAP, RTRNCAPX,
        SELL, SELLX, SHRSIN, SHRSOUT, SHTSELL, SHTSELLX, STKSPLIT, STOCKDIV,
        XFRSHRS, XIN, XOUT, BUYBOND, BUYBONDX }

    private int mID = -1;
    private int mAccountID = -1;
    private final ObjectProperty<LocalDate> mDate = new SimpleObjectProperty<>();
    private StringProperty mTradeAction = new SimpleStringProperty();
    private StringProperty mSecurityName = new SimpleStringProperty();
    private final StringProperty mReference = new SimpleStringProperty();
    private final StringProperty mPayee = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> mCashAmount = new SimpleObjectProperty<>();  // this is cash amount
    private ObjectProperty<BigDecimal> mPayment = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mDeposite = new SimpleObjectProperty<>();
    private final StringProperty mMemo = new SimpleStringProperty();
    private final StringProperty mCategory = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> mBalance = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> mInvestAmount = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> mCommission = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> mQuantity = new SimpleObjectProperty<>();

    // getters
    public int getID() { return mID; }
    public int getAccountID() { return mAccountID; }
    public ObjectProperty<LocalDate> getDateProperty() { return mDate; }
    public StringProperty getReferenceProperty() { return mReference; }
    public StringProperty getPayeeProperty() { return mPayee; }
    public StringProperty getMemoProperty() { return mMemo; }
    public StringProperty getCategoryProperty() { return mCategory; }

    public ObjectProperty<BigDecimal> getInvestAmountProperty() { return mInvestAmount; }
    public ObjectProperty<BigDecimal> getCashAmountProperty() { return mCashAmount; }
    public ObjectProperty<BigDecimal> getPaymentProperty() { return mPayment; }
    public ObjectProperty<BigDecimal> getDepositProperty() { return mDeposite; }
    public ObjectProperty<BigDecimal> getCommissionProperty() { return mCommission; }
    public ObjectProperty<BigDecimal> getBalanceProperty() { return mBalance; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantity; }

    //public TradeAction getTradeAction() { return mTradeAction; }
    public StringProperty getTradeActionProperty() { return mTradeAction; }
    public StringProperty getSecurityNameProperty() { return mSecurityName; }

     // setters
    public void setBalance(BigDecimal b) { mBalance.setValue(b); }

    // Trade Transaction constructor
    // for cash transactions, the amount can be either positive or negative
    // for other transactions, the amount is the notional amount, either 0 or positive
    public Transaction(int id, int accountID, LocalDate date, TradeAction ta, String securityName,
                       BigDecimal quantity, String memo, BigDecimal commission, BigDecimal amount) {
        mID = id;
        mAccountID = accountID;
        mDate.set(date);
        mTradeAction.set(ta.name());
        mSecurityName.set(securityName);
        mCommission.set(commission);
        mQuantity.set(quantity);

        switch (ta) {
            // todo
            // need to verify each
            case BUY:
            case BUYBOND:
            case CVTSHRT:
                mInvestAmount.setValue(amount);
                mCashAmount.setValue(amount.negate());
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
                mInvestAmount.setValue(amount);
                mCashAmount.setValue(BigDecimal.ZERO);
                break;
            case SELL:
            case SHTSELL:
                mInvestAmount.setValue(amount.negate());
                mCashAmount.setValue(amount);
                break;
            case SELLX:
            case SHTSELLX:
                mInvestAmount.setValue(amount.negate());
                mCashAmount.setValue(BigDecimal.ZERO);
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
                mInvestAmount.setValue(BigDecimal.ZERO);
                mCashAmount.setValue(amount);
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
                mInvestAmount.setValue(BigDecimal.ZERO);
                mCashAmount.setValue(BigDecimal.ZERO);
                break;
            default:
                System.err.println("TradingAction " + ta.name() + " not implement yet");
                break;
        }

        if (mCashAmount.get() == null) {
            if (amount == null)
                System.err.println("Amount is null?" + id);
            System.err.println("Null cash amount? " + id);
            System.exit(1);
        }
        // todo
        // need to finish here
    }


    // Banking Transaction constructors
    public Transaction(int id, int accountID, LocalDate date, String reference, String payee, String memo,
                       String category, BigDecimal amount) {
        mID = id;
        mAccountID = accountID;
        mDate.setValue(date);
        mReference.setValue(reference);
        mPayee.setValue(payee);
        mMemo.setValue(memo);
        mCategory.setValue(category);
        mCashAmount.setValue(amount);
        mTradeAction.setValue("");

        mDeposite.setValue(null);
        mPayment.setValue(null);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            mDeposite.setValue(amount);
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            mPayment.setValue(amount.negate());
        }

        System.out.println("Transaction constructor: " + "ID = " + id + "; Amount = " + mCashAmount);
    }

}
