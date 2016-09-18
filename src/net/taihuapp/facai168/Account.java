package net.taihuapp.facai168;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Created by ghe on 3/19/15.
 *
 */
public class Account {

    enum Type { SPENDING, INVESTING, PROPERTY, DEBT }

    private Type mType;

    private final IntegerProperty mID;
    private final StringProperty mName;
    private final StringProperty mDescription;
    private final ObjectProperty<BigDecimal> mCurrentBalance;
    private final ObservableList<Transaction> mTransactionList = FXCollections.observableArrayList();
    private final BooleanProperty mHiddenFlag = new SimpleBooleanProperty(false);
    private final IntegerProperty mDisplayOrder = new SimpleIntegerProperty(Integer.MAX_VALUE);

    // default constructor
    public Account() {
        this(0, Type.SPENDING, "", "", false, Integer.MAX_VALUE, null);
    }

    // detailed constructor
    public Account(int id, Type type, String name, String description, Boolean hidden, Integer displayOrder,
                   BigDecimal balance) {
        mID = new SimpleIntegerProperty(id);
        mType = type;
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);
        mCurrentBalance = new SimpleObjectProperty<>(balance);
        mHiddenFlag.set(hidden);
        mDisplayOrder.set(displayOrder);
    }

    // copy constructor
    Account(Account account) {
        this(account.getID(), account.getType(), account.getName(), account.getDescription(),
                account.getHiddenFlag(), account.getDisplayOrder(), account.getCurrentBalanceProperty().get());
    }

    // getters and setters
    public Type getType() { return mType; }
    public void setType(Type t) throws Exception {
        if (mID.get() > 0) {
            throw new Exception("Can't change account type for an exiting account");
        }
        mType = t;
    }

    IntegerProperty getIDProperty() { return mID; }
    int getID() { return mID.get(); }
    void setID(int id) { mID.set(id); }

    BooleanProperty getHiddenFlagProperty() { return mHiddenFlag; }
    Boolean getHiddenFlag() { return getHiddenFlagProperty().get(); }
    void setHiddenFlag(boolean h) { getHiddenFlagProperty().set(h); }

    IntegerProperty getDisplayOrderProperty() { return mDisplayOrder; }
    Integer getDisplayOrder() { return getDisplayOrderProperty().get(); }
    void setDisplayOrder(int d) { mDisplayOrder.set(d); }

    StringProperty getNameProperty() { return mName; }
    String getName() { return mName.get(); }
    void setName(String name) { mName.set(name); }

    StringProperty getDescriptionProperty() { return mDescription; }
    String getDescription() { return mDescription.get(); }
    void setDescription(String d) { mDescription.set(d); }

    void setTransactionList(List<Transaction> tList) {
        mTransactionList.setAll(tList);
        updateTransactionListBalance();
    }
    ObservableList<Transaction> getTransactionList() { return mTransactionList; }

    ObjectProperty<BigDecimal> getCurrentBalanceProperty() { return mCurrentBalance; }
    void setCurrentBalance(BigDecimal cb) { mCurrentBalance.set(cb); }

    // update balance field for each transaction for SPENDING account
    // no-op for other types of accounts
    private void updateTransactionListBalance() {
        BigDecimal b = new BigDecimal(0);
        boolean accountBalanceIsSet = false;
        for (Transaction t : getTransactionList()) {
            if (getType() != Type.INVESTING  // investing account balance is handled differently
                    && !accountBalanceIsSet && t.getTDateProperty().get().isAfter(LocalDate.now())) {
                // this is a future transaction.  if account current balance is not set
                // set it before process this future transaction
                setCurrentBalance(b);
                accountBalanceIsSet = true;
            }
            BigDecimal amount = t.getCashAmountProperty().get();
            if (amount != null) {
                b = b.add(amount);
                t.setBalance(b);
            }
        }
        // at the end of the list, if the account balance still not set, set it now.
        if (getType() != Type.INVESTING && !accountBalanceIsSet)
            setCurrentBalance(b);
    }

    public String toString() {
        return "mID:" + mID.get() + ";mType:" + mType.name()
                + ";mName:" + mName.get() + ";mDescription:" + mDescription.get();
    }
}
