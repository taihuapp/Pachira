package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;

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

    // default constructor
    public Account() {
        this(0, Type.SPENDING, "", "");
    }

    public Account(int id, Type type, String name, String description) {
        mID = new SimpleIntegerProperty(id);
        mType = type;
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);

        mCurrentBalance = new SimpleObjectProperty<>(BigDecimal.ZERO);
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

    StringProperty getNameProperty() { return mName; }
    String getName() { return mName.get(); }
    void setName(String name) { mName.set(name); }

    StringProperty getDescriptionProperty() { return mDescription; }
    String getDescription() { return mDescription.get(); }
    void setDescription(String d) { mDescription.set(d); }

    ObjectProperty<BigDecimal> getCurrentBalanceProperty() { return mCurrentBalance; }
    BigDecimal getCurrentBalance() { return mCurrentBalance.get(); }
    void setCurrentBalance(BigDecimal cb) { mCurrentBalance.set(cb); }

    public String toString() {
        return "mID:" + mID.get() + ";mType:" + mType.name()
                + ";mName:" + mName.get() + ";mDescription:" + mDescription.get();
    }
}
