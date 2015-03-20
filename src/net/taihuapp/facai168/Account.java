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
        mID = new SimpleIntegerProperty(-1);
        mTypeID = new SimpleIntegerProperty(-1);
        mName = new SimpleStringProperty("");
        mDescription = new SimpleStringProperty("");
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
}
