package net.taihuapp.facai168;

import java.math.BigDecimal;

/**
 * Created by ghe on 6/28/15.
 * Cash holding class
 */

class CashHolding extends SecurityHolding {
    public CashHolding() {
        super("CASH");
        super.setPrice(BigDecimal.ONE);
    }

    @Override
    public void addLot(Transaction t) {
        BigDecimal total = t.getCashAmountProperty().get().add(getCostBasisProperty().get());
        getCostBasisProperty().set(total);
        getQuantityProperty().set(total);
        updateAggregate();
    }
}
