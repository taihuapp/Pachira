package net.taihuapp.facai168;

import javafx.beans.InvalidationListener;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Created by ghe on 3/19/15.
 */
public class Account {

    public enum Type { SPENDING, INVESTING, PROPERTY, DEBT }

    private Type mType;

    private final IntegerProperty mID;
    private final StringProperty mName;
    private final StringProperty mDescription;

    // default constructor
    public Account() {
        this(-1, Type.SPENDING, "", "");
    }

    public Account(int id, Type type, String name, String description) {
        mID = new SimpleIntegerProperty(id);
        mType = type;
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);
    }

    // getters and setters
    public Type getType() { return mType; }
    public void setType(Type t) throws Exception {
        if (mID.get() >= 0) {
            throw new Exception("Can't change account type for an exiting account");
        }
        mType = t;
    }

    public IntegerProperty getIDProperty() { return mID; }
    public int getID() { return mID.get(); }
    public void setID(int id) { mID.set(id); }

    public StringProperty getNameProperty() { return mName; }
    public String getName() { return mName.get(); }
    public void setName(String name) { mName.set(name); }

    public StringProperty getDescriptionProperty() { return mDescription; }
    public String getDescription() { return mDescription.get(); }
    public void setDescription(String d) { mDescription.set(d); }

    public String toString() {
        return "mID:" + mID.get() + ";mType:" + mType.name()
                + ";mName:" + mName.get() + ";mDescription:" + mDescription.get();
    }
}
