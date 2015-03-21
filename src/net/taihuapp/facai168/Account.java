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
    private final IntegerProperty mID;
    private final IntegerProperty mTypeID;
    private final StringProperty mName;
    private final StringProperty mDescription;

    // default constructor
    public Account() {
        this(-1, -1, "", "");
    }

    public Account(int id, int typeID, String name, String description) {
        mID = new SimpleIntegerProperty(id);
        mTypeID = new SimpleIntegerProperty(typeID);
        mName = new SimpleStringProperty(name);
        mDescription = new SimpleStringProperty(description);
    }


    // getters and setters
    public IntegerProperty getIDProperty() { return mID; }
    public int getID() { return mID.get(); }
    public void setID(int id) { mID.set(id); }

    public IntegerProperty getTypeIDProperty() { return mTypeID; }
    public int getTypeID() { return mTypeID.get(); }
    public void setTypeID(int typeID) { mTypeID.set(typeID); }

    public StringProperty getNameProperty() { return mName; }
    public String getName() { return mName.get(); }
    public void setName(String name) { mName.set(name); }

    public StringProperty getDescriptionProperty() { return mDescription; }
    public String getDescription() { return mDescription.get(); }
    public void setDescription(String d) { mDescription.set(d); }

    public String toString() {
        return "mID:" + mID.get() + ";mTypeID:" + mTypeID.get()
                + ";mName:" + mName.get() + ";mDescription:" + mDescription.get();
    }
}
