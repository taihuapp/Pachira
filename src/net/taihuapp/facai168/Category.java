package net.taihuapp.facai168;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by ghe on 5/22/16.
 *
 */
public class Category {

    private int mID;
    private final StringProperty mNameProperty;  // name of the category
    private final StringProperty mDescriptionProperty;  // description
    private final ObjectProperty<Boolean> mIsIncomeProperty; // income category flag
    // mTaxRefNum = -1 for non tax related
    //               0 for tax related but no valid ref num
    //              >0 actual tax ref number
    private final IntegerProperty mTaxRefNumProperty;  // Tax reference number (for tax-related items,
    private ObjectProperty<BigDecimal> mBudgetAmountProperty; // budget amount

    // constructor
    Category(int id, String name, String description, boolean isIncome, int taxRefNum) {
        mID = id;
        mNameProperty = new SimpleStringProperty(name);
        mDescriptionProperty = new SimpleStringProperty(description);
        mIsIncomeProperty = new SimpleObjectProperty<>(isIncome);
        mTaxRefNumProperty = new SimpleIntegerProperty(taxRefNum);

        mBudgetAmountProperty = new SimpleObjectProperty<>(null);
    }

    // default constructor
    public Category() {
        this(0, "", "", true, -1);
    }

    void setID(int id) { mID = id; }
    int getID() { return mID; }

    StringProperty getNameProperty() { return mNameProperty; }
    String getName() { return getNameProperty().get(); }
    void setName(String name) { getNameProperty().set(name); }

    StringProperty getDescriptionProperty() { return mDescriptionProperty; }
    String getDescription() { return getDescriptionProperty().get(); }
    void setDescription(String des) { getDescriptionProperty().set(des); }

    ObjectProperty<Boolean> getIsIncomeProperty() { return mIsIncomeProperty; }
    Boolean getIsIncome() { return getIsIncomeProperty().get(); }
    void setIsIncome(boolean isIncome) { getIsIncomeProperty().set(isIncome); }

    void setIsTaxRelated(boolean t) {
        if (t && (getTaxRefNum() < 0)) {
            setTaxRefNum(0); // set it to be tax related
        } else if (!t) {
            setTaxRefNum(-1); // set it to be non tax related
        }
    }
    boolean isTaxRelated() { return getTaxRefNum() >= 0; }

    IntegerProperty getTaxRefNumProperty() { return mTaxRefNumProperty; }
    int getTaxRefNum() { return getTaxRefNumProperty().get(); }
    void setTaxRefNum(int r) { mTaxRefNumProperty.set(r); }

    ObjectProperty<BigDecimal> getBudgetAmountProperty() { return mBudgetAmountProperty; }
    BigDecimal getBudgetAmount() { return getBudgetAmountProperty().get(); }
    void setBudgetAmount(BigDecimal b) { getBudgetAmountProperty().set(b); }

    static Category fromQIFLines(List<String> lines)  {
        Category category = new Category();
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    category.setName(l.substring(1));
                    break;
                case 'D':
                    category.setDescription(l.substring(1));
                    break;
                case 'T':
                    category.setIsTaxRelated(true);
                    break;
                case 'R':
                    category.setTaxRefNum(Integer.parseInt(l.substring(1)));
                    break;
                case 'I':
                    category.setIsIncome(true);
                    break;
                case 'E':
                    category.setIsIncome(false);
                    break;
                case 'B':
                    category.setBudgetAmount(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                default:
                    return null;
            }
        }
        return category;
    }

    public String toString() { return "[" + getName() + "," + getDescription() + "]" ;}
}