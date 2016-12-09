package net.taihuapp.facai168;

import javafx.beans.property.SimpleSetProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Created by ghe on 12/7/16.
 *
 */
class Tag {
    private int mID;
    private final StringProperty mNameProperty;
    private final StringProperty mDescriptionProperty;

    Tag(int id, String name, String desc) {
        mID = id;
        mNameProperty = new SimpleStringProperty(name);
        mDescriptionProperty = new SimpleStringProperty(desc);
    }

    int getID() { return mID; }
    void setID(int i) { mID = i; }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String n) { getNameProperty().set(n); }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String desc) { getDescriptionProperty().set(desc); }
}

