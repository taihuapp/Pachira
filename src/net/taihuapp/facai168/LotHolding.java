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
    public StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasisProperty; }
    public BigDecimal getPrice() { return mPriceProperty.get(); }
    public BigDecimal getQuantity() { return mQuantityProperty.get(); }
    public BigDecimal getCostBasis() { return mCostBasisProperty.get(); }
    public BigDecimal getMarketValue() { return mMarketValueProperty.get(); }
    public BigDecimal getPNL() { return mPNLProperty.get(); }
    public BigDecimal getPctReturn() { return mPctRetProperty.get(); }

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

        updateAggregate();
    }

    // update Aggregate quantity
    protected void updateAggregate() {
        BigDecimal c = getCostBasisProperty().get();
        BigDecimal p = getPriceProperty().get();
        BigDecimal q = getQuantityProperty().get();
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

}
