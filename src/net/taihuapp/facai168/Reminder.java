package net.taihuapp.facai168;

import javafx.beans.property.*;
import javafx.util.converter.DateStringConverter;

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
    private final IntegerProperty mAccountIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mCategoryIDProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty mTransferAccountIDProperty = new SimpleIntegerProperty(0);
    private final StringProperty mTagProperty = new SimpleStringProperty("");
    private final StringProperty mMemoProperty = new SimpleStringProperty("");

    private DateSchedule mDateSchedule;

    // default constructor
    Reminder() {

        // default monthly schedule, starting today, no end, counting day of month forward.
        mDateSchedule = new DateSchedule(DateSchedule.BaseUnit.MONTH, 1, LocalDate.now(), null,
                true, true);
    }

    DateSchedule getDateSchedule() { return mDateSchedule; }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    Type getType() { return getTypeProperty().get(); }
    void setTyp(Type t) { getTypeProperty().set(t); }

    StringProperty getPayeeProperty() { return mPayeeProperty; }
    String getPayee() { return getPayeeProperty().get(); }
    void setPayee(String p) { getPayeeProperty().set(p); }

    ObjectProperty<BigDecimal> getAmountProperty() { return mAmountProperty; }
    BigDecimal getAmount() { return getAmountProperty().get(); }
    void setAmount(BigDecimal a) { getAmountProperty().set(a); }

    IntegerProperty getAccountIDProperty() { return mAccountIDProperty; }
    Integer getAccountID() { return getAccountIDProperty().get(); }
    void setAccountID(int i) { getAccountIDProperty().set(i); }

    IntegerProperty getCategoryIDProperty() { return mCategoryIDProperty; }
    Integer getCategoryID() { return getCategoryIDProperty().get(); }
    void setCategoryID(int i) { getCategoryIDProperty().set(i); }

    IntegerProperty getTransferAccountIDProperty() { return mTransferAccountIDProperty; }
    Integer getTransferAccountID() { return getTransferAccountIDProperty().get(); }
    void setTransferAccountID(int i) { getTransferAccountIDProperty().set(i); }

    StringProperty getTagProperty() { return mTagProperty; }
    String getTag() { return getTagProperty().get(); }
    void setTag(String t) { getTagProperty().set(t); }

    StringProperty getMemoProperty() { return mMemoProperty; }
    String getMemo() { return getMemoProperty().get(); }
    void setMemo(String m) { getMemoProperty().set(m); }
}
