package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 11/29/16.
 *
 */
class Reminder {

    enum Type { PAYMENT, DEPOSIT, TRANSFER }

    private int mID = -1;
    private final ObjectProperty<Type> mTypeProperty = new SimpleObjectProperty<>(Type.PAYMENT);
    private final StringProperty mPayeeProperty = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> mAmountProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final IntegerProperty mEstimateCountProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mAccountIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTransferAccountIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTagIDProperty = new SimpleIntegerProperty(0);
    private final StringProperty mMemoProperty = new SimpleStringProperty("");

    private DateSchedule mDateSchedule;

    // default constructor
    Reminder() {

        // default monthly schedule, starting today, no end, counting day of month forward.
        mDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1, LocalDate.now(), null,
                3, true, true);
    }

    // copy constructor
    Reminder(Reminder r) {
        this(r.getID(), r.getType(), r.getPayee(), r.getAmount(), r.getEstimateCount(), r.getAccountID(),
                r.getCategoryID(), r.getTransferAccountID(), r.getTagID(), r.getMemo(), r.getDateSchedule());
    }

    Reminder(int id, Type type, String payee, BigDecimal amount, int estCnt, int accountID, int categoryID,
             int transferAccountID, int tagID, String memo, DateSchedule ds) {
        mID = id;
        mTypeProperty.set(type);
        mPayeeProperty.set(payee);
        mAmountProperty.set(amount);
        mEstimateCountProperty.set(estCnt);
        mAccountIDProperty.set(accountID);
        mCategoryIDProperty.set(categoryID);
        mTransferAccountIDProperty.set(transferAccountID);
        mTagIDProperty.set(tagID);
        mMemoProperty.set(memo);
        mDateSchedule = ds;
    }

    DateSchedule getDateSchedule() { return mDateSchedule; }
    void setDateSchedule(DateSchedule ds) { mDateSchedule = ds; }

    int getID() { return mID; }
    void setID(int id) { mID = id; }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    Type getType() { return getTypeProperty().get(); }
    void setTyp(Type t) { getTypeProperty().set(t); }

    StringProperty getPayeeProperty() { return mPayeeProperty; }
    String getPayee() { return getPayeeProperty().get(); }
    void setPayee(String p) { getPayeeProperty().set(p); }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    BigDecimal getAmount() { return getAmountProperty().get(); }
    void setAmount(BigDecimal a) { getAmountProperty().set(a); }

    IntegerProperty getEstimateCountProperty() { return mEstimateCountProperty; }
    Integer getEstimateCount() { return getEstimateCountProperty().get(); }
    void setEstimateCount(int c) { getEstimateCountProperty().set(c); }

    IntegerProperty getAccountIDProperty() { return mAccountIDProperty; }
    Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(int i) { getAccountIDProperty().set(i); }

    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    void setCategoryID(int i) { getCategoryIDProperty().set(i); }

    IntegerProperty getTransferAccountIDProperty() { return mTransferAccountIDProperty; }
    Integer getTransferAccountID() { return getTransferAccountIDProperty().get(); }
    void setTransferAccountID(int i) { getTransferAccountIDProperty().set(i); }

    IntegerProperty getTagIDProperty() { return mTagIDProperty; }
    Integer getTagID() { return getTagIDProperty().get(); }
    void setTagID(int i) { getTagIDProperty().set(i); }

    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    void setMemo(String m) { getMemoProperty().set(m); }
}
