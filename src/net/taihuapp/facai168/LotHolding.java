package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;

/**
 * Created by ghe on 7/7/15.
 * Base class for holdings and lot information
 */
abstract class LotHolding {

    private StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mMarketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mCostBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mPNLProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mPctRetProperty = new SimpleObjectProperty<>();

    // getters
    abstract String getLabel();
    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasisProperty; }
    ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }
    private ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRetProperty; }
    String getSecurityName() { return mSecurityNameProperty.get(); }
    BigDecimal getPrice() { return mPriceProperty.get(); }
    BigDecimal getQuantity() { return mQuantityProperty.get(); }
    BigDecimal getCostBasis() { return mCostBasisProperty.get(); }
    BigDecimal getMarketValue() { return mMarketValueProperty.get(); }
    BigDecimal getPNL() { return mPNLProperty.get(); }
    BigDecimal getPctRet() { return mPctRetProperty.get(); }

    // constructor
    LotHolding(String n) { mSecurityNameProperty.set(n); }

    // setters
    void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    void setCostBasis(BigDecimal c) { mCostBasisProperty.set(c); }
    void setPrice(BigDecimal p) {
        if (p == null)
            mPriceProperty.set(BigDecimal.ZERO);
        else
            mPriceProperty.set(p);
    }

    protected abstract void updateMarketValue(BigDecimal p); // update market value for price p

    // update PctRet, assume MarketValue, PNL, and cost basis are updated
    protected void updatePctRet() {
        BigDecimal c = getCostBasis();
        if (c.compareTo(BigDecimal.ZERO) != 0) {
            getPctRetProperty().set((new BigDecimal(100)).multiply(getPNL())
                    .divide(c.abs(), 2, BigDecimal.ROUND_HALF_UP));
        } else {
            getPNLProperty().set(null);
        }
    }
}
