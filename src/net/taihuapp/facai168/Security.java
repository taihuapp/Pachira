package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Created by ghe on 4/2/15.
 * Security class
 */
public class Security {
    enum Type {
        // make sure the name is not longer than 16 characters
        // or database structure needs to be changed.
        STOCK, BOND, MUTUALFUND, CD, INDEX, OTHER;

        public String toString() {
            switch (this) {
                case STOCK:
                    return "Stock";
                case BOND:
                    return "Bond";
                case MUTUALFUND:
                    return "Mutual Fund";
                case INDEX:
                    return "Market Index";
                case CD:
                    return "CD";
                default:
                    return "Other";
            }
        }

        static Type fromString(String t) {
            switch (t.toUpperCase()) {
                case "MUTUAL FUND":
                    return MUTUALFUND;
                case "MARKET INDEX":
                    return INDEX;
                default:
                    return Type.valueOf(t.toUpperCase());
            }
        }
    }

    // should "property" should be used?
    private int mID;
    private final StringProperty mTickerProperty;
    private final StringProperty mNameProperty;
    private ObjectProperty<Type> mTypeProperty;

    // default constructor
    public Security() {
        // 0 is not a legit security ID in database
        this(0, "", "", Type.STOCK);
    }

    public Security(int id, String ticker, String name, Type type) {
        mID = id;
        mTickerProperty = new SimpleStringProperty(ticker);
        mNameProperty = new SimpleStringProperty(name);
        mTypeProperty = new SimpleObjectProperty<>(type);
    }

    Security(Security s) {
        this(s.getID(), s.getTicker(), s.getName(), s.getType());
    }

    // getters and setters
    int getID() { return mID; }
    void setID(int id) { mID = id; }

    StringProperty getTickerProperty() { return mTickerProperty; }
    String getTicker() { return getTickerProperty().get(); }
    void setTicker(String ticker) { getTickerProperty().set(ticker); }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    ObjectProperty<Type> getTypeProperty() { return mTypeProperty; }
    Type getType() { return getTypeProperty().get(); }
    void setType(Type type) { getTypeProperty().set(type); }

    public String toString() {
        return "ID = " + getID() + ", Name = '" + getName() + "', Ticker = '" + getTicker()
                + "', Type = '" + getType() + "'";
    }
}
