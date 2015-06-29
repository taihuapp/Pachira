package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ghe on 6/22/15.
 */
public class SecurityHolding {

    static class LotInfo implements Comparable<LotInfo> {
        private int mTransactionID;
        private ObjectProperty<LocalDate> mDateProperty = new SimpleObjectProperty<>();
        private ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>();
        private ObjectProperty<BigDecimal> mCostBasisProperty = new SimpleObjectProperty<>();

        public LotInfo(int id, LocalDate date, BigDecimal quantity, BigDecimal costBasis) {
            mTransactionID = id;
            mDateProperty.set(date);
            mQuantityProperty.set(quantity);
            mCostBasisProperty.set(costBasis);
        }

        public LocalDate getDateProperty() { return mDateProperty.get(); }
        public int getTransactionID() { return mTransactionID; }

        @Override
        public int compareTo(LotInfo l) {
            // a bit optimization here, if IDs are equal, then must be equal
            int idCompare = getTransactionID()-l.getTransactionID();
            if (idCompare == 0)
                return 0;

            // otherwise, order by date first, then by id within the same date
            int dateCompare = getDateProperty().compareTo(l.getDateProperty());
            if (dateCompare == 0)
                return idCompare;
            return dateCompare;
        }
    }

    private ObservableList<LotInfo> mLotInfoList = FXCollections.observableArrayList();

    private StringProperty mSecurityNameProperty = new SimpleStringProperty();
    private ObjectProperty<BigDecimal> mPriceProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mQuantityProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mMarketValueProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mCostBasisProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mPNLProperty = new SimpleObjectProperty<>();
    private ObjectProperty<BigDecimal> mPctRetProperty = new SimpleObjectProperty<>();

    // constructor
    public SecurityHolding(String n, List<LotInfo> lList) {
        mSecurityNameProperty.set(n);
        mQuantityProperty.set(BigDecimal.ZERO);
        mCostBasisProperty.set(BigDecimal.ZERO);
        mPriceProperty.set(BigDecimal.ZERO);

        if (lList == null)
            return;

        for (LotInfo l : lList) {
            mQuantityProperty.set(mQuantityProperty.get().add(l.mQuantityProperty.get()));
            mCostBasisProperty.set(mCostBasisProperty.get().add(l.mCostBasisProperty.get()));
        }

        updateAggregate();
    }

    public SecurityHolding(String n) {
        this(n, null);
    }

    public void addLot(Transaction t) {
        if (!t.getSecurityNameProperty().get().equals(getSecurityNameProperty().get())) {
            System.err.println("Mismatch security name, expecting " + getSecurityNameProperty().get() +
                    ", got " + t.getSecurityNameProperty().get() + ". shouldn't happen.  skip");
            return;
        }
        BigDecimal quantity = t.getQuantityProperty().get();
        if (quantity == BigDecimal.ZERO)
            return; // nothing to do
        BigDecimal costBasis = t.getInvestAmountProperty().get().negate();
        LocalDate date = t.getDateProperty().get();

        LotInfo newLot = new LotInfo(t.getID(), date, quantity, costBasis);
        int index = mLotInfoList.size()-1;
        while (index >= 0) {
            LotInfo l = mLotInfoList.get(index);
            int lotCompare = newLot.compareTo(l);
            if (lotCompare == 0) {
                System.err.println("Duplicated Transaction: " + t.toString() + ". Skipped.");
                return;
            }

            //
            if (lotCompare > 0) {
                // new lot should be after index
                break;
            }
        }
        mLotInfoList.add(++index, newLot);

        // update aggregate
        updateAggregate(quantity, costBasis);
    }

    private void updateAggregate(BigDecimal q, BigDecimal c, BigDecimal p) {
        BigDecimal quantity = mQuantityProperty.get().add(q);
        BigDecimal costBasis = mCostBasisProperty.get().add(c);
        BigDecimal marketValue = quantity.multiply(p);
        BigDecimal pnl = marketValue.subtract(costBasis);
        BigDecimal ret = BigDecimal.ZERO;
        if (costBasis != BigDecimal.ZERO)
            ret = pnl.multiply(BigDecimal.valueOf(100)).divide(costBasis, 2, BigDecimal.ROUND_HALF_UP);

        mCostBasisProperty.set(costBasis);
        mMarketValueProperty.set(marketValue);
        mPctRetProperty.set(ret);
        mPNLProperty.set(pnl);
        mPriceProperty.set(p);
        mQuantityProperty.set(quantity);
    }

    private void updateAggregate(BigDecimal q, BigDecimal c) {
        updateAggregate(q, c, mPriceProperty.get());
    }

    protected void updateAggregate() {
        updateAggregate(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void setPrice(BigDecimal p) {
        if (p == null)
            p = BigDecimal.ZERO;
        mPriceProperty.set(p);
        updateAggregate();
    }

    // getters
    public StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasisProperty; }
    public ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }
    public ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRetProperty; }

}
