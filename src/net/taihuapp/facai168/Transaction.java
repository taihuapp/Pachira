package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.sql.Date;

/**
 * Created by ghe on 4/9/15.
 */

public class Transaction {

    public enum TradeAction { BUY, BUYX, CASH, CGLONG, CGLONGX, CGSHORT, CGSHORTX,
        CONTRIB, CONTRIBX, DIV, DIVX, INTINC, INTINCX, MISCEXP, MISCEXPX,
        MISCINC, MISCINCX, REINVDIV, REINVINT, REINVLG, REINVMD, REINVSH,
        RTRNCAP, RTRNCAPX, SELL, SELLX, SHRSIN, SHRSOUT, STKSPLIT, WITHDRWX,
        XIN, XOUT }

    private int mID = -1;
    private int mAccountID = -1;
    private final ObjectProperty<Date> mDate = new SimpleObjectProperty<>();
    private final StringProperty mReference = new SimpleStringProperty();
    private final StringProperty mPayee = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> mAmount = new SimpleObjectProperty<>();
    private final TradeAction mTradeAction = null;
    private final StringProperty mMemo = new SimpleStringProperty();
    private final StringProperty mCategory = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> mBalance = new SimpleObjectProperty<>();


    // getters
    public int getAccountID() { return mAccountID; }
    public ObjectProperty<Date> getDateProperty() { return mDate; }
    public StringProperty getReferenceProperty() { return mReference; }
    public StringProperty getPayeeProperty() { return mPayee; }
    public StringProperty getMemoProperty() { return mMemo; }
    public StringProperty getCategoryProperty() { return mCategory; }
    public ObjectProperty<BigDecimal> getAmountProperty() { return mAmount; }
    public ObjectProperty<BigDecimal> getBalanceProperty() { return mBalance; }

    // setters
    public void setBalance(BigDecimal b) { mBalance.setValue(b); }

    // constructors
    public Transaction(int id, int accountID, Date date, String reference, String payee, String memo,
                       String category, BigDecimal amount) {
        mID = id;
        mAccountID = accountID;
        mDate.setValue(date);
        mReference.setValue(reference);
        mPayee.setValue(payee);
        mMemo.setValue(memo);
        mCategory.setValue(category);
        mAmount.setValue(amount);
    }

}
