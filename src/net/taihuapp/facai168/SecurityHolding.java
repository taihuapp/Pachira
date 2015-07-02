package net.taihuapp.facai168;

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
            newCostBasis = getCostBasis().multiply(newQuantity).divide(getQuantity());
            openCostBasis = openLot.getCostBasis().multiply(openQuantity).divide(openLot.getQuantity());

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

    BigDecimal getPrice() { return mPriceProperty.get(); }

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

    public void addLot000(Transaction t, List<MatchInfo> matchList) {
        if (!t.getSecurityNameProperty().get().equals(getSecurityNameProperty().get())) {
            System.err.println("Mismatch security name, expecting " + getSecurityNameProperty().get() +
                    ", got " + t.getSecurityNameProperty().get() + ". shouldn't happen.  skip");
            return;
        }
        BigDecimal quantity = t.getQuantityProperty().get();
        if (quantity.compareTo(BigDecimal.ZERO) == 0)
            return;  // zero quantity

        BigDecimal costBasis = t.getInvestAmountProperty().get();
        LocalDate date = t.getDateProperty().get();

        for (MatchInfo matchPair : matchList) {
            if (matchPair.getTransactionID() != t.getID()) {
                System.err.println("Mismatch transaction ID.  Expecting " + t.getID() +
                        ", got " + matchPair.getTransactionID() + ".  Skipping");
                continue;
            }
            int mid = matchPair.getMatchTransactionID();
            int index = getLotIndex(mid);
            if (index < 0) {
                System.err.println("Missing matching transaction " + matchPair.getMatchQuantity() + ".");
                continue;
            }
            LotInfo matchingLot = mLotInfoList.get(index);
            BigDecimal openQuantity = matchingLot.getQuantity();
            BigDecimal openCostBasis = matchingLot.getCostBasis();
            if (quantity.signum()*openQuantity.signum() > 0) {
                System.err.println("Transaction " + matchPair.getTransactionID() + " and Matching Transaction " +
                        matchPair.getMatchTransactionID() + " are not offsetting: " +
                        quantity + " vs. " + openQuantity);
                continue;
            }

            BigDecimal matchQuantity = matchPair.getMatchQuantity();
            if (matchQuantity.abs().compareTo(quantity.abs()) > 0 ||
                    matchQuantity.abs().compareTo(openQuantity.abs()) > 0) {
                System.err.println("Inconsistent matching quantity: " +
                        quantity + "/" + matchQuantity + "/" + openQuantity);
                continue;
            }

            BigDecimal oldQuantity = quantity;
            BigDecimal oldOpenQuantity = openQuantity;
            if (quantity.signum() > 0) {
                quantity = quantity.subtract(matchQuantity.abs());
                openQuantity = openQuantity.add(matchQuantity.abs());
            } else {
                quantity = quantity.add(matchQuantity.abs());
                openQuantity = openQuantity.subtract(matchQuantity.abs());
            }
            openCostBasis = openCostBasis.multiply(openQuantity).divide(oldOpenQuantity);
            costBasis = costBasis.multiply(quantity).divide(oldQuantity);
            matchingLot.setQuantity(openQuantity);
            matchingLot.setCostBasis(openCostBasis);
        }

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return; // completely matched
        }

        if (matchList.size() > 0) {
            System.err.print("Transaction " + t.getID() + " matching residual: " + quantity);
        }
        mLotInfoList.add(new LotInfo(t.getID(), date, quantity, costBasis));
        //updateAggregate(quantity, costBasis);
    }

    public void addLot000(Transaction t) {
        if (!t.getSecurityNameProperty().get().equals(getSecurityNameProperty().get())) {
            System.err.println("Mismatch security name, expecting " + getSecurityNameProperty().get() +
                    ", got " + t.getSecurityNameProperty().get() + ". shouldn't happen.  skip");
            return;
        }
        BigDecimal quantity = t.getQuantityProperty().get();
        if (quantity == BigDecimal.ZERO)
            return; // nothing to do
        BigDecimal costBasis = t.getInvestAmountProperty().get();
        LocalDate date = t.getDateProperty().get();

        LotInfo newLot = new LotInfo(t.getID(), date, quantity, costBasis);
        int index = mLotInfoList.size()-1;
        while (index >= 0) {
            //todo
            // need to increment or de decrement index
            System.err.println("More work here in while loop");
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

            index--;
        }
        mLotInfoList.add(++index, newLot);

        // update aggregate
       // updateAggregate(quantity, costBasis);
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
/*
    private void updateAggregate(BigDecimal q, BigDecimal c, BigDecimal p) {
        BigDecimal quantity = mQuantityProperty.get().add(q);
        BigDecimal costBasis = mCostBasisProperty.get().add(c);
        BigDecimal marketValue = quantity.multiply(p);
        BigDecimal pnl = marketValue.subtract(costBasis);
        BigDecimal ret = BigDecimal.ZERO;
        if (costBasis.compareTo(BigDecimal.ZERO) != 0)
            ret = pnl.multiply(BigDecimal.valueOf(100)).divide(costBasis.abs(), 2, BigDecimal.ROUND_HALF_UP);

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

*/

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
