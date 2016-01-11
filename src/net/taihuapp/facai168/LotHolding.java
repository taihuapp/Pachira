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
public abstract class LotHolding {

    private StringProperty mSecurityNameProperty = new SimpleStringProperty("");
    private ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mMarketValueProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mCostBasisProperty = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private ObjectProperty<BigDecimal> mPNLProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mPctRetProperty = new SimpleObjectProperty<>();

    // getters
    public abstract String getLabel();
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasisProperty; }
    public ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }
    public ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRetProperty; }
    public String getSecurityName() { return mSecurityNameProperty.get(); }
    public BigDecimal getPrice() { return mPriceProperty.get(); }
    public BigDecimal getQuantity() { return mQuantityProperty.get(); }
    public BigDecimal getCostBasis() { return mCostBasisProperty.get(); }
    public BigDecimal getMarketValue() { return mMarketValueProperty.get(); }
    public BigDecimal getPNL() { return mPNLProperty.get(); }
    public BigDecimal getPctRet() { return mPctRetProperty.get(); }

    // constructor
    public LotHolding(String n) { mSecurityNameProperty.set(n); }

    // setters
    protected void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
    protected void setCostBasis(BigDecimal c) { mCostBasisProperty.set(c); }
    public void setPrice(BigDecimal p) {
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

/*    protected void updatePNL(BigDecimal p) {
        System.err.println("Extending class should override updatePNL method");
    }
 */

/*
    // update Aggregate quantity
    protected void updateAggregate(BigDecimal p) {
        BigDecimal c = getCostBasis();
        BigDecimal q = getQuantity();
        BigDecimal m = q.multiply(p);
        BigDecimal pnl = m.subtract(c);
        mMarketValueProperty.set(m);
        mPNLProperty.set(pnl);
        if (c.compareTo(BigDecimal.ZERO) != 0) {
            mPctRetProperty.set((new BigDecimal(100)).multiply(pnl).divide(c.abs(), 2, BigDecimal.ROUND_HALF_UP));
        } else {
            mPctRetProperty.set(null);
        }
    }
*/

}
