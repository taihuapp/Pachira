package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;

/**
 * Created by ghe on 1/7/17.
 * A class for split transactions
 *
 * Split transactions have a simpler structure than
 * a full blown transactions.  It is essentially a cash transaction
 * But split transactions use different conventions.  So we are
 * using a separate class for it.
 */

class SplitTransaction {
    private int mID;

    // positive for Category ID
    // negative for negative of Transfer Account ID
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);

    private final StringProperty mMemoProperty = new SimpleStringProperty();

    // amount can be positive or negative
    // positive means cash into account, similar to XIN/DEPOSIT in Transaction class
    // negative means cash out of account, XOUT/WITHDRAW
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);  // this is amount

    private int mMatchID;  // the id of the transaction is matched up to this split transaction

    SplitTransaction(SplitTransaction st) {
        this(st.getID(), st.getCategoryID(), st.getMemo(), st.getAmount(), st.getMatchID());
    }

    SplitTransaction(int id, int cid, String memo, BigDecimal amount, int matchTid) {
        mID = id;
        mCategoryIDProperty.set(cid);
        mMemoProperty.set(memo);
        mAmountProperty.set(amount);
        mMatchID = matchTid;
    }

    int getID() { return mID; }
    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    BigDecimal getAmount() { return getAmountProperty().get(); }
    int getMatchID() { return mMatchID; }

    void setID(int id) { mID = id; }
    void setMatchID(int mid) { mMatchID = mid; }
    void setMemo(String memo) { mMemoProperty.set(memo); }
    void setAmount(BigDecimal amount) { mAmountProperty.set(amount); }

    boolean isTransfer(int exAid) {
        int cid = getCategoryID();

        return ((-cid > MainApp.MIN_ACCOUNT_ID) && (-cid != exAid));
    }

}
