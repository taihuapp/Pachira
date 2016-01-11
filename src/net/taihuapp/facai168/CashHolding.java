package net.taihuapp.facai168;

import java.math.BigDecimal;

/**
 * Created by ghe on 6/28/15.
 * Cash holding class
 */

class CashHolding extends SecurityHolding {
    public CashHolding() {
        super("CASH");
        setPrice(BigDecimal.ONE);
    }

    public void addLot(LotInfo lotInfo) {
        BigDecimal total = lotInfo.getQuantity().add(getCostBasisProperty().get());
        getCostBasisProperty().set(total);
        getQuantityProperty().set(total);
        getMarketValueProperty().set(total);
    }
}
