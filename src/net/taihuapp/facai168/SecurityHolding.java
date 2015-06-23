package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;

/**
 * Created by ghe on 6/22/15.
 */
public class SecurityHolding {
    private StringProperty mSecurityName;
    private ObjectProperty<BigDecimal> mPrice;
    private ObjectProperty<BigDecimal> mQuantity;
    private ObjectProperty<BigDecimal> mMarketValue;
    private ObjectProperty<BigDecimal> mCostBasis;
    private ObjectProperty<BigDecimal> mPNL;
    private ObjectProperty<BigDecimal> mPctRet;

    // constructor
    public SecurityHolding(String n, BigDecimal p, BigDecimal q, BigDecimal c) {
        mSecurityName.set(n);
        mPrice.set(p);
        mQuantity.set(q);
        mCostBasis.set(c);

        BigDecimal marketValue = p.multiply(q);
        mMarketValue.set(marketValue);
        mPNL.set(marketValue.subtract(c));
        mPctRet.set(marketValue.divide(c).subtract(BigDecimal.ONE));
    }

    // getters
    public StringProperty getSecurityNameProperty() { return mSecurityName; }
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPrice; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantity; }
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValue; }
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasis; }
    public ObjectProperty<BigDecimal> getPNLProperty() { return mPNL; }
    public ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRet; }

}
