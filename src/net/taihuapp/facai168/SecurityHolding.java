package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ghe on 6/22/15.
 * SecurityHolding class
 */
public class SecurityHolding extends LotHolding {

    static class LotInfo extends LotHolding implements Comparable<LotInfo> {

        private int mTransactionID;
        private ObjectProperty<LocalDate> mDateProperty = new SimpleObjectProperty<>();

        public LotInfo(int id, String n, LocalDate date, BigDecimal quantity, BigDecimal costBasis) {
            super(n);
            mTransactionID = id;
            mDateProperty.set(date);

            setQuantity(quantity);
            setCostBasis(costBasis);
        }

        public LocalDate getDate() { return mDateProperty.get(); }
        public int getTransactionID() { return mTransactionID; }

        @Override
        public String getLabel() { return getDate().toString(); }

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

    // constructor
    public SecurityHolding(String n, List<LotInfo> lList) {
        super(n);

        if (lList == null)
            return;

        updateAggregate();
    }

    public SecurityHolding(String n) {
        this(n, null);
    }

    // getters
    @Override
    public String getLabel() { return getSecurityName(); }

    private int getLotIndex(int tid) {
        for (int idx = 0; idx < mLotInfoList.size(); idx++) {
            if (mLotInfoList.get(idx).getTransactionID() == tid)
                return idx;
        }
        return -1;
    }

    public ObservableList<LotInfo> getLotInfoList() { return mLotInfoList; }

    // add the lot at the end
    public void addLot(LotInfo lotInfo) {
        setQuantity(getQuantity().add(lotInfo.getQuantity()));
        mLotInfoList.add(lotInfo);
    }

    public void addLot(LotInfo lotInfo, List<MatchInfo> matchInfoList) {
        setQuantity(getQuantity().add(lotInfo.getQuantity()));
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

    @Override
    public void setPrice(BigDecimal p) {
        super.setPrice(p);

        for (LotInfo li : getLotInfoList()) {
            li.setPrice(p);
        }
    }

    // todo should move this into addLot (involves changing logic a little
    @Override
    protected void updateAggregate() {
        // start from fresh
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;

        // todo
        // merge the two loops
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

        for (Iterator<LotInfo> iter = mLotInfoList.iterator(); iter.hasNext(); ) {
            LotInfo li = iter.next();
            if (li.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                iter.remove();
        }

        setQuantity(quantity);
        setCostBasis(costBasis);
        super.updateAggregate();
    }
}
