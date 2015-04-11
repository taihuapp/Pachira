package net.taihuapp.facai168;

import javafx.beans.property.*;

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
    private final ObjectProperty<Date> mDate = new SimpleObjectProperty<Date>();
    private final TradeAction mTradeAction = null;
    private final StringProperty mPayee = new SimpleStringProperty();

    // getters
    public int getAccountID() { return mAccountID; }
    public ObjectProperty<Date> getDateProperty() { return mDate; }
    public StringProperty getPayeeProperty() { return mPayee; }
    // setters

    // constructors
    public Transaction(int id, int accountID, Date date, String payee) {
        mID = id;
        mAccountID = accountID;
        mDate.setValue(date);
        mPayee.setValue(payee);
    }
}
