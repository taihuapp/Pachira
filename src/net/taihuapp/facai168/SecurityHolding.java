package net.taihuapp.facai168;

import com.sun.istack.internal.NotNull;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Created by ghe on 6/22/15.
 * SecurityHolding class
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

        public LocalDate getDate() { return mDateProperty.get(); }
        public int getTransactionID() { return mTransactionID; }
        public BigDecimal getQuantity() { return mQuantityProperty.get(); }
        public BigDecimal getCostBasis() { return mCostBasisProperty.get(); }

        public void setQuantity(BigDecimal q) { mQuantityProperty.set(q); }
        public void setCostBasis(BigDecimal c) { mCostBasisProperty.set(c); }

        // return true for success, false for failure
        public boolean lotMatch(LotInfo openLot, BigDecimal matchAmt) {
            if (matchAmt.compareTo(BigDecimal.ZERO) == 0)
                return true;  // nothing to match

            if ((getQuantity().abs().compareTo(matchAmt) < 0) ||
                    (openLot.getQuantity().abs().compareTo(matchAmt) < 0)) {
                // matchAmt bigger than quantity, can't be
                System.err.println("LotInfo::lotMatch inconsistent quantity: "
                        + getTransactionID() + " (" + getQuantity() + ")/"
                        + openLot.getTransactionID() + " (" + openLot.getQuantity() + ")/"
                        + matchAmt);
                return false;
            }

            if (getQuantity().signum()*openLot.getQuantity().signum() > 0) {
                System.err.println("LotInfo::lotMatch lots not offsetting: "
                        + getTransactionID() + " (" + getQuantity() + ")/"
                        + openLot.getTransactionID() + " (" + openLot.getQuantity() + ")/"
                        + matchAmt);
                return false;
            }

            BigDecimal newQuantity, newCostBasis, openQuantity, openCostBasis;
            if (getQuantity().signum () > 0) {
                newQuantity = getQuantity().subtract(matchAmt);
                openQuantity = openLot.getQuantity().add(matchAmt);

            } else {
                newQuantity = getQuantity().add(matchAmt);
                openQuantity = openLot.getQuantity().subtract(matchAmt);
            }

            int scale = getQuantity().scale();

            newCostBasis = getCostBasis().multiply(newQuantity).divide(getQuantity(),
                    scale, BigDecimal.ROUND_HALF_UP);
            openCostBasis = openLot.getCostBasis().multiply(openQuantity).divide(openLot.getQuantity(),
                    scale, BigDecimal.ROUND_HALF_UP);

            openLot.setQuantity(openQuantity);
            openLot.setCostBasis(openCostBasis);

            setQuantity(newQuantity);
            setCostBasis(newCostBasis);

            return true;
        }

        @Override
        public int compareTo(LotInfo l) {
            // a bit optimization here, if IDs are equal, then must be equal
            int idCompare = getTransactionID()-l.getTransactionID();
            if (idCompare == 0)
                return 0;

            // otherwise, order by date first, then by id within the same date
            int dateCompare = getDate().compareTo(l.getDate());
            if (dateCompare == 0)
                return idCompare;
            return dateCompare;
        }
    }

    static class MatchInfo {
        private final int mTransactionID;
        private final int mMatchTransactionID;
        private final BigDecimal mMatchQuantity;

        public MatchInfo(int tid, int mid, BigDecimal q) {
            mTransactionID = tid;
            mMatchTransactionID = mid;
            mMatchQuantity = q;
        }

        // getters
        int getTransactionID() { return mTransactionID; }
        int getMatchTransactionID() { return mMatchTransactionID; }
        BigDecimal getMatchQuantity() { return mMatchQuantity; }
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

        updateAggregate();
    }

    public SecurityHolding(String n) {
        this(n, null);
    }

    // getters
    public StringProperty getSecurityNameProperty() { return mSecurityNameProperty; }
    public ObjectProperty<BigDecimal> getPriceProperty() { return mPriceProperty; }
    public ObjectProperty<BigDecimal> getQuantityProperty() { return mQuantityProperty; }
    public ObjectProperty<BigDecimal> getMarketValueProperty() { return mMarketValueProperty; }
    public ObjectProperty<BigDecimal> getCostBasisProperty() { return mCostBasisProperty; }
    public ObjectProperty<BigDecimal> getPNLProperty() { return mPNLProperty; }
    public ObjectProperty<BigDecimal> getPctRetProperty() { return mPctRetProperty; }
    public BigDecimal getPrice() { return mPriceProperty.get(); }

    private int getLotIndex(int tid) {
        for (int idx = 0; idx < mLotInfoList.size(); idx++) {
            if (mLotInfoList.get(idx).getTransactionID() == tid)
                return idx;
        }
        return -1;
    }

    // add the lot at the end
    public void addLot(LotInfo lotInfo) {
        mLotInfoList.add(lotInfo);
    }

    public void addLot(LotInfo lotInfo, List<MatchInfo> matchInfoList) {
        if (matchInfoList.size() == 0) {
            // no specific lot to offset, simply add to the end
            mLotInfoList.add(lotInfo);
            return;
        }

        for (MatchInfo matchInfo : matchInfoList) {
            int index = getLotIndex(matchInfo.getMatchTransactionID());
            if (index < 0) {
                System.err.println("Missing matching transaction " + matchInfo.getMatchTransactionID());
                continue;
            }
            LotInfo matchingLot = mLotInfoList.get(index);
            lotInfo.lotMatch(matchingLot, matchInfo.getMatchQuantity());
        }

        if (lotInfo.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
            System.err.println("LotInfo::addLot: lotMatch not complete" + lotInfo.getTransactionID());
            mLotInfoList.add(lotInfo); // add to the end
        }
    }

    protected void updateAggregate() {
        // start from fresh
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;

        int openIdx = 0;
        int lsSign = 0;
        for (int i = 0; i < mLotInfoList.size(); i++) {
            LotInfo lotInfo = mLotInfoList.get(i);
            BigDecimal q = lotInfo.getQuantity();
            int lotSign = q.signum();
            if (lsSign*lotSign < 0) {
                // lotInfo has the opposite sign as previous lot
                while (openIdx < i) {
                    LotInfo openLot = mLotInfoList.get(openIdx);
                    BigDecimal oldQuantity = openLot.getQuantity();
                    BigDecimal oldCostBasis = openLot.getCostBasis();
                    lotInfo.lotMatch(openLot, lotInfo.getQuantity().abs().min(oldQuantity.abs()));
                    // update costBasis
                    costBasis = costBasis.subtract(oldCostBasis.subtract(openLot.getCostBasis()));
                    if (lotInfo.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                        break;  // we

                    openIdx++;
                }
            }

            quantity = quantity.add(q);
            costBasis = costBasis.add(lotInfo.getCostBasis());
            lsSign = quantity.signum();
        }

        mQuantityProperty.set(quantity);
        mCostBasisProperty.set(costBasis);
        mMarketValueProperty.set(quantity.multiply(getPrice()));
    }

    public void setPrice(BigDecimal p) {
        if (p == null)
            p = BigDecimal.ZERO;
        mPriceProperty.set(p);
        updateAggregate();
    }

}
