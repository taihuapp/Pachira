/*
 * Copyright (C) 2018-2021.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Pachira.
 *
 * Pachira is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * Pachira is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.pachira.model;

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.util.Pair;
import net.taihuapp.pachira.*;
import net.taihuapp.pachira.dao.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

import static net.taihuapp.pachira.Transaction.TradeAction.STKSPLIT;

public class MainModel {

    private final DaoManager daoManager = DaoManager.getInstance();
    private final ObservableList<Account> accountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty(), a.getTypeProperty(),
                    a.getCurrentBalanceProperty() });
    private final ObjectProperty<Account> currentAccountProperty = new SimpleObjectProperty<>(null);

    private final ObservableList<Transaction> transactionList = FXCollections.observableArrayList();

    /**
     * Constructor - build up the MainModel object and load the accounts and transactions from database
     * @throws DaoException - from database operations
     */
    public MainModel() throws DaoException {
        accountList.setAll(((AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT)).getAll());
        transactionList.setAll(((TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION)).getAll());

        // transaction comparator for investing accounts
        final Comparator<Transaction> investingAccountTransactionComparator = Comparator
                .comparing(Transaction::getTDate)
                .thenComparing(Transaction::getID);

        // transaction comparator for other accounts
        final Comparator<Transaction> spendingAccountTransactionComparator = Comparator
                .comparing(Transaction::getTDate)
                .thenComparing(Transaction::cashFlow, Comparator.reverseOrder())
                .thenComparing(Transaction::getID);

        for (Account account : accountList) {
            final FilteredList<Transaction> filteredList = new FilteredList<>(transactionList,
                    t -> t.getAccountID() == account.getID());
            final SortedList<Transaction> sortedList = new SortedList<>(filteredList,
                            account.getType().isGroup(Account.Type.Group.INVESTING) ?
                                    investingAccountTransactionComparator : spendingAccountTransactionComparator);
            account.setTransactionList(sortedList);

            // computer security holding list and update account balance for each transaction
            // and set account balance
            List<SecurityHolding> shList = computeSecurityHoldings(sortedList, LocalDate.now(), -1);
            account.setCurrentBalance(shList.get(shList.size()-1).getMarketValue());
        }
    }

    /**
     * update account balances for the account fit the criteria
     * @param predicate - selecting criteria
     * @throws DaoException - from computeSecurityHoldings
     */
    public void updateAccountBalance(Predicate<Account> predicate) throws DaoException {
        FilteredList<Account> filteredList = new FilteredList<>(accountList, predicate);
        for (Account account : filteredList) {
            ObservableList<Transaction> transactionList = account.getTransactionList();
            List<SecurityHolding> shList = computeSecurityHoldings(transactionList, LocalDate.now(), -1);
            account.setCurrentBalance(shList.get(shList.size() - 1).getMarketValue());
        }
    }

    /**
     * get accounts fit the selecting criteria in default sorting order
     * @param predicate - selecting criteria
     * @return - list of accounts
     */
    public FilteredList<Account> getAccountList(Predicate<Account> predicate) {
        return new FilteredList<>(accountList, predicate);
    }

    /**
     * @param predicate - selecting criteria
     * @param comparator - sorting order
     * @return - accounts in a SortedList
     */
    public SortedList<Account> getAccountList(Predicate<Account> predicate, Comparator<Account> comparator) {
        return new SortedList<>(getAccountList(predicate), comparator);
    }

    /**
     * @param g - if null, no restrictions, otherwise, only accounts in group g
     * @param hidden - if null, no restrictions, otherwise, only accounts hidden flag matches hidden
     * @param exDeleted - if true, exclude deleted account, otherwise, include deleted account
     * @return accounts in as a SortedList
     */
    public SortedList<Account> getAccountList(Account.Type.Group g, Boolean hidden, Boolean exDeleted) {
        Predicate<Account> p = a ->  (g == null || a.getType().isGroup(g))
                && (hidden == null || a.getHiddenFlag().equals(hidden))
                && !(exDeleted && a.getName().equals(DaoManager.DELETED_ACCOUNT_NAME));
        Comparator<Account> c = Comparator.comparing(Account::getType)
                .thenComparing(Account::getDisplayOrder).thenComparing(Account::getID);
        return getAccountList(p, c);
    }

    /**
     * compute security holdings for a given transaction up to the given date, excluding a given transaction
     * the input list should have the same account id and ordered according the account type
     * @param tList - an observableList of transactions
     * @param date - the date to compute up to
     * @param exTid - the id of the transaction to be excluded.
     * @return - list of security holdings for the given date
     *           as a side effect, it will update running cash balance for each transaction
     */
    List<SecurityHolding> computeSecurityHoldings(ObservableList<Transaction> tList, LocalDate date, int exTid)
            throws DaoException {

        BigDecimal totalCash = BigDecimal.ZERO.setScale(SecurityHolding.CURRENCYDECIMALLEN, RoundingMode.HALF_UP);
        BigDecimal totalCashNow = totalCash;
        Map<String, SecurityHolding> shMap = new HashMap<>();  // map of security name and securityHolding
        Map<String, List<Transaction>> stockSplitTransactionListMap = new HashMap<>();

        // now loop through the sorted and filtered list
        for (Transaction t : tList) {
            totalCash = totalCash.add(t.getCashAmount().setScale(SecurityHolding.CURRENCYDECIMALLEN,
                    RoundingMode.HALF_UP));
            if (!t.getTDate().isAfter(date))
                totalCashNow = totalCash;

            t.setBalance(totalCash);

            if ((t.getTDate().equals(date) && t.getID() >= exTid) || t.getTDate().isAfter(date))
                continue;

            String name = t.getSecurityName();
            if (name != null && !name.isEmpty()) {
                // it has a security name
                SecurityHolding securityHolding = shMap.computeIfAbsent(name, SecurityHolding::new);
                if (t.getTradeAction() == STKSPLIT) {
                    securityHolding.adjustStockSplit(t.getQuantity(), t.getOldQuantity());
                    List<Transaction> splitList = stockSplitTransactionListMap.computeIfAbsent(name,
                            k -> new ArrayList<>());
                    splitList.add(t);
                } else if (Transaction.hasQuantity(t.getTradeAction())) {
                    LocalDate aDate = t.getADate();
                    if (aDate == null)
                        aDate = t.getTDate();
                    securityHolding.addLot(new SecurityHolding.LotInfo(t.getID(), name, t.getTradeAction(), aDate,
                            t.getPrice(), t.getSignedQuantity(), t.getCostBasis()), getMatchInfoList(t.getID()));
                }
            }
        }

        BigDecimal totalMarketValue = totalCashNow;
        BigDecimal totalCostBasis = totalCashNow;
        SecurityDao securityDao = (SecurityDao) daoManager.getDao(DaoManager.DaoType.SECURITY);
        SecurityPriceDao securityPriceDao = (SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE);
        List<SecurityHolding> shList = new ArrayList<>(shMap.values());
        shList.removeIf(sh -> sh.getQuantity().setScale(DaoManager.QUANTITY_FRACTION_DISPLAY_LEN,
                RoundingMode.HALF_UP).signum() == 0);
        for (SecurityHolding sh : shList) {
            Price price = null;
            BigDecimal p = BigDecimal.ZERO;
            Optional<Security> securityOptional = securityDao.get(sh.getSecurityName());
            if (securityOptional.isPresent()) {
                Optional<Pair<Security, Price>> optionalSecurityPricePair =
                        securityPriceDao.getLastPrice(new Pair<>(securityOptional.get(), date));
                if (optionalSecurityPricePair.isPresent()) {
                    price = optionalSecurityPricePair.get().getValue();
                    p = price.getPrice();
                }
            }
            if (price != null && price.getDate().isBefore(date)) {
                // need to check if there is any stock split between price.getDate() and date
                List<Transaction> splitList = stockSplitTransactionListMap.get(sh.getSecurityName());
                if (splitList != null) {
                    // w have a list of stock splits, check now
                    // since this list is order by date, we start from the end
                    ListIterator<Transaction> li = splitList.listIterator(splitList.size());
                    while (li.hasPrevious()) {
                        Transaction t = li.previous();
                        if (t.getTDate().isBefore(price.getDate()))
                            break;
                        p = p.multiply(t.getOldQuantity()).divide(t.getQuantity(), DaoManager.PRICE_FRACTION_LEN,
                                RoundingMode.HALF_UP);
                    }
                }
            }
            sh.updateMarketValue(p);
            sh.updatePctRet();

            totalMarketValue = totalMarketValue.add(sh.getMarketValue());
            totalCostBasis = totalCostBasis.add(sh.getCostBasis());
        }

        SecurityHolding cashHolding = new SecurityHolding("CASH");
        cashHolding.setPrice(null);
        cashHolding.setQuantity(null);
        cashHolding.setCostBasis(totalCash);
        cashHolding.setMarketValue(totalCash);

        SecurityHolding totalHolding = new SecurityHolding("TOTAL");
        totalHolding.setMarketValue(totalMarketValue);
        totalHolding.setQuantity(null);
        totalHolding.setCostBasis(totalCostBasis);
        totalHolding.setPNL(totalMarketValue.subtract(totalCostBasis));

        shList.sort(Comparator.comparing(SecurityHolding::getSecurityName));
        if (totalCash.signum() != 0)
            shList.add(cashHolding);
        shList.add(totalHolding);
        return shList;
    }

    /**
     * get MatchInfoList for a given transaction id
     * @param tid transaction id
     * @return list of MatchInfo for the transaction with id tid
     * @throws DaoException - from Dao operations
     */
    private List<SecurityHolding.MatchInfo> getMatchInfoList(int tid) throws DaoException {
        return ((PairTidMatchInfoListDao) DaoManager.getInstance().getDao(DaoManager.DaoType.PAIR_TID_MATCH_INFO))
                .get(tid).map(Pair::getValue).orElse(new ArrayList<>());
    }

    public ObjectProperty<Account> getCurrentAccountProperty() { return currentAccountProperty; }

    public void setCurrentAccount(Account account) { getCurrentAccountProperty().set(account); }
}
