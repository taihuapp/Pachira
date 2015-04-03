package net.taihuapp.facai168;

/**
 * Created by ghe on 4/2/15.
 */
public class Security {
    public enum Type {
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
    private String mTicker;
    private String mName;
    private Type mType;

    // default constructor
    public Security() {
        mID = -1;
        mType = Type.STOCK;
    }

    public Security(int id, String ticker, String name, Type type) {
        mID = id;
        mTicker = ticker;
        mName = name;
        mType = type;
    }

    // getters and setters
    public int getID() { return mID; }
    public String getTicker() { return mTicker; }
    public String getName() { return mName; }
    public Type getType() { return mType; }

    public void setID(int id) { mID = id; }
}
