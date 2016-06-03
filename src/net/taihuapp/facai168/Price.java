package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Created by ghe on 6/1/16.
 *
 */
class Price {

    private ObjectProperty<LocalDate> mDateProperty;
    private ObjectProperty<BigDecimal> mPriceProperty;

    Price(LocalDate d, BigDecimal p) {
        mDateProperty = new SimpleObjectProperty<>(d);
        mPriceProperty = new SimpleObjectProperty<>(p);
    }

    ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
    LocalDate getDate() { return getDateProperty().get(); }
    void setDate(LocalDate d) { getDateProperty().set(d); }

    ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    BigDecimal getPrice() { return getPriceProperty().get(); }
    void setPrice(BigDecimal p) { getPriceProperty().set(p); }
}
