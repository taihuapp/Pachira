package net.taihuapp.facai168;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // copy construcgtor
        // how to chain constructor???
        public LotInfo(LotInfo li0) {
            super(li0.getSecurityName());
            mTransactionID = li0.getTransactionID();
            mDateProperty.set(li0.getDate());
            mTradeActionProperty.set(li0.getTradeAction());

            setQuantity(li0.getQuantity());
            setCostBasis(li0.getCostBasis());
            setPrice(li0.getPrice());
        }

        // constructor
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

        ObjectProperty<LocalDate> getDateProperty() { return mDateProperty; }
        LocalDate getDate() { return mDateProperty.get(); }
        int getTransactionID() { return mTransactionID; }
        StringProperty getTradeActionProperty() { return mTradeActionProperty; }
        String getTradeAction() { return getTradeActionProperty().get(); }

        // compute market value and pnl
        @Override
        protected void updateMarketValue(BigDecimal p) {
            getMarketValueProperty().set(getQuantity().multiply(p));
            getPNLProperty().set(getMarketValue().subtract(getCostBasis()));
        }

        @Override
        public String getLabel() { return getDate().toString(); }

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

        MatchInfo(int tid, int mid, BigDecimal q) {
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

    ObservableList<LotInfo> getLotInfoList() { return mLotInfoList; }

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
    void addLot(LotInfo lotInfo, List<MatchInfo> matchInfoList) {
        BigDecimal oldQuantity = getQuantity();

        // update total quantity here
        BigDecimal lotInfoQuantity = lotInfo.getQuantity();
        if (lotInfoQuantity == null)
            return;  // nothing todo here
        setQuantity(oldQuantity.add(lotInfoQuantity));

        if ((oldQuantity.signum() >= 0) && (lotInfoQuantity.signum() > 0)
            || ((oldQuantity.signum() <= 0) && (lotInfo.getQuantity().signum() < 0))) {
            // same sign, nothing of offset
            if (matchInfoList.size() > 0) {
                System.err.println("" + lotInfo.getTransactionID() + " can't find offset lots" );
            }
            getLotInfoList().add(lotInfo);
            setCostBasis(getCostBasis().add(lotInfo.getCostBasis()));
            return;
        }

        // lotInfo quantity has opposite sign as current holding, need to match lots
        if (lotInfoQuantity.abs().compareTo(oldQuantity.abs()) > 0) {
            // more than offset the current?  we shouldn't be here
            // probably something went wrong.  Issue a error message
            // and then match offset anyway
            System.err.println("" + lotInfo.getTransactionID()
                    + " can't find enough lots to offset.  Something is wrong");

            BigDecimal newQuantity = lotInfo.getQuantity().add(oldQuantity);
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

    void adjustStockSplit(BigDecimal newQuantity, BigDecimal oldQuantity) {
        BigDecimal newQTotal = BigDecimal.ZERO;
        for (LotInfo li : getLotInfoList()) {
            BigDecimal oldQ = li.getQuantity();
            BigDecimal oldP = li.getPrice();
            BigDecimal newQ = oldQ.multiply(newQuantity).divide(oldQuantity, oldQ.scale(),
                    BigDecimal.ROUND_HALF_UP);
            newQTotal = newQTotal.add(newQ);
            li.setQuantity(newQ);
            li.setPrice(li.getPrice().multiply(oldQuantity).divide(newQuantity, oldP.scale(),
                    RoundingMode.HALF_UP));
        }
        setQuantity(newQTotal);
    }
}