package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
        private StringProperty mTradeActionProperty = new SimpleStringProperty();

        public LotInfo(int id, String n, String ta, LocalDate date,
                       BigDecimal price, BigDecimal quantity, BigDecimal costBasis) {
            super(n);
            mTransactionID = id;
            mDateProperty.set(date);
            mTradeActionProperty.set(ta);

            setQuantity(quantity);
            setCostBasis(costBasis);
            setPrice(price);  // this is the trade price
        }

        public ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
        public LocalDate getDate() { return mDateProperty.get(); }
        public int getTransactionID() { return mTransactionID; }
        public StringProperty getTradeActionProperty() { return mTradeActionProperty; }

        // compute market value and pnl
        @Override
        protected void updateMarketValue(BigDecimal p) {
            getMarketValueProperty().set(getQuantity().multiply(p));
            getPNLProperty().set(getMarketValue().subtract(getCostBasis()));
        }

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
        private final BigDecimal mMatchQuantity;  // always positive

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
/*
    public SecurityHolding(String n, List<LotInfo> lList) {
        super(n);

        if (lList == null)
            return;

        // we really don't need the price here
        updateAggregate(BigDecimal.ZERO);
    }
*/

    public SecurityHolding(String n) {
        super(n);
        //this(n, null);
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

    private BigDecimal scaleCostBasis(BigDecimal oldC, BigDecimal oldQ, BigDecimal newQ) {
        return oldC.multiply(newQ).divide(oldQ, oldC.scale(), BigDecimal.ROUND_HALF_UP);
    }

    // update marketvalue and PNL
    @Override
    protected void updateMarketValue(BigDecimal p) {
        setPrice(p);
        BigDecimal m = BigDecimal.ZERO;
        for (LotInfo li : getLotInfoList()) {
            li.updateMarketValue(p);
            m = m.add(li.getMarketValue());
        }
        getMarketValueProperty().set(m);
        getPNLProperty().set(m.subtract(getCostBasis()));
    }

    @Override
    protected void updatePctRet() {
        super.updatePctRet();
        getLotInfoList().forEach(LotInfo::updatePctRet);
    }

    // add the lot and match off if necessary
    // if the lots are added with wrong order, the cost basis
    // calculation will be wrong
    public void addLot(LotInfo lotInfo, List<MatchInfo> matchInfoList) {
        // Changing in quantity is independt of matching offsetting
        setQuantity(getQuantity().add(lotInfo.getQuantity()));

        if ((getQuantity().signum() > 0) && (lotInfo.getQuantity().signum() > 0)
            || ((getQuantity().signum() < 0) && (lotInfo.getQuantity().signum() < 0))) {
            // same sign, nothing of offset
            if (matchInfoList.size() > 0) {
                System.err.println("" + lotInfo.getTransactionID() + " can't find offset lots" );
            }
            getLotInfoList().add(lotInfo);
            setCostBasis(getCostBasis().add(lotInfo.getCostBasis()));
            return;
        }

        // lotInfo quantity has opposite sign as current holding, need to match lots
        if (lotInfo.getQuantity().abs().compareTo(getQuantity().abs()) > 0) {
            // more than offset the current?  we shouldn't be here
            // probably something went wrong.  Issue a error message
            // and then match offset anyway
            System.err.println("" + lotInfo.getTransactionID()
                    + " can't find enough lots to offset.  Something is wrong");

            BigDecimal newQuantity = lotInfo.getQuantity().add(getQuantity());
            lotInfo.setCostBasis(scaleCostBasis(lotInfo.getCostBasis(), lotInfo.getQuantity(), newQuantity));
            lotInfo.setQuantity(newQuantity);

            getLotInfoList().clear();
            getLotInfoList().add(lotInfo);
            setCostBasis(lotInfo.getCostBasis());
            return;
        }

        if (matchInfoList.size() == 0) {
            // offset with fifo rule
            Iterator<LotInfo> iter = getLotInfoList().iterator();
            while (iter.hasNext()) {
                LotInfo li = iter.next();
                if (li.getQuantity().abs().compareTo(lotInfo.getQuantity().abs()) > 0) {
                    // this lot in the list has more to offset the lotInfo
                    BigDecimal oldQ = li.getQuantity();
                    BigDecimal newQ = oldQ.add(lotInfo.getQuantity());
                    BigDecimal oldC = li.getCostBasis();
                    BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);
                    li.setCostBasis(newC);
                    li.setQuantity(newQ);
                    setCostBasis(getCostBasis().add(newC).subtract(oldC));
                    return;
                }

                // lotInfo may has more than li can offset
                BigDecimal oldC = lotInfo.getCostBasis();
                BigDecimal oldQ = lotInfo.getQuantity();
                BigDecimal newQ = oldQ.add(li.getQuantity());
                BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);

                lotInfo.setQuantity(newQ);
                lotInfo.setCostBasis(newC);

                setCostBasis(getCostBasis().subtract(li.getCostBasis()));

                iter.remove();  // li has been offset, remove

                if (lotInfo.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    // nothing left to offset
                    return;
                }
            }
        }

        // offset against matchinfo
        for (MatchInfo matchInfo : matchInfoList) {
            int matchTID = matchInfo.getMatchTransactionID();
            int matchIndex =  getLotIndex(matchTID);
            if (matchIndex < 0) {
                System.err.println("Missing matching transaction " + matchInfo.getMatchTransactionID());
                continue;
            }

            LotInfo matchLot = getLotInfoList().get(matchIndex);
            // getMatchQuantity() always none negative
            BigDecimal matchQ = matchInfo.getMatchQuantity();
            if (matchLot.getQuantity().abs().compareTo(matchQ) < 0 ) {
                // something is wrong here
                System.err.println("Match lot " + matchTID + " q = " + matchLot.getQuantity()
                        + ", needed " + matchQ + " to match. Reset match Q.");
                matchQ = matchLot.getQuantity().abs();
            }

            BigDecimal oldQ = matchLot.getQuantity();
            BigDecimal newQ = oldQ.compareTo(BigDecimal.ZERO) > 0 ? oldQ.subtract(matchQ) : oldQ.add(matchQ);
            BigDecimal oldC = matchLot.getCostBasis();
            BigDecimal newC = scaleCostBasis(oldC, oldQ, newQ);

            if (newQ.compareTo(BigDecimal.ZERO) == 0) {
                getLotInfoList().remove(matchLot);
            } else {
                matchLot.setQuantity(newQ);
                matchLot.setCostBasis(newC);
            }
            setQuantity(getQuantity().add(newQ).subtract(oldQ));
            setCostBasis(getCostBasis().add(newC).subtract(oldC));

            // update lotInfo Q and C
            oldQ = lotInfo.getQuantity();
            newQ = oldQ.compareTo(BigDecimal.ZERO) > 0 ? oldQ.subtract(matchQ) : oldQ.add(matchQ);
            oldC = lotInfo.getCostBasis();
            newC = scaleCostBasis(oldC, oldQ, newQ);
            lotInfo.setQuantity(newQ);
            lotInfo.setCostBasis(newC);
        }
    }

    // add the lot at the end
/*
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
*/

/*    @Override
    public void setPrice(BigDecimal p) {
        super.setPrice(p);

        for (LotInfo li : getLotInfoList()) {
            //li.setPrice(p);
            li.updateAggregate(p);
        }
    }
*/

    // todo should move this into addLot (involves changing logic a little
/*    @Override
    protected void updateAggregate(BigDecimal p) {
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

        // todo hack here
        super.updateAggregate(BigDecimal.ZERO);
    }
    */
}
