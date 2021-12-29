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

package net.taihuapp.pachira;

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import net.taihuapp.pachira.dao.DaoException;
import net.taihuapp.pachira.dao.DaoManager;
import net.taihuapp.pachira.dao.ReminderDao;
import net.taihuapp.pachira.dao.ReminderTransactionDao;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * a class to manage Reminders and Reminder Transactions
 */
public class ReminderModel {

    private static final Logger logger = Logger.getLogger(ReminderModel.class);

    private final MainModel mainModel; // we need a MainModel reference to handle transactions, accounts, etc
    private final Map<Integer, Reminder> reminderIdMap = new HashMap<>();
    private final ObservableList<ReminderTransaction> reminderTransactions = FXCollections.observableArrayList(
            rt -> new Observable[]{ rt.getDueDateProperty(), rt.getTransactionIDProperty() });

    // constructor
    ReminderModel(MainModel mainModel) throws DaoException, ModelException {
        this.mainModel = mainModel;

        // get dao manager
        final DaoManager daoManager = DaoManager.getInstance();

        // setup reminder id map
        for (Reminder r : ((ReminderDao) daoManager.getDao(DaoManager.DaoType.REMINDER)).getAll()) {
            reminderIdMap.put(r.getID(), r);
        }

        // setup reminder transaction list
        final List<ReminderTransaction> rtList =
                ((ReminderTransactionDao) daoManager.getDao(DaoManager.DaoType.REMINDER_TRANSACTION)).getAll();
        final Set<Integer> missingReminderIdSet = new TreeSet<>();
        final Map<Integer, List<ReminderTransaction>> rtMap = new HashMap<>();

        int cnt = 0;
        for (ReminderTransaction rt : rtList) {
            if (reminderIdMap.containsKey(rt.getReminderId())) {
                reminderTransactions.add(rt); // add to the master list
                rtMap.computeIfAbsent(rt.getReminderId(), k -> new ArrayList<>()).add(rt);
            } else {
                missingReminderIdSet.add(rt.getReminderId());
                cnt++;
            }
        }

        if (cnt > 0) {
            logger.warn(cnt + " reminder transaction without valid reminder.  Missing reminder ids: "
                    + missingReminderIdSet);
        }

        updateReminderTransactionList();
    }

    Reminder getReminder(int reminderId) { return reminderIdMap.get(reminderId); }

    // delete a reminder, also delete all reminder transaction associated with the reminder.
    void deleteReminder(int rId) throws DaoException {
        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            ((ReminderDao) daoManager.getDao(DaoManager.DaoType.REMINDER)).delete(rId);
            ((ReminderTransactionDao) daoManager.getDao(DaoManager.DaoType.REMINDER_TRANSACTION))
                    .deleteByReminderId(rId);
            daoManager.commit();

            reminderIdMap.remove(rId);
            reminderTransactions.removeIf(rt -> rt.getReminderId() == rId);
        } catch (DaoException e) {
            // there was a database error
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    ObjectProperty<BigDecimal> getReminderTransactionAmountProperty(ReminderTransaction reminderTransaction) {
        if (reminderTransaction.getTransactionID() < 0) {
            return reminderIdMap.get(reminderTransaction.getReminderId()).getAmountProperty();
        } else {
            return mainModel.getTransactionByID(reminderTransaction.getTransactionID())
                    .map(Transaction::getAmountProperty).orElse(null);
        }
    }

    // update un-executed reminder transactions for all reminders
    private void updateReminderTransactionList() throws DaoException, ModelException {
        for (Reminder r : reminderIdMap.values()) {
            updateReminderTransactionList(r);
        }
    }

    Set<Integer> getLoanReminderLoanAccountIdSet() {
        // loan account id is stored in category id in a loan reminder.
        return reminderIdMap.values().stream().filter(r -> r.getType() == Reminder.Type.LOAN_PAYMENT)
                .map(r -> -r.getCategoryID()).collect(Collectors.toSet());
    }

    // update un-executed reminder transaction for reminder
    private void updateReminderTransactionList(Reminder reminder) throws DaoException, ModelException {

        final FilteredList<ReminderTransaction> rtList = new FilteredList<>(reminderTransactions,
                rt -> rt.getReminderId() == reminder.getID() && rt.isCompletedOrSkipped());
        SortedList<ReminderTransaction> sortedList =
                new SortedList<>(rtList, Comparator.comparing(ReminderTransaction::getDueDate).reversed());

        final DateSchedule ds = reminder.getDateSchedule();
        final LocalDate nextDueDate = sortedList.isEmpty() || sortedList.get(0).getDueDate().isBefore(ds.getStartDate())?
                ds.getStartDate() : ds.getNextDueDate(sortedList.get(0).getDueDate());
        if (ds.getEndDate() != null && nextDueDate.isAfter(ds.getEndDate()))
            return; // we are done here

        if (reminder.getType() == Reminder.Type.LOAN_PAYMENT) {
            final int loanAccountId = -reminder.getSplitTransactionList().get(0).getCategoryID();
            final Loan loan = mainModel.getLoan(loanAccountId)
                    .orElseThrow(() -> new ModelException(ModelException.ErrorCode.LOAN_NOT_FOUND,
                            "Missing loan with account id = " + loanAccountId, null));
            final Loan.PaymentItem paymentItem = loan.getPaymentItem(nextDueDate)
                    .orElseThrow(() -> new ModelException(ModelException.ErrorCode.LOAN_PAYMENT_NOT_FOUND,
                            "Missing payment item on " + nextDueDate, null));
            // update principal and interest payment
            List<SplitTransaction> stList = reminder.getSplitTransactionList();
            stList.get(0).setAmount(paymentItem.getPrincipalAmount().negate());
            stList.get(1).setAmount(paymentItem.getInterestAmount().negate());

            reminder.setAmount(stList.stream().map(SplitTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).abs());
        } else {
            final int estCnt = reminder.getEstimateCount();
            final BigDecimal amt;
            if (estCnt > 0) {
                final int n = Math.min(estCnt, sortedList.size());
                if (n == 0) {
                    // a new Reminder, no reminder transaction yet
                    amt = BigDecimal.ZERO;
                } else {
                    BigDecimal sum = BigDecimal.ZERO;
                    for (int i = 0; i < n; i++) {
                        sum = sum.add(mainModel.getTransactionByID(sortedList.get(i).getTransactionID())
                                .map(Transaction::getAmount).orElse(BigDecimal.ZERO));
                    }
                    amt = sum.divide(BigDecimal.valueOf(n), DaoManager.AMOUNT_FRACTION_LEN, RoundingMode.HALF_UP);
                }
                reminder.setAmount(amt);
            }
        }
        reminderTransactions.removeIf(rt -> rt.getReminderId() == reminder.getID() && !rt.isCompletedOrSkipped());

        reminderTransactions.add(new ReminderTransaction(reminder.getID(), nextDueDate, -1));
    }

    /*
     * if reminder ID is positive, the reminder is used to update the database.
     * otherwise, the reminder is insert to the database, the returning id is updated in reminder
     *
     * reminderIdMap is updated
     */
    void insertUpdateReminder(Reminder reminder) throws DaoException, ModelException {
        DaoManager daoManager = DaoManager.getInstance();
        int rid = reminder.getID();
        if (rid > 0)
            ((ReminderDao) daoManager.getDao(DaoManager.DaoType.REMINDER)).update(reminder);
        else {
            rid = ((ReminderDao) daoManager.getDao(DaoManager.DaoType.REMINDER)).insert(reminder);
            reminder.setID(rid);
        }

        reminderIdMap.put(rid, reminder);

        updateReminderTransactionList(reminder);
    }

    // return an instance of mainModel
    MainModel getMainModel() { return mainModel; }

    // return the reminder transaction list
    ObservableList<ReminderTransaction> getReminderTransactions() { return reminderTransactions; }

    // insert reminder transaction to database and also update reminder transaction list.
    void insertReminderTransaction(ReminderTransaction rt) throws DaoException, ModelException {

        final Reminder reminder = getReminder(rt.getReminderId());

        DaoManager daoManager = DaoManager.getInstance();
        try {
            daoManager.beginTransaction();
            if (reminder.getType() == Reminder.Type.LOAN_PAYMENT) {
                mainModel.insertLoanTransaction(new LoanTransaction(-1, LoanTransaction.Type.REGULAR_PAYMENT,
                        -reminder.getSplitTransactionList().get(0).getCategoryID(), rt.getTransactionID(),
                        rt.getDueDate(), BigDecimal.ZERO, BigDecimal.ZERO));
            }

            ((ReminderTransactionDao) daoManager.getDao(DaoManager.DaoType.REMINDER_TRANSACTION)).insert(rt);

            daoManager.commit();

            reminderTransactions.add(rt);

            updateReminderTransactionList(reminder);
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    // return a skeleton transaction based on the reminder transaction.
    Transaction getReminderTransactionTemplate(ReminderTransaction reminderTransaction)
            throws DaoException, ModelException {
        final Reminder reminder = getReminder(reminderTransaction.getReminderId());

        // make a copy of split transactions
        final List<SplitTransaction> stList = new ArrayList<>();
        for (SplitTransaction st : reminder.getSplitTransactionList()) {
            final SplitTransaction stCopy = new SplitTransaction(st);
            stCopy.setID(0);
            stList.add(stCopy);
        }

        final BigDecimal amount;
        final Transaction.TradeAction tradeAction;
        switch (reminder.getType()) {
            case DEPOSIT:
                amount = reminder.getAmount();
                tradeAction = Transaction.TradeAction.DEPOSIT;
                break;
            case PAYMENT:
                amount = reminder.getAmount();
                tradeAction = Transaction.TradeAction.WITHDRAW;
                break;
            case LOAN_PAYMENT:
                amount = reminder.getSplitTransactionList().stream().map(SplitTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                tradeAction = (amount.compareTo(BigDecimal.ZERO) > 0) ?
                        Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW;
                break;
            default:
                throw new IllegalArgumentException(reminder.getType() + " not implemented");
        }

        final int categoryId = reminder.getSplitTransactionList().isEmpty() ? reminder.getCategoryID() : 0;
        final Transaction transaction = new Transaction(reminder.getAccountID(), reminderTransaction.getDueDate(),
                tradeAction, categoryId);
        transaction.setAmount(amount.abs());
        transaction.setPayee(reminder.getPayee());
        transaction.setMemo(reminder.getMemo());
        transaction.setSplitTransactionList(stList);
        transaction.setTagID(reminder.getTagID());

        return transaction;
    }
}
