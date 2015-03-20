package net.taihuapp.facai168;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Created by ghe on 3/19/15.
 */
public class AccountType {
    private final IntegerProperty mID;
    private final StringProperty mType;

    // public contructor
    public AccountType() {
        this(-1, "");
    }

    public AccountType(int id, String type) {
        mID = new SimpleIntegerProperty(id);
        mType = new SimpleStringProperty(type);
    }

    // getters and setters
    public IntegerProperty getIDProperty() { return mID; }
    public int getID() { return mID.get(); }
    public void setID(int id) { mID.set(id); }

    public StringProperty getTypeProperty() { return mType; }
    public String getType() { return mType.get(); }
    public void setType(String type) { mType.set(type); }
}
