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

import com.opencsv.CSVReader;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.util.Pair;
import net.taihuapp.pachira.dao.*;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.taihuapp.pachira.Transaction.TradeAction.*;

public class MainModel {

    public enum InsertMode { DB_ONLY, MEM_ONLY, BOTH }

    private static final Logger logger = Logger.getLogger(MainModel.class);

    private final DaoManager daoManager = DaoManager.getInstance();
    private final ObservableList<Account> accountList = FXCollections.observableArrayList(
            a -> new Observable[] { a.getHiddenFlagProperty(), a.getDisplayOrderProperty(), a.getTypeProperty(),
                    a.getCurrentBalanceProperty() });
    private final ObjectProperty<Account> currentAccountProperty = new SimpleObjectProperty<>(null);

    // transactionList should be sorted according to getID
    private final ObservableList<Transaction> transactionList = FXCollections.observableArrayList();
    private final ObservableList<AccountDC> accountDCList = FXCollections.observableArrayList();
    private final ObservableList<Security> securityList = FXCollections.observableArrayList();
    private final ObservableList<Tag> tagList = FXCollections.observableArrayList();
    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();

    private final Vault vault = new Vault();
    public final BooleanProperty hasMasterPasswordProperty = new SimpleBooleanProperty(false);
    private final ObservableList<DirectConnection> dcInfoList = FXCollections.observableArrayList();
    ObservableList<DirectConnection> getDCInfoList() { return dcInfoList; }

    /**
     * Constructor - build up the MainModel object and load the accounts and transactions from database
     * @throws DaoException - from database operations
     */
    public MainModel() throws DaoException, ModelException {
        categoryList.setAll(((CategoryDao) daoManager.getDao(DaoManager.DaoType.CATEGORY)).getAll());
        tagList.setAll(((TagDao) daoManager.getDao(DaoManager.DaoType.TAG)).getAll());
        securityList.setAll(((SecurityDao) daoManager.getDao(DaoManager.DaoType.SECURITY)).getAll());
        accountList.setAll(((AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT)).getAll());
        transactionList.setAll(((TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION)).getAll());
        FXCollections.sort(transactionList, Comparator.comparing(Transaction::getID));

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
            account.getCurrentSecurityList().setAll(fromSecurityHoldingList(shList));
            account.setCurrentBalance(shList.get(shList.size()-1).getMarketValue());
        }

        // initialize AccountDCList
        accountDCList.setAll(((AccountDCDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT_DC)).getAll());

        // initialize the Direct connection vault
        initVault();
    }

    /**
     *
     * @param t - search key
     * @return index of object matches the key, or (-(insertion point)-1).
     */
    private int getTransactionIndex(Transaction t) {
        return Collections.binarySearch(transactionList, t, Comparator.comparing(Transaction::getID));
    }

    private int getTransactionIndexByID(int tid) {
        Transaction t = new Transaction(-1, LocalDate.MAX, BUY, 0);
        t.setID(tid);
        return getTransactionIndex(t);
    }

    private Optional<Transaction> getTransactionByID(int tid) {
        final int index = getTransactionIndexByID(tid);
        if (index >= 0)
            return Optional.of(transactionList.get(index));
        return Optional.empty();
    }

    public Optional<Transaction> getTransaction(Predicate<Transaction> predicate) {
        return transactionList.stream().filter(predicate).findFirst();
    }

    // why do we expose DB vs MEM to public?
    // that's because enterShareClassConversionTransaction need it.  We need to move that
    // inside mainModel
    void insertTransaction(Transaction transaction, InsertMode mode) throws DaoException {
        if (mode != InsertMode.MEM_ONLY) {
            final int tid = ((TransactionDao) DaoManager.getInstance().getDao(DaoManager.DaoType.TRANSACTION))
                    .insert(transaction);
            transaction.setID(tid);
        }
        if (mode != InsertMode.DB_ONLY) {
            final int index = getTransactionIndex(transaction);
            if (index < 0)
                transactionList.add(-(1+index), transaction);
            else {
                throw new IllegalStateException("Transaction list and database out of sync");
            }
        }
    }

    /**
     * find the first security matches the predicate
     * @param predicate - search criteria
     * @return Optional
     */
    public Optional<Security> getSecurity(Predicate<Security> predicate) {
        return securityList.stream().filter(predicate).findFirst();
    }

    /**
     * return the security list
     * @return - the security list
     */
    public ObservableList<Security> getSecurityList() {
        return securityList;
    }

    /**
     * extract security list from a security holding list
     * @param shList - a list of SecurityHolding objects
     * @return - a list of Security objects
     */
    private List<Security> fromSecurityHoldingList(List<SecurityHolding> shList) {
        List<Security> securityList = new ArrayList<>();
        for (SecurityHolding sh : shList) {
            String sName = sh.getSecurityName();
            if (sName.equals("TOTAL") || sName.equals("CASH"))
                continue; // skip total and cash lines

            getSecurity(security -> security.getName().equals(sName)).ifPresent(securityList::add);
        }
        return securityList;
    }

    private void initVault() throws ModelException, DaoException {
        try {
            vault.setupKeyStore();
        } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException
                | InvalidKeySpecException e) {
            throw new ModelException(ModelException.ErrorCode.FAIL_TO_SETUP_KEYSTORE, e.getClass().getName(), e);
        }

        String encodedHashedMasterPassword = initDCInfoList();
        if (encodedHashedMasterPassword != null) {
            vault.setHashedMasterPassword(encodedHashedMasterPassword);
            hasMasterPasswordProperty.set(true);
        }
    }

    /**
     * initialized direct connect information list
     * * @return encoded hashed master password or null if not set
     * @throws DaoException from database operations
     */
    private String initDCInfoList() throws DaoException {
        dcInfoList.setAll(((DirectConnectionDao) daoManager.getDao(DaoManager.DaoType.DIRECT_CONNECTION)).getAll());
        for (DirectConnection directConnection : dcInfoList) {
            if (directConnection.getName().equals(DirectConnection.HAS_MASTER_PASSWORD_NAME))
                return directConnection.getEncryptedPassword();
        }
        return null;
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
            account.getCurrentSecurityList().setAll(fromSecurityHoldingList(shList));
            account.setCurrentBalance(shList.get(shList.size() - 1).getMarketValue());
        }
    }

    /**
     *
     * @param pair input pair of security and date
     * @return the price of the security for the given date as an optional or an empty optional
     */
    Optional<Pair<Security, Price>> getSecurityPrice(Pair<Security, LocalDate> pair) throws DaoException {
        return ((SecurityPriceDao) DaoManager.getInstance().getDao(DaoManager.DaoType.SECURITY_PRICE)).get(pair);
    }

    void insertSecurityPrice(Pair<Security, Price> pair) throws DaoException {
        ((SecurityPriceDao) DaoManager.getInstance().getDao(DaoManager.DaoType.SECURITY_PRICE)).insert(pair);
    }
    /**
     * Given a csv file name, scan the file, import the lines of valid Symbol,Date,Price triplets
     * @param file - a file object of the csv file
     * @return - a Pair object of a list of accepted security prices and a list of rejected lines
     */
    public Pair<List<Pair<Security, Price>>, List<String[]>> importPrices(File file) throws IOException, DaoException {
        final List<Pair<Security, Price>> priceList = new ArrayList<>();
        final List<String[]> skippedLines = new ArrayList<>();

        final DaoManager daoManager = DaoManager.getInstance();
        final Map<String, Security> tickerSecurityMap = new HashMap<>();
        securityList.forEach(s -> tickerSecurityMap.put(s.getTicker(), s));

        List<String> datePatterns = Arrays.asList("yyyy/M/d", "M/d/yyyy", "M/d/yy");
        final CSVReader reader = new CSVReader(new FileReader(file));
        for (String[] line : reader.readAll()) {
            if (line[0].equals("Symbol")) {
                // skip the header line
                skippedLines.add(line);
                continue;
            }

            if (line.length < 3) {
                logger.warn("Bad formatted line: " + String.join(",", line));
                skippedLines.add(line);
                continue;
            }

            BigDecimal p = new BigDecimal(line[1]);
            if (p.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Negative Price: " + String.join(",", line));
                skippedLines.add(line);
                continue;
            }

            Security security = tickerSecurityMap.get(line[0]);
            if (security == null) {
                logger.warn("Unknown ticker: " + line[0]);
                skippedLines.add(line);
                continue;
            }

            boolean added = false;
            for (String pattern : datePatterns) {
                try {
                    LocalDate localDate = LocalDate.parse(line[2], DateTimeFormatter.ofPattern(pattern));
                    priceList.add(new Pair<>(security, new Price(localDate, p)));
                    added = true;
                    break;
                } catch (DateTimeParseException ignored) {
                }
            }
            if (!added) {
                logger.warn("Unable to parse date: " + String.join(",", line));
                skippedLines.add(line);
            }
        }

        // now ready to insert to database
        ((SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE)).mergePricesToDB(priceList);

        // the set of securities updated prices
        Set<Security> securitySet = new HashSet<>();
        priceList.forEach(p -> securitySet.add(p.getKey()));

        Predicate<Account> predicate = account -> {
            if (!account.getType().isGroup(Account.Type.Group.INVESTING))
                return false;
            for (Security security : account.getCurrentSecurityList()) {
                if (securitySet.contains(security)) {
                    return true;
                }
            }
            // nothing in account current security list is in security set.
            return false;
        };
        List<Account> accountList = getAccountList(predicate);

        for (Account account : accountList) {
            List<SecurityHolding> shList = computeSecurityHoldings(account.getTransactionList(), LocalDate.now(), -1);
            account.getCurrentSecurityList().setAll(fromSecurityHoldingList(shList));
            account.setCurrentBalance(shList.get(shList.size()-1).getMarketValue());
        }

        return new Pair<>(priceList, skippedLines);
    }

    /**
     * return an optional of AccountDC
     * @param accountID input account id
     * @return optional of AccountDC
     * @throws DaoException - from database operations
     */
    public Optional<AccountDC> getAccountDC(int accountID) throws DaoException {
        AccountDCDao accountDCDao = (AccountDCDao) DaoManager.getInstance().getDao(DaoManager.DaoType.ACCOUNT_DC);
        return accountDCDao.get(accountID);
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

    Optional<Account> getAccount(Predicate<Account> predicate) {
        return accountList.stream().filter(predicate).findFirst();
    }

    void insertReminderTransaction(ReminderTransaction rt) throws DaoException {
        ((ReminderTransactionDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REMINDER_TRANSACTION)).insert(rt);
    }

    /**
     * get
     * @return - the past reminder transactions and one next future reminder transaction for each reminder
     * @throws DaoException - database operations
     */
    ObservableList<ReminderTransaction> getReminderTransactionList() throws DaoException {
        // get the past reminder transactions first
        ObservableList<ReminderTransaction> reminderTransactions = FXCollections.observableArrayList();
        reminderTransactions.setAll(((ReminderTransactionDao) daoManager
                .getDao(DaoManager.DaoType.REMINDER_TRANSACTION)).getAll());

        List<Reminder> reminders = ((ReminderDao) daoManager.getDao(DaoManager.DaoType.REMINDER)).getAll();

        for (Reminder reminder : reminders) {
            FilteredList<ReminderTransaction> filteredList = new FilteredList<>(reminderTransactions,
                    rt -> rt.getReminder() != null && rt.getReminder().getID() == reminder.getID());
            SortedList<ReminderTransaction> sortedList = new SortedList<>(filteredList,
                    Comparator.comparing(ReminderTransaction::getDueDate).reversed());

            // let estimate the amount
            final int n = Math.min(reminder.getEstimateCount(), sortedList.size());
            BigDecimal amt = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                final int tid = sortedList.get(i).getTransactionID();
                amt = amt.add(getTransactionByID(tid).map(Transaction::getAmount).orElse(BigDecimal.ZERO));
            }
            if (n > 0) {
                amt = amt.divide(BigDecimal.valueOf(n), DaoManager.AMOUNT_FRACTION_LEN, RoundingMode.HALF_UP);
                reminder.setAmount(amt);
            }

            // calculate next due date
            final LocalDate lastDueDate = sortedList.isEmpty() ?
                    reminder.getDateSchedule().getStartDate() : sortedList.get(0).getDueDate().plusDays(1);
            final LocalDate nextDueDate = reminder.getDateSchedule().getNextDueDate(lastDueDate);
            reminderTransactions.add(new ReminderTransaction(reminder, nextDueDate, -1));
        }
        return reminderTransactions;
    }

    void deleteReminder(Reminder reminder) throws DaoException {
        ((ReminderDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REMINDER)).delete(reminder.getID());
    }

    void insertReminder(Reminder reminder) throws DaoException {
        ((ReminderDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REMINDER)).insert(reminder);
    }

    void updateReminder(Reminder reminder) throws DaoException {
        ((ReminderDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REMINDER)).update(reminder);
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
        SecurityPriceDao securityPriceDao = (SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE);
        List<SecurityHolding> shList = new ArrayList<>(shMap.values());
        shList.removeIf(sh -> sh.getQuantity().setScale(DaoManager.QUANTITY_FRACTION_DISPLAY_LEN,
                RoundingMode.HALF_UP).signum() == 0);
        for (SecurityHolding sh : shList) {
            Price price = null;
            BigDecimal p = BigDecimal.ZERO;
            Optional<Security> securityOptional = getSecurity(security ->
                    security.getName().equals(sh.getSecurityName()));
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
    List<SecurityHolding.MatchInfo> getMatchInfoList(int tid) throws DaoException {
        return ((PairTidMatchInfoListDao) DaoManager.getInstance().getDao(DaoManager.DaoType.PAIR_TID_MATCH_INFO))
                .get(tid).map(Pair::getValue).orElse(new ArrayList<>());
    }

    public ObjectProperty<Account> getCurrentAccountProperty() { return currentAccountProperty; }
    public void setCurrentAccount(Account account) { getCurrentAccountProperty().set(account); }
    public Account getCurrentAccount() { return getCurrentAccountProperty().get(); }

    public ObservableList<AccountDC> getAccountDCList() { return accountDCList; }

    public ObservableList<Category> getCategoryList() { return categoryList; }

    public Optional<Category> getCategory(Predicate<Category> predicate) {
        return categoryList.stream().filter(predicate).findFirst();
    }

    public void insertCategory(Category category) throws DaoException {
        int id = ((CategoryDao) DaoManager.getInstance().getDao(DaoManager.DaoType.CATEGORY)).insert(category);
        category.setID(id);
        categoryList.add(category);
    }

    public void updateCategory(Category category) throws DaoException {
        ((CategoryDao) DaoManager.getInstance().getDao(DaoManager.DaoType.CATEGORY)).update(category);
        getCategory(c -> c.getID() == category.getID()).ifPresent(c -> c.copy(category));
    }

    public ObservableList<Tag> getTagList() { return tagList; }

    public Optional<Tag> getTag(Predicate<Tag> predicate) { return tagList.stream().filter(predicate).findFirst(); }

    /**
     * insert tag to the database and the master tag list
     * @param tag - input
     * @throws DaoException database operation
     */
    public void insertTag(Tag tag) throws DaoException {
        int id = ((TagDao) DaoManager.getInstance().getDao(DaoManager.DaoType.TAG)).insert(tag);
        tag.setID(id);
        tagList.add(tag);
    }

    /**
     * update tag in the database and the master list
     * @param tag - input
     * @throws DaoException database operation
     */
    public void updateTag(Tag tag) throws DaoException {
        // update database
        ((TagDao) DaoManager.getInstance().getDao(DaoManager.DaoType.TAG)).update(tag);
        // update master list
        getTag(t -> t.getID() == tag.getID()).ifPresent(t -> t.copy(tag));
    }

    /**
     *
     * @return all payees in a sorted set with case insensitive ordering.
     */
    public SortedSet<String> getPayeeSet() {
        SortedSet<String> payeeSet = new TreeSet<>(Comparator.comparing(String::toLowerCase));

        for (Transaction transaction : transactionList) {
            final String payee = transaction.getPayee();
            if (payee != null && !payee.isEmpty())
                payeeSet.add(payee);
        }
        return payeeSet;
    }

    // Alter, including insert, delete, and modify a transaction, both in DB and in MasterList.
    // It performs all the necessary consistency checks.
    // If oldT is null, newT is inserted
    // If newT is null, oldT is deleted
    // If oldT and newT both are not null, oldT is modified to newT.
    // return true for success and false for failure
    // this is the new one
    void alterTransaction(Transaction oldT, Transaction newT, List<SecurityHolding.MatchInfo> newMatchInfoList)
            throws DaoException, ModelException {
        if (oldT == null && newT == null)
            return; // both null, no-op.

        final Set<Transaction> insertTSet = new HashSet<>(); // transactions inserted to DB
        final Set<Transaction> updateTSet = new HashSet<>(); // transactions updated in DB
        final Set<Integer> deleteTIDSet = new HashSet<>(); // IDs of transactions deleted in DB
        final Set<Integer> accountIDSet = new HashSet<>(); // IDs of accounts need to update balance

        DaoManager daoManager = DaoManager.getInstance();
        TransactionDao transactionDao = (TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION);
        PairTidMatchInfoListDao pairTidMatchInfoListDao =
                (PairTidMatchInfoListDao) daoManager.getDao(DaoManager.DaoType.PAIR_TID_MATCH_INFO);
        SecurityPriceDao securityPriceDao = (SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE);
        try {
            // start a dao transaction
            daoManager.beginTransaction();

            if (newT != null) {
                // we will be working on the copy instead of the original
                final Transaction newTCopy = new Transaction(newT);

                final Transaction.TradeAction newTTA = newTCopy.getTradeAction();

                // check quantity
                if (newTTA == SELL || newTTA == SHRSOUT || newTTA == CVTSHRT) {
                    // get account transaction list
                    final ObservableList<Transaction> transactions = getAccount(a -> a.getID() == newTCopy.getAccountID())
                            .map(Account::getTransactionList).orElseThrow(() ->
                            new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                    "Transaction '" + newTCopy.getID() + "' has an invalid account ID ("
                                            + newTCopy.getAccountID() + ")", null));
                    // compute security holdings
                    final BigDecimal quantity = computeSecurityHoldings(transactions, newTCopy.getTDate(),
                            newTCopy.getID())
                            .stream().filter(sh -> sh.getSecurityName().equals(newTCopy.getSecurityName()))
                            .map(SecurityHolding::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (((newTTA == SELL || newTTA == SHRSOUT) && quantity.compareTo(newTCopy.getQuantity()) < 0)
                            || ((newTTA == CVTSHRT) && quantity.negate().compareTo(newTCopy.getQuantity()) < 0)) {
                        // existing quantity not enough for the new trade
                        throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                "New " + newTTA + " transaction quantity exceeds existing quantity",
                                null);
                    }
                }

                // check ADate for SHRSIN
                if (newTTA == SHRSIN) {
                    final LocalDate aDate = newTCopy.getADate();
                    if (aDate == null || aDate.isAfter(newTCopy.getTDate())) {
                        throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                "Acquisition date '" + aDate + "' should be on or before trade date '"
                                        + newTCopy.getTDate() + "'", null);
                    }
                }

                // we need to save newT and newMatchInfoList to DB
                if (newTCopy.getID() > 0) {
                    transactionDao.update(newTCopy);
                    updateTSet.add(newTCopy);
                } else {
                    newTCopy.setID(transactionDao.insert(newTCopy));
                    insertTSet.add(newTCopy);
                }
                pairTidMatchInfoListDao.insert(new Pair<>(newTCopy.getID(), newMatchInfoList));

                if (newTCopy.isSplit()) {
                    // split transaction shouldn't have any match id and match split id.
                    newTCopy.setMatchID(-1, -1);
                    // check transfer in split transactions
                    for (SplitTransaction st : newTCopy.getSplitTransactionList()) {
                        if (st.isTransfer(newTCopy.getAccountID())) {
                            if (st.getCategoryID() == -newTCopy.getAccountID()) {
                                throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                        "Split transaction transferring back to the same account. "
                                        + "Self transferring is not permitted.", null);
                            }
                            // this split transaction is a transfer transaction
                            Transaction stXferT = null;
                            if (st.getMatchID() > 0) {
                                // it is modify exist transfer transaction
                                // stXferT is a copy of the original.
                                stXferT = getTransaction(t -> t.getID() == st.getMatchID())
                                        .map(Transaction::new).orElseThrow(() ->
                                                new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                                        "Split Transaction (" + newTCopy.getID() + ", " + st.getID()
                                                                + ") linked to null",
                                                        null));

                                if (!stXferT.isCash()) {
                                    // transfer transaction is an invest transaction, check trade action compatibility
                                    if (stXferT.TransferTradeAction() != (st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ?
                                            Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW)) {
                                        throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                                "Split transaction cash flow not compatible with "
                                                        + "transfer transaction trade action",
                                                null);
                                    }
                                    if (stXferT.getTradeAction() == SHRSIN) {
                                        // shares transferred in, need to check acquisition date
                                        final LocalDate aDate = stXferT.getADate();
                                        if (aDate == null || newTCopy.getTDate().isBefore(aDate)) {
                                            throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                                    "Invalid acquisition date for shares transferred in", null);
                                        }
                                    } else {
                                        stXferT.setADate(null);
                                    }
                                    // for existing transfer transactions, we only update the minimum information
                                    stXferT.setAccountID(-st.getCategoryID());
                                    stXferT.setCategoryID(-newTCopy.getAccountID());
                                    stXferT.setTDate(newTCopy.getTDate());
                                    stXferT.setAmount(st.getAmount().abs());
                                }
                            }

                            if (stXferT == null || stXferT.isCash()) {
                                // we need to create new xfer transaction
                                stXferT = new Transaction(-st.getCategoryID(), newTCopy.getTDate(),
                                        (st.getAmount().compareTo(BigDecimal.ZERO) >= 0 ? WITHDRAW : DEPOSIT),
                                        -newTCopy.getAccountID());
                                stXferT.setID(st.getMatchID());
                                stXferT.setPayee(newTCopy.getPayee());
                                stXferT.setMemo(st.getMemo());
                                stXferT.setMatchID(newTCopy.getID(), st.getID());
                                stXferT.setAmount(st.getAmount().abs());
                            }

                            // insert stXferT to database and update st match id
                            if (stXferT.getID() > 0) {
                                transactionDao.update(stXferT);
                                updateTSet.add(stXferT);
                            } else {
                                stXferT.setID(transactionDao.insert(stXferT));
                                insertTSet.add(stXferT);
                            }
                            st.setMatchID(stXferT.getID());

                            // we need to update the transfer account
                            accountIDSet.add(-st.getCategoryID());
                        } else {
                            // st is not a transfer, set match id to 0
                            st.setMatchID(0);
                        }
                    }

                    // match id and/or match split id in newT might have changed
                    // match id in split transactions might have changed
                    transactionDao.update(newTCopy);
                } else if (newTCopy.isTransfer()) {
                    if (newTCopy.getCategoryID() == -newTCopy.getAccountID()) {
                        throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                "Transaction is transferring back to the same account", null);
                    }

                    // non split, transfer
                    Transaction xferT = null;
                    if (newTCopy.getMatchID() > 0) {
                        // newT linked to a split transaction
                        // xferT is a copy of the original
                        xferT = getTransaction(t -> t.getID() == newTCopy.getMatchID())
                                .map(Transaction::new).orElseThrow(() ->
                                        new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                                "Transaction " + newTCopy.getID() + " is linked to null",
                                                null));
                        if (xferT.isSplit()) {
                            if (newTCopy.getMatchSplitID() <= 0) {
                                throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                        "Transaction (" + newTCopy.getMatchID() + ") missing match split id",
                                        null);
                            }
                            if (!newTCopy.getTDate().equals(xferT.getTDate())
                                    || (-newTCopy.getCategoryID() != xferT.getAccountID())) {
                                throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                        "Can't change date and/or transfer account to a transaction linked "
                                                + "to a split transaction.",
                                        null);
                            }
                            final List<SplitTransaction> stList = xferT.getSplitTransactionList();
                            final SplitTransaction xferSt = stList.stream()
                                    .filter(st -> st.getID() == newTCopy.getMatchSplitID())
                                    .findFirst()
                                    .orElseThrow(() -> new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                            "Transaction (" + newTCopy.getID() + ") linked to null split transaction",
                                            null));
                            xferSt.setMatchID(newTCopy.getID());
                            xferSt.setAmount(newTCopy.TransferTradeAction() == DEPOSIT ?
                                    newTCopy.getAmount() : newTCopy.getAmount().negate());
                            xferSt.getCategoryIDProperty().set(-newTCopy.getAccountID());
                            if (xferSt.getMemo().isEmpty())
                                xferSt.setMemo(newTCopy.getMemo());
                            final BigDecimal amount = xferT.getSplitTransactionList().stream()
                                    .map(SplitTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                                xferT.setTradeAction(DEPOSIT);
                                xferT.setAmount(amount);
                            } else {
                                xferT.setTradeAction(WITHDRAW);
                                xferT.setAmount(amount.negate());
                            }
                        } else if (!xferT.isCash()) {
                            // non cash, check trade action compatibility
                            if (xferT.TransferTradeAction() != newTCopy.getTradeAction()) {
                                throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                        "Transfer transaction has an investment trade action not "
                                                + "compatible with " + newTCopy.getTradeAction(),
                                        null);
                            }
                            if (xferT.getTradeAction() == SHRSIN) {
                                // shares transferred in, need to check acquisition date
                                final LocalDate aDate = xferT.getADate();
                                if (aDate == null || newTCopy.getTDate().isBefore(aDate)) {
                                    throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                            "Invalid acquisition date for shares transferred in",
                                            null);
                                }
                            } else {
                                xferT.setADate(null);
                            }
                            xferT.setAccountID(-newTCopy.getCategoryID());
                            xferT.setCategoryID(-newTCopy.getAccountID());
                            xferT.setTDate(newTCopy.getTDate());
                            xferT.setAmount(newTCopy.getAmount());
                        }
                    }

                    if ((xferT == null) || (!xferT.isSplit() && xferT.isCash())) {
                        xferT = new Transaction(-newTCopy.getCategoryID(), newTCopy.getTDate(),
                                newTCopy.TransferTradeAction(), -newTCopy.getAccountID());
                        xferT.setID(newTCopy.getMatchID());
                        xferT.setPayee(newTCopy.getPayee());
                        xferT.setMemo(newTCopy.getMemo());
                        xferT.setAmount(newTCopy.getAmount());
                    }

                    // setMatchID for xferT
                    if (!xferT.isSplit())
                        xferT.setMatchID(newTCopy.getID(), -1);

                    // we might need to set matchID if xferT is newly created
                    // but we never create a new match split transaction
                    // so we keep the matchSplitID the same as before
                    if (xferT.getID() > 0) {
                        transactionDao.update(xferT);
                        updateTSet.add(xferT);
                    } else {
                        xferT.setID(transactionDao.insert(xferT));
                        insertTSet.add(xferT);
                    }
                    newTCopy.setMatchID(xferT.getID(), newTCopy.getMatchSplitID());

                    // update newT MatchID in DB
                    transactionDao.update(newTCopy);

                    // we need to update xfer account
                    accountIDSet.add(-newTCopy.getCategoryID());
                } else {
                    // non-split, non transfer, make sure set match id properly
                    newTCopy.setMatchID(-1, -1);
                    transactionDao.update(newTCopy);
                }

                final Security security;
                final BigDecimal price;
                if (!newTCopy.isCash()) {
                    security = getSecurity(s -> s.getName().equals(newTCopy.getSecurityName())).orElse(null);
                    price = security == null ? null : newTCopy.getPrice();
                } else {
                    security = null;
                    price = null;
                }

                if (security != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                    // insert price if not currently present
                    if (securityPriceDao.get(new Pair<>(security, newTCopy.getTDate())).isEmpty()) {
                        securityPriceDao.insert(new Pair<>(security, new Price(newTCopy.getTDate(), price)));
                    }
                    getAccountList(Account.Type.Group.INVESTING, false, true).stream()
                            .filter(a -> a.hasSecurity(security)).forEach(a -> accountIDSet.add(a.getID()));
                }

                accountIDSet.add(newTCopy.getAccountID());
            }

            if (oldT != null) {
                // we have an oldT, need to delete the related transactions, if those are not updated
                // we never modify oldT, so no need to create a copy

                // first make sure it is not covered by SELL or CVTSHRT
                if (pairTidMatchInfoListDao.get(oldT.getID()).isPresent()) {
                    throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                            "Cannot delete transaction (" + oldT.getID() + ") which is lot matched",
                            null);
                }

                if (oldT.isSplit()) {
                    // this is a split transaction
                    for (SplitTransaction st : oldT.getSplitTransactionList()) {
                        if (st.isTransfer(oldT.getID())) {
                            // check if the transfer transaction is updated
                            if (updateTSet.stream().noneMatch(t -> t.getID() == st.getMatchID())) {
                                // not being updated
                                transactionDao.delete(st.getMatchID());
                                deleteTIDSet.add(st.getMatchID());
                            }

                            // need to update the account later
                            accountIDSet.add(-st.getCategoryID());
                        }
                    }
                } else if (oldT.isTransfer()) {
                    // oldT is a transfer.  Check if the xferT is updated
                    if (updateTSet.stream().noneMatch(t -> t.getID() == oldT.getMatchID())) {
                        // linked transaction is not being updated
                        if (oldT.getMatchSplitID() > 0) {
                            // oldT is a transfer from a split transaction,
                            // we will delete the corresponding split, adjust amount of
                            // the main transferring transaction, and update it
                            final Transaction xferT = getTransaction(t -> t.getID() == oldT.getMatchID())
                                    .orElseThrow(() -> new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                            "Transaction (" + oldT.getID() + ") linked to null transaction",
                                            null));

                            // delete the split transaction matching split id.
                            final List<SplitTransaction> stList = xferT.getSplitTransactionList().stream()
                                    .filter(st -> st.getID() != oldT.getMatchSplitID()).collect(Collectors.toList());
                            // check consistency of resulting amount with xferT trade action
                            final BigDecimal amount = stList.stream().map(SplitTransaction::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            if (((xferT.TransferTradeAction() == WITHDRAW)
                                    && (amount.compareTo(BigDecimal.ZERO) > 0))
                                    || ((xferT.TransferTradeAction() == DEPOSIT)
                                    && (amount.compareTo(BigDecimal.ZERO) < 0))) {
                                throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                        "Modify/delete transaction linked to a split transaction, "
                                                + "resulting inconsistency in linked transaction",
                                        null);
                            }
                            xferT.setAmount(amount.abs());
                            if (stList.size() == 1) {
                                // only one split transaction in the list, no need to split
                                // make it a simple transfer
                                final SplitTransaction st = stList.get(0);
                                stList.clear();

                                xferT.getCategoryIDProperty().set(st.getCategoryID());
                                if (xferT.getMemo().isEmpty())
                                    xferT.setMemo(st.getMemo());
                                xferT.setMatchID(st.getMatchID(), -1);

                                if (st.getMatchID() > 0) {
                                    final Transaction xferXferT = transactionDao.get(st.getMatchID())
                                            .orElseThrow(() ->
                                                    new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                                                            "(" + oldT.getID() + ", " + st.getID()
                                                                    + ") is linked to missing transaction "
                                                                    + st.getMatchID(),
                                                            null));

                                    // xferXferT was linked to (xferT, st), now is only linked to xferT
                                    xferXferT.setMatchID(xferXferT.getMatchID(), -1);
                                    transactionDao.update(xferXferT);

                                    updateTSet.add(xferXferT);
                                    accountIDSet.add(xferXferT.getAccountID());
                                }
                            }
                            xferT.setSplitTransactionList(stList);
                            transactionDao.update(xferT);

                            updateTSet.add(xferT);
                        } else {
                            // oldT linked to non-split transaction
                            transactionDao.delete(oldT.getMatchID());
                            deleteTIDSet.add(oldT.getMatchID());
                        }

                        accountIDSet.add(-oldT.getCategoryID());
                    }
                }

                // now ready to delete oldT
                accountIDSet.add(oldT.getAccountID());
                if (updateTSet.stream().noneMatch(t -> t.getID() == oldT.getID())) {
                    transactionDao.delete(oldT.getID());
                    deleteTIDSet.add(oldT.getID());
                }

                // need to update account
                accountIDSet.add(oldT.getAccountID());
            }

            // commit to database
            daoManager.commit();

            // done with database work, update MasterList now

            // delete these transactions from master list
            for (int tid : deleteTIDSet) {
                final int index = getTransactionIndexByID(tid);
                if (index < 0) {
                    throw new IllegalStateException("Transaction " + tid + " should be, but not found in master list");
                }
                transactionList.remove(index);
            }

            // update these
            for (Transaction t : updateTSet) {
                final int index = getTransactionIndex(t);
                if (index < 0) {
                    throw new IllegalStateException("Transaction " + t + " should be, but not found in master list");
                }
                transactionList.set(index, t);
            }

            // insert these
            for (Transaction t : insertTSet) {
                final int index = getTransactionIndex(t);
                if (index >= 0) {
                    throw new IllegalStateException("Transaction " + t + " should not be, but found in master list");
                }
                transactionList.add(-(1+index), t);
            }

            // update account balances
            updateAccountBalance(account -> accountIDSet.contains(account.getID()));

            // we are done
        } catch (DaoException | ModelException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }
}
