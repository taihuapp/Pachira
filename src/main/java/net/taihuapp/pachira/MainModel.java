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
import com.webcohesion.ofx4j.OFXException;
import com.webcohesion.ofx4j.client.AccountStatement;
import com.webcohesion.ofx4j.client.FinancialInstitution;
import com.webcohesion.ofx4j.client.FinancialInstitutionAccount;
import com.webcohesion.ofx4j.client.impl.BaseFinancialInstitutionData;
import com.webcohesion.ofx4j.client.impl.FinancialInstitutionImpl;
import com.webcohesion.ofx4j.client.net.OFXV1Connection;
import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponse;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardAccountDetails;
import com.webcohesion.ofx4j.domain.data.investment.accounts.InvestmentAccountDetails;
import com.webcohesion.ofx4j.domain.data.signup.AccountProfile;
import com.webcohesion.ofx4j.io.OFXParseException;
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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.taihuapp.pachira.QIFUtil.EOL;
import static net.taihuapp.pachira.Transaction.TradeAction.*;

public class MainModel {

    public static final String DELETED_ACCOUNT_NAME = "Deleted Account";
    public static final int SAVEDREPORTS_NAME_LEN = 32;
    static final int PRICE_FRACTION_DISPLAY_LEN = 6;
    // minimum 2 decimal places, maximum 4 decimal places
    static final DecimalFormat DOLLAR_CENT_FORMAT = new DecimalFormat("###,##0.00##");
    static final int QUANTITY_FRACTION_DISPLAY_LEN = 6;

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
    private final ObservableList<DirectConnection.FIData> fiDataList = FXCollections.observableArrayList();
    ObservableList<DirectConnection> getDCInfoList() { return dcInfoList; }

    /**
     * Constructor - build up the MainModel object and load the accounts and transactions from database
     * @throws DaoException - from database operations
     */
    public MainModel() throws DaoException, ModelException {
        categoryList.setAll(((CategoryDao) daoManager.getDao(DaoManager.DaoType.CATEGORY)).getAll());
        tagList.setAll(((TagDao) daoManager.getDao(DaoManager.DaoType.TAG)).getAll());

        initSecurityList();

        initTransactionList();

        initAccountList();

        // initialize AccountDCList
        accountDCList.setAll(((AccountDCDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT_DC)).getAll());
        fiDataList.setAll(((FIDataDao) daoManager.getDao(DaoManager.DaoType.FIDATA)).getAll());

        // initialize the Direct connection vault
        initVault();
    }

    Optional<UUID> getClientUID() throws DaoException {
        return daoManager.getClientUID();
    }

    void putClientUID(UUID uuid) throws DaoException {
        daoManager.putClientUID(uuid);
    }

    String getDBFileName() throws DaoException { return daoManager.getDBFileName(); }

    void insertUpdateReportSetting(ReportDialogController.Setting setting) throws DaoException {
        ReportSettingDao reportSettingDao =
                (ReportSettingDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REPORT_SETTING);
        if (setting.getID() <= 0)
            setting.setID(reportSettingDao.insert(setting));
        else
            reportSettingDao.update(setting);
    }

    List<ReportDialogController.Setting> getReportSettingList() throws DaoException {
        return ((ReportSettingDao) DaoManager.getInstance().getDao(DaoManager.DaoType.REPORT_SETTING)).getAll();
    }

    private void initSecurityList() throws DaoException {
        securityList.setAll(((SecurityDao) daoManager.getDao(DaoManager.DaoType.SECURITY)).getAll());
    }

    private void initTransactionList() throws DaoException {
        transactionList.setAll(((TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION)).getAll());
        FXCollections.sort(transactionList, Comparator.comparing(Transaction::getID));
    }

    private void initAccountList() throws DaoException {
        AccountDao accountDao = (AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT);
        accountList.setAll(((AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT)).getAll());
        // make sure all account display orders are correct

        FXCollections.sort(accountList, Comparator.comparing(Account::getType).thenComparing(Account::getDisplayOrder));
        int currentDisplayOrder = 0;
        Account.Type currentType = null;
        for (Account a : accountList) {
            if (a.getName().equals(DELETED_ACCOUNT_NAME))
                continue;
            if (!a.getType().equals(currentType)) {
                currentDisplayOrder = 0;
                currentType = a.getType();
            }
            if (a.getDisplayOrder() != currentDisplayOrder) {
                a.setDisplayOrder(currentDisplayOrder);
                accountDao.update(a);
            }
            currentDisplayOrder++;
        }

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
        return transactionList.stream().filter(predicate).findAny();
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
        return securityList.stream().filter(predicate).findAny();
    }

    /**
     * insert or update security in database and master list
     * @param security - the input security
     * @throws DaoException - from database operation
     */
    void mergeSecurity(Security security) throws DaoException {
        SecurityDao securityDao = (SecurityDao) DaoManager.getInstance().getDao(DaoManager.DaoType.SECURITY);
        if (security.getID() <= 0) {
            securityDao.insert(security);
            getSecurityList().add(security);
        } else {
            securityDao.update(security);
            for (int i = 0; i < getSecurityList().size(); i++) {
                Security s = getSecurityList().get(i);
                if (s.getID() == security.getID()) {
                    getSecurityList().set(i, security);
                }
            }
        }

        // update security name in transaction list
        initTransactionList();

        // update security holdings of accounts
        initAccountList();
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

    private Vault getVault() { return vault; }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasMasterPasswordInKeyStore() { return getVault().hasMasterPasswordInKeyStore(); }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean verifyMasterPassword(final String password) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException {
        char[] mpChars = password.toCharArray();
        try {
            return getVault().verifyMasterPassword(mpChars);
        } finally {
            Arrays.fill(mpChars, ' ');
        }
    }

    // set master password in Vault
    // save hashed and encoded master password to database
    void setMasterPassword(final String password) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException, DaoException {
        char[] mpChars = password.toCharArray();
        try {
            getVault().setMasterPassword(mpChars);
            mergeMasterPassword(getVault().getEncodedHashedMasterPassword());
        } finally {
            Arrays.fill(mpChars, ' ');
            hasMasterPasswordProperty.set(getVault().hasMasterPassword());
        }
    }

    // save hashed and encoded master password into database.
    private void mergeMasterPassword(String encodedHashedMasterPassword) throws DaoException {
        ((DirectConnectionDao) daoManager.getDao(DaoManager.DaoType.DIRECT_CONNECTION))
                .merge(new DirectConnection(-1, DirectConnection.HASHED_MASTER_PASSWORD_NAME,
                        0, null, encodedHashedMasterPassword));
    }

    // delete master password in vault
    // empty DCInfo table and AccountDCS table
    // empty DCInfoList
    void deleteMasterPassword() throws KeyStoreException, DaoException {
        DirectConnectionDao directConnectionDao =
                (DirectConnectionDao) daoManager.getDao(DaoManager.DaoType.DIRECT_CONNECTION);
        AccountDCDao accountDCDao = (AccountDCDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT_DC);
        daoManager.beginTransaction();
        try {
            // handle h2 database first, we can roll back if something went wrong
            directConnectionDao.deleteAll();
            accountDCDao.deleteAll();

            // delete master password in the vault, this cannot be undo
            getVault().deleteMasterPassword();

            daoManager.commit();

            getAccountDCList().clear();
            getDCInfoList().clear();
            hasMasterPasswordProperty.set(getVault().hasMasterPassword());
        } catch (DaoException | KeyStoreException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    // this method setDBSavepoint.  If the save point is set by the caller, an SQLException will be thrown
    // return false when isUpdate is true and curPassword doesn't match existing master password
    // exception will be thrown if any is encountered
    // otherwise, return true
    boolean updateMasterPassword(final String curPassword, final String newPassword)
            throws NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException,
            UnrecoverableKeyException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, DaoException, ModelException {

        if (!verifyMasterPassword(curPassword))
            return false;  // in update mode, curPassword doesn't match existing master password

        final char[] newPasswordChars = newPassword.toCharArray();

        final List<char[]> clearUserNameList = new ArrayList<>();
        final List<char[]> clearPasswordList = new ArrayList<>();
        final List<char[]> clearAccountNumberList = new ArrayList<>();

        try {
            for (DirectConnection dc : getDCInfoList()) {
                clearUserNameList.add(decrypt(dc.getEncryptedUserName()));
                clearPasswordList.add(decrypt(dc.getEncryptedPassword()));
            }
            for (AccountDC adc : getAccountDCList())
                clearAccountNumberList.add(decrypt(adc.getEncryptedAccountNumber()));

            getVault().setMasterPassword(newPasswordChars);

            for (int i = 0; i < getDCInfoList().size(); i++) {
                DirectConnection dc = getDCInfoList().get(i);
                dc.setEncryptedUserName(encrypt(clearUserNameList.get(i)));
                dc.setEncryptedPassword(encrypt(clearPasswordList.get(i)));
            }
            for (int i = 0; i < getAccountDCList().size(); i++) {
                AccountDC adc = getAccountDCList().get(i);
                adc.setEncryptedAccountNumber(encrypt(clearAccountNumberList.get(i)));
            }
        } finally {
            // wipe it clean
            Arrays.fill(newPasswordChars, ' ');

            for (char[] chars : clearUserNameList)
                Arrays.fill(chars, ' ');
            for (char[] chars : clearPasswordList)
                Arrays.fill(chars, ' ');
            for (char[] chars : clearAccountNumberList)
                Arrays.fill(chars, ' ');
        }

        daoManager.beginTransaction();
        try {
            mergeMasterPassword(getVault().getEncodedHashedMasterPassword());
            for (DirectConnection directConnection : getDCInfoList())
                mergeDCInfo(directConnection);
            for (AccountDC accountDC : getAccountDCList())
                mergeAccountDC(accountDC);
            daoManager.commit();
            return true;
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            initVault(); // reload vault
            throw e;
        }
    }

    /**
     * initialized direct connect information list, exclude HASHED_MASTER_PASSWORD_NAME from dcInfoList
     * @return encoded hashed master password or null if not set
     * @throws DaoException from database operations
     */
    String initDCInfoList() throws DaoException {
        dcInfoList.setAll(((DirectConnectionDao) daoManager.getDao(DaoManager.DaoType.DIRECT_CONNECTION)).getAll());
        dcInfoList.sort(Comparator.comparing(DirectConnection::getID));
        Iterator<DirectConnection> iterator = dcInfoList.iterator();
        while (iterator.hasNext()) {
            DirectConnection directConnection = iterator.next();
            if (directConnection.getName().equals(DirectConnection.HASHED_MASTER_PASSWORD_NAME)) {
                iterator.remove();
                return directConnection.getEncryptedPassword();
            }
        }
        return null;
    }

    void mergeDCInfo(DirectConnection dcInfo) throws DaoException {
        DirectConnectionDao directConnectionDao =
                (DirectConnectionDao) daoManager.getDao(DaoManager.DaoType.DIRECT_CONNECTION);
        if (dcInfo.getID() > 0) {
            directConnectionDao.update(dcInfo);
            for (int i = 0; i < getDCInfoList().size(); i++) {
                if (getDCInfoList().get(i).getID() == dcInfo.getID()) {
                    getDCInfoList().set(i, dcInfo);
                    break;
                }
            }
        } else {
            directConnectionDao.insert(dcInfo);
            getDCInfoList().add(dcInfo);
        }
    }

    public Optional<DirectConnection> getDCInfo(Predicate<DirectConnection> predicate) {
        return getDCInfoList().filtered(predicate).stream().findAny();
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

    List<Price> getSecurityPriceList(Security security) throws DaoException {
        return ((SecurityPriceDao) DaoManager.getInstance().getDao(DaoManager.DaoType.SECURITY_PRICE)).get(security);
    }

    void insertSecurityPrice(Pair<Security, Price> pair) throws DaoException {
        ((SecurityPriceDao) DaoManager.getInstance().getDao(DaoManager.DaoType.SECURITY_PRICE)).insert(pair);
    }

    /**
     * insert or update a list of security and price pairs.
     * @param priceList - the input list
     * @throws DaoException - from database operation
     */
    void mergeSecurityPrices(List<Pair<Security, Price>> priceList) throws DaoException {
        ((SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE)).mergePricesToDB(priceList);
    }

    void deleteSecurityPrice(Security security, LocalDate date) throws DaoException {
        ((SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE)).delete(new Pair<>(security, date));
    }

    /**
     * Given a csv file name, scan the file, import the lines of valid Symbol,Date,Price triplets
     * @param file - a file object of the csv file
     * @return - a Pair object of a list of accepted security prices and a list of rejected lines
     */
    public Pair<List<Pair<Security, Price>>, List<String[]>> importPrices(File file) throws IOException, DaoException {
        final List<Pair<Security, Price>> priceList = new ArrayList<>();
        final List<String[]> skippedLines = new ArrayList<>();

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
        mergeSecurityPrices(priceList);

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
        return getAccountDCList().filtered(accountDC -> accountDC.getAccountID() == accountID).stream().findAny();
    }

    public void deleteAccountDC(int accountID) throws DaoException {
        ((AccountDCDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT_DC)).delete(accountID);
        Iterator<AccountDC> iterator = getAccountDCList().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getAccountID() == accountID) {
                iterator.remove();
                break;
            }
        }
    }

    public void mergeAccountDC(AccountDC accountDC) throws DaoException {
        ((AccountDCDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT_DC)).merge(accountDC);
        for (int i = 0; i < getAccountDCList().size(); i++) {
            AccountDC adc = getAccountDCList().get(i);
            if (adc.getAccountID() == accountDC.getAccountID()) {
                getAccountDCList().set(i, accountDC);
                return;
            }
        }
        getAccountDCList().add(accountDC);
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
                && !(exDeleted && a.getName().equals(DELETED_ACCOUNT_NAME));
        Comparator<Account> c = Comparator.comparing(Account::getType)
                .thenComparing(Account::getDisplayOrder).thenComparing(Account::getID);
        return getAccountList(p, c);
    }

    Optional<Account> getAccount(Predicate<Account> predicate) {
        return accountList.stream().filter(predicate).findAny();
    }

    ObservableList<DirectConnection.FIData> getFIDataList() { return fiDataList; }

    Optional<DirectConnection.FIData> getFIData(Predicate<DirectConnection.FIData> predicate) {
        return fiDataList.stream().filter(predicate).findAny();
    }

    void deleteFIData(int fiDataID) throws DaoException {
        ((FIDataDao) daoManager.getDao(DaoManager.DaoType.FIDATA)).delete(fiDataID);
        Iterator<DirectConnection.FIData> iterator = fiDataList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getID() == fiDataID) {
                iterator.remove();
                break;
            }
        }
    }

    /**
     * save fiData to database and master list
     * @param fiData - input
     * @throws DaoException - from database operation
     */
    void insertUpdateFIData(DirectConnection.FIData fiData) throws DaoException {
        FIDataDao fiDataDao = (FIDataDao) daoManager.getDao(DaoManager.DaoType.FIDATA);
        if (fiData.getID() > 0) {
            fiDataDao.update(fiData);
            for (int i = 0; i < fiDataList.size(); i++)
                if (fiDataList.get(i).getID() == fiData.getID()) {
                    fiDataList.set(i, fiData);
                    break;
                }
        } else {
            fiData.setID(fiDataDao.insert(fiData));
            fiDataList.add(fiData);
        }
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
     * insert or update account to database and master list
     * @param account - the account to be inserted or updated
     * @throws DaoException - from database operations
     */
    void insertUpdateAccount(Account account) throws DaoException {
        AccountDao accountDao = (AccountDao) DaoManager.getInstance().getDao(DaoManager.DaoType.ACCOUNT);
        final boolean isInsert = account.getID() <= 0;
        if (isInsert) {
            account.setDisplayOrder(getAccountList(a -> a.getType().equals(account.getType())).size());
            account.setID(accountDao.insert(account));
        } else
            accountDao.update(account);

        if (isInsert)
            accountList.add(account);
        else {
            for (int i = 0; i < accountList.size(); i++) {
                final Account a = accountList.get(i);
                if (a.getID() == account.getID()) {
                    if (a != account)
                        accountList.set(i, account);
                    break;
                }
            }
        }
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

            if ((t.getTDate().equals(date) && t.getID() == exTid) || t.getTDate().isAfter(date))
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
        return categoryList.stream().filter(predicate).findAny();
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

    public Optional<Tag> getTag(Predicate<Tag> predicate) { return tagList.stream().filter(predicate).findAny(); }

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

    void setTransactionStatus(int tid, Transaction.Status newStatus) throws DaoException, ModelException {
        Transaction t = getTransactionByID(tid).orElseThrow(() ->
                new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION, "Bad Transaction ID" + tid, null));
        Transaction.Status oldStatus = t.getStatus();
        t.setStatus(newStatus);
        try {
            ((TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION)).update(t);
        } catch (DaoException e) {
            t.setStatus(oldStatus);
            throw e;
        }
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
                                    .findAny()
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

    ObservableList<Transaction> getMergeCandidateTransactionList(final Transaction transaction) {
        final Optional<Account> accountOptional = getAccount(a -> a.getID() == transaction.getAccountID());
        if (accountOptional.isEmpty())
            throw new IllegalArgumentException("Transaction (" + transaction.getID() + ") has an invalid account ID ("
                    + transaction.getAccountID() + ")");
        final Account account = accountOptional.get();
        if (!account.getType().isGroup(Account.Type.Group.SPENDING)) {
            throw new IllegalArgumentException("Account type " + account.getType().toString()
                    + " is not supported yet");
        }
        // find transactions of the same account, within 2 weeks before or after,
        // not reconciled, not downloaded, ant match amount
        final BigDecimal netAmount = transaction.getDeposit().subtract(transaction.getPayment());
        final Predicate<Transaction> predicate = t ->
                t.getTDate().isAfter(transaction.getTDate().minusWeeks(2))
                        && t.getTDate().isBefore(transaction.getTDate().plusWeeks(2))
                        && !t.getStatus().equals(Transaction.Status.RECONCILED)
                        && t.getFITID().isEmpty()
                        && t.getDeposit().subtract(t.getPayment()).compareTo(netAmount) == 0;
        return new FilteredList<>(account.getTransactionList(), predicate);
    }

    /**
     * mark all cleared transaction for the given account as reconciled
     * and update account reconciled date
     * @param account the account to be reconciled
     * @param d - the date
     */
    void reconcileAccount(Account account, LocalDate d) throws DaoException {
        // create a local list of relevant transactions
        final List<Transaction> tList = new ArrayList<>(account.getTransactionList()
                .filtered(t -> t.getStatus().equals(Transaction.Status.CLEARED)));
        LocalDate oldReconcileDate = account.getLastReconcileDate();

        tList.forEach(t -> t.setStatus(Transaction.Status.RECONCILED));
        account.setLastReconcileDate(d);

        DaoManager daoManager = DaoManager.getInstance();
        TransactionDao transactionDao = (TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION);
        AccountDao accountDao = (AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT);
        try {
            daoManager.beginTransaction();
            for (Transaction t : tList) {
                transactionDao.update(t);
            }
            accountDao.update(account);
            daoManager.commit();
        } catch (DaoException e) {
            // something went wrong

            // put back the old reconcile date
            account.setLastReconcileDate(oldReconcileDate);
            tList.forEach(t -> t.setStatus(Transaction.Status.CLEARED));

            // roll back database
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }

            throw e;
        }
    }

    /**
     * find all transactions contains searchString in the following fields:
     *   category name, transfer account name, tag name, payee, security name, memo, trade action
     * @param searchString - the string to be searched
     * @return - the list of transactions contains the search string
     */
    ObservableList<Transaction> getStringSearchTransactionList(final String searchString) {
        final String lowerSearchString = searchString.toLowerCase();

        // category name and transfer account name
        Set<Integer> categoryOrTransferAccountNameMatchIDSet = getCategoryList()
                .filtered(c -> c.getName().toLowerCase().contains(lowerSearchString))
                .stream().map(Category::getID).collect(Collectors.toCollection(HashSet<Integer>::new));
        getAccountList(a -> a.getName().toLowerCase().contains(lowerSearchString))
                        .stream().map(a -> -a.getID())
                .forEach(categoryOrTransferAccountNameMatchIDSet::add);

        // Tag name
        Set<Integer> tagNameMatchIDSet = getTagList()
                .filtered(t -> t.getName().toLowerCase().contains(lowerSearchString))
                .stream().map(Tag::getID).collect(Collectors.toCollection(HashSet<Integer>::new));

        Predicate<Transaction> predicate = t -> {
            if (categoryOrTransferAccountNameMatchIDSet.contains(t.getCategoryID()))
                return true;

            if (tagNameMatchIDSet.contains(t.getTagID()))
                return true;

            final String payee = t.getPayee();
            if (payee != null && payee.toLowerCase().contains(lowerSearchString))
                return true;

            final String securityName = t.getSecurityName();
            if (securityName != null && securityName.toLowerCase().contains(lowerSearchString))
                return true;

            final String memo = t.getMemo();
            if (memo != null && memo.toLowerCase().contains(lowerSearchString))
                return true;

            return t.getTradeAction().toString().toLowerCase().contains(lowerSearchString);
        };

        return new FilteredList<>(transactionList, predicate);
    }

    /**
     * swap account display order in db and in memory
     * @param a1 one of the two accounts to be swapped
     * @param a2 one of the two account to be swapped
     */
    void swapAccountDisplayOrder(Account a1, Account a2) throws DaoException {
        if (a1 == null || a2 == null || a1.getType() != a2.getType())
            throw new IllegalArgumentException("swapAccountDisplayOrder takes two account of same group");

        final int o1 = a1.getDisplayOrder();
        final int o2 = a2.getDisplayOrder();

        a1.setDisplayOrder(o2);
        a2.setDisplayOrder(o1);

        DaoManager daoManager = DaoManager.getInstance();
        AccountDao accountDao = (AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT);

        try {
            daoManager.beginTransaction();
            accountDao.update(a1);
            accountDao.update(a2);
            daoManager.commit();

            int count = 0;
            for (int i = 0; i < accountList.size(); i++) {
                Account account = accountList.get(i);
                if (account.getID() == a1.getID()) {
                    if (account != a1)
                        accountList.set(i, a1);
                    count++;
                }
                if (account.getID() == a2.getID()) {
                    if (account != a2)
                        accountList.set(i, a2);
                    count++;
                }
                if (count > 1)
                    break;
            }
        } catch (DaoException e) {
            try {
                // put back the original display orders
                a1.setDisplayOrder(o1);
                a2.setDisplayOrder(o2);
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    // take a Transaction input (with SELL or CVTSHRT), compute the realize gain
    BigDecimal calcRealizedGain(Transaction transaction) throws DaoException, ModelException {
        BigDecimal realizedGain = BigDecimal.ZERO;
        for (CapitalGainItem cgi : getCapitalGainItemList(transaction)) {
            realizedGain = realizedGain.add(cgi.getProceeds()).subtract(cgi.getCostBasis());
        }
        return realizedGain;
    }

    List<CapitalGainItem> getCapitalGainItemList(Transaction transaction) throws DaoException, ModelException {
        Transaction.TradeAction ta = transaction.getTradeAction();
        if (!ta.equals(SELL) && ta.equals(Transaction.TradeAction.CVTSHRT))
            return null;

        Account account = getAccount(a -> a.getID() == transaction.getAccountID())
                .orElseThrow(() -> new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                       "Transaction " + transaction.getID()
                        + " has an invalid account id " + transaction.getAccountID(), null));
        List<SecurityHolding> securityHoldingList = computeSecurityHoldings(account.getTransactionList(),
                transaction.getTDate(), transaction.getID());
        List<SecurityHolding.MatchInfo> miList = getMatchInfoList(transaction.getID());
        List<CapitalGainItem> capitalGainItemList = new ArrayList<>();
        for (SecurityHolding securityHolding : securityHoldingList) {
            if (!securityHolding.getSecurityName().equals(transaction.getSecurityName()))
                continue;  // different security, skip

            // we have the right security holding here now
            BigDecimal remainCash = transaction.getAmount();
            BigDecimal remainQuantity = transaction.getQuantity();
            FilteredList<SecurityHolding.LotInfo> lotInfoList = new FilteredList<>(securityHolding.getLotInfoList());
            Map<Integer, SecurityHolding.MatchInfo> matchMap = new HashMap<>();
            if (!miList.isEmpty()) {
                // we have a matchInfo list,
                for (SecurityHolding.MatchInfo mi : miList)
                    matchMap.put(mi.getMatchTransactionID(), mi);
                lotInfoList.setPredicate(li -> matchMap.containsKey(li.getTransactionID()));
            }
            //for (SecurityHolding.LotInfo li : securityHolding.getLotInfoList()) {
            for (SecurityHolding.LotInfo li : lotInfoList) {
                BigDecimal costBasis;
                BigDecimal proceeds;
                Transaction matchTransaction;

                matchTransaction = getTransactionByID(li.getTransactionID()).orElseThrow(() ->
                        new ModelException(ModelException.ErrorCode.INVALID_LOT_INFO,
                                "LotInfo " + li + " has an invalid transaction id (" + li.getTransactionID() + ")",
                                null));
                SecurityHolding.MatchInfo mi = matchMap.get(li.getTransactionID());
                BigDecimal liMatchQuantity = (mi == null) ?
                        li.getQuantity().min(remainQuantity) : mi.getMatchQuantity();

                costBasis = li.getCostBasis().multiply(liMatchQuantity).divide(li.getQuantity(), RoundingMode.HALF_UP);
                proceeds = remainCash.multiply(liMatchQuantity).divide(remainQuantity, RoundingMode.HALF_UP);
                remainCash = remainCash.subtract(proceeds);
                remainQuantity = remainQuantity.subtract(liMatchQuantity);
                if (ta.equals(SELL))
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, liMatchQuantity,
                            costBasis, proceeds));
                else
                    capitalGainItemList.add(new CapitalGainItem(transaction, matchTransaction, liMatchQuantity,
                            proceeds, costBasis));

                if (remainQuantity.compareTo(BigDecimal.ZERO) == 0)
                    break; // done
            }
        }

        return capitalGainItemList;
    }

    // todo
    // These few methods should belong to Direct Connection, but I need to put a FIData object instead of
    // a FIID in DirectConnection
    // Later.
    private FinancialInstitution DCGetFinancialInstitution(DirectConnection directConnection)
            throws MalformedURLException, ModelException {
        OFXV1Connection connection = new OFXV1Connection();
        DirectConnection.FIData fiData = getFIData(fd -> fd.getID() == directConnection.getFIID())
                .orElseThrow(() -> new ModelException(ModelException.ErrorCode.INVALID_DIRECT_CONNECTION,
                        "DirectConnection " + directConnection.getName() + " doesn't have valid FIID",
                        null));
        BaseFinancialInstitutionData baseFIData = new BaseFinancialInstitutionData();
        baseFIData.setFinancialInstitutionId(fiData.getFIID());
        baseFIData.setOFXURL(new URL(fiData.getURL()));
        baseFIData.setName(fiData.getName());
        baseFIData.setOrganization(fiData.getORG());
        FinancialInstitution fi = new FinancialInstitutionImpl(baseFIData, connection);
        fi.setLanguage(Locale.US.getISO3Language().toUpperCase());
        return fi;
    }

    // download account statement from DirectConnection
    // currently only support SPENDING account type
    Set<TransactionType> DCDownloadAccountStatement(Account account)
            throws IllegalArgumentException, MalformedURLException, NoSuchAlgorithmException,
            InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, OFXException, DaoException, ModelException {

        Account.Type accountType = account.getType();
        AccountDC adc = getAccountDC(account.getID()).orElseThrow(() ->
                new ModelException(ModelException.ErrorCode.ACCOUNT_NO_DC,
                        account.getName() + " has no AccountDC", null));
        DirectConnection directConnection = getDCInfo(dc -> dc.getID() == adc.getDCID()).orElseThrow(() ->
                new ModelException(ModelException.ErrorCode.INVALID_ACCOUNT_DC,
                        "AccountDC for account " + account.getName() + " has no DirectConnection", null));
        FinancialInstitution financialInstitution = DCGetFinancialInstitution(directConnection);
        getClientUID().ifPresent(uuid -> financialInstitution.setClientUID(uuid.toString()));

        final String username = new String(decrypt(directConnection.getEncryptedUserName()));
        final String password = new String(decrypt(directConnection.getEncryptedPassword()));
        final String clearAccountNumber = new String(decrypt(adc.getEncryptedAccountNumber()));

        final FinancialInstitutionAccount fiAccount;
        if (accountType.equals(Account.Type.CREDIT_CARD)) {
            CreditCardAccountDetails creditCardAccountDetails = new CreditCardAccountDetails();
            creditCardAccountDetails.setAccountNumber(clearAccountNumber);
            fiAccount = financialInstitution.loadCreditCardAccount(creditCardAccountDetails, username, password);
        } else if (accountType.isGroup(Account.Type.Group.INVESTING)) {
            InvestmentAccountDetails investmentAccountDetails = new InvestmentAccountDetails();
            investmentAccountDetails.setAccountNumber(clearAccountNumber);
            fiAccount = financialInstitution.loadInvestmentAccount(investmentAccountDetails, username, password);
        } else {
            BankAccountDetails bankAccountDetails = new BankAccountDetails();
            bankAccountDetails.setAccountType(AccountType.valueOf(adc.getAccountType()));
            bankAccountDetails.setBankId(adc.getRoutingNumber());
            bankAccountDetails.setAccountNumber(clearAccountNumber);
            fiAccount = financialInstitution.loadBankAccount(bankAccountDetails,  username, password);
        }

        java.util.Date endDate = new java.util.Date();
        java.util.Date startDate = adc.getLastDownloadDateTime();

        // lastReconcileDate should correspond to the end of business of on that day
        // at the local time zone (use systemDefault zone for now).
        // so we use the start of the next day as the start date
        LocalDate lastReconcileDate = account.getLastReconcileDate();
        java.util.Date lastReconcileDatePlusOneDay = lastReconcileDate == null ?
                null : java.sql.Date.from(lastReconcileDate.plusDays(1).atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant());
        if (lastReconcileDatePlusOneDay != null && startDate.compareTo(lastReconcileDatePlusOneDay) < 0)
            startDate = lastReconcileDatePlusOneDay;

        daoManager.beginTransaction();
        try {
            AccountStatement statement = fiAccount.readStatement(startDate, endDate);
            Set<TransactionType> unTested = importAccountStatement(account, statement);
            adc.setLastDownloadInfo(statement.getLedgerBalance().getAsOfDate(),
                    BigDecimal.valueOf(statement.getLedgerBalance().getAmount())
                            .setScale(SecurityHolding.CURRENCYDECIMALLEN, RoundingMode.HALF_UP));
            mergeAccountDC(adc);
            return unTested;
        } catch (DaoException e) {
            try {
                daoManager.rollback();
                initTransactionList(); // this is a hack.  Need to re-organize the code
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }


    /**
     * read account statement from OFX file
     * @param file - input ofx file
     * @return BankStatementResponse
     * @throws IOException - from open input file
     * @throws ModelException - from OFX parse
     */
    BankStatementResponse readOFXStatement(final File file) throws IOException, ModelException {
        final OFXBankStatementReader reader = new OFXBankStatementReader();
        try {
            final BankStatementResponse statement = reader.readOFXStatement(new FileInputStream(file));

            final String warning = reader.getWarning();
            if (warning != null)
                logger.warn("readOFXStatement " + file.getAbsolutePath() + " warning: "
                        + System.lineSeparator() + warning);
            return statement;
        } catch (OFXParseException e) {
            throw new ModelException(ModelException.ErrorCode.OFX_PARSE_EXCEPTION,
                    "OFX parse exception on " + file.getAbsolutePath(), e);
        }
    }

    // Banking transaction logic is currently coded in.
    Set<TransactionType> importAccountStatement(Account account, AccountStatement statement) throws DaoException {
        if (statement.getTransactionList() == null)
            return Collections.emptySet();  // didn't download any transaction, do nothing

        Set<String> downloadedIDSet = account.getTransactionList().stream().map(Transaction::getFITID)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());

        Set<TransactionType> testedTransactionType = new HashSet<>(Arrays.asList(TransactionType.OTHER,
                TransactionType.CREDIT, TransactionType.DEBIT, TransactionType.CHECK, TransactionType.INT));
        Set<TransactionType> unTestedTransactionType = new HashSet<>();

        ArrayList<Transaction> tobeImported = new ArrayList<>();
        for (com.webcohesion.ofx4j.domain.data.common.Transaction ofx4jT
                : statement.getTransactionList().getTransactions()) {
            if (downloadedIDSet.contains(ofx4jT.getId()))
                continue; // this transaction has been downloaded. skip

            // posted is always at 1200 UTC, which would convert to the same date at any time zone
            LocalDate tDate = ofx4jT.getDatePosted().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            Transaction.TradeAction ta = null;
            BigDecimal amount = ofx4jT.getBigDecimalAmount();
            Category category = new Category();
            switch (ofx4jT.getTransactionType()) {
                case CREDIT:
                case DEP:
                case DIRECTDEP:
                    ta = Transaction.TradeAction.DEPOSIT;
                    break;
                case CHECK:
                case DEBIT:
                case DIRECTDEBIT:
                case PAYMENT:
                case REPEATPMT:
                    ta = Transaction.TradeAction.WITHDRAW;
                    amount = amount.negate();
                    break;
                case INT:
                    ta = Transaction.TradeAction.DEPOSIT;
                    category = getCategory(c -> c.getName().equals("Interest Inc")).orElse(null);
                    break;
                case DIV:
                    ta = Transaction.TradeAction.DEPOSIT;
                    category = getCategory(c -> c.getName().equals("Div Income")).orElse(null);
                    break;
                case FEE:
                case SRVCHG:
                    ta = Transaction.TradeAction.WITHDRAW;
                    category = getCategory(c -> c.getName().equals("Fees & Charges")).orElse(null);
                    break;
                case XFER:
                    if (amount.compareTo(BigDecimal.ZERO) >= 0)
                        ta = Transaction.TradeAction.DEPOSIT;
                    else {
                        ta = Transaction.TradeAction.WITHDRAW;
                        amount = amount.negate();
                    }
                    break;
                case ATM:
                case CASH:
                case OTHER:
                case POS:
                    if (amount.compareTo(BigDecimal.ZERO) >= 0) {
                        ta = Transaction.TradeAction.DEPOSIT;
                    } else {
                        ta = Transaction.TradeAction.WITHDRAW;
                        amount = amount.negate();
                    }
                    break;
            }

            if (!testedTransactionType.contains(ofx4jT.getTransactionType()))
                unTestedTransactionType.add(ofx4jT.getTransactionType());

            Transaction transaction = new Transaction(account.getID(), tDate, ta,
                    category == null ? 0 : category.getID());

            transaction.setAmount(amount);
            transaction.setFITID(ofx4jT.getId());

            String refString;
            if (ofx4jT.getCheckNumber() != null) {
                refString = ofx4jT.getCheckNumber();
                if (ofx4jT.getReferenceNumber() != null)
                    refString += " " + ofx4jT.getReferenceNumber();
            } else {
                if (ofx4jT.getReferenceNumber() != null)
                    refString = ofx4jT.getReferenceNumber();
                else
                    refString = "";
            }
            transaction.setReference(refString);


            String payee;
            if (ofx4jT.getName() != null) {
                payee = ofx4jT.getName();
                if (ofx4jT.getPayee() != null && ofx4jT.getPayee().getName() != null)
                    payee += " " + ofx4jT.getPayee().getName();
            } else {
                if (ofx4jT.getPayee() != null && ofx4jT.getPayee().getName() != null)
                    payee = ofx4jT.getPayee().getName();
                else
                    payee = "";
            }
            transaction.setPayee(payee);

            String memo = ofx4jT.getMemo();
            transaction.setMemo(memo == null ? "" : memo);

            transaction.setStatus(Transaction.Status.CLEARED); // downloaded transactions are all cleared

            tobeImported.add(transaction);
        }

        daoManager.beginTransaction();
        try {
            for (Transaction t : tobeImported)
                insertTransaction(t, InsertMode.DB_ONLY);
            daoManager.commit();
            transactionList.addAll(tobeImported);
            FXCollections.sort(transactionList, Comparator.comparing(Transaction::getID));
            updateAccountBalance(a -> a.getID() == account.getID());
            return unTestedTransactionType;
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    Collection<AccountProfile> DCDownloadFinancialInstitutionAccountProfiles(DirectConnection directConnection)
            throws MalformedURLException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException,
            UnrecoverableKeyException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, OFXException, DaoException, ModelException {
        FinancialInstitution financialInstitution = DCGetFinancialInstitution(directConnection);
        getClientUID().ifPresent(uuid -> financialInstitution.setClientUID(uuid.toString()));
        String username = new String(decrypt(directConnection.getEncryptedUserName()));
        String password = new String(decrypt(directConnection.getEncryptedPassword()));

        return financialInstitution.readAccountProfiles(username, password);
    }

    // encrypt a char array using master password in the vault, return encrypted and encoded
    String encrypt(final char[] secret) throws NoSuchAlgorithmException, InvalidKeySpecException,
            KeyStoreException, UnrecoverableKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        return getVault().encrypt(secret);
    }

    char[] decrypt(final String encodedEncryptedSecretWithSaltAndIV) throws IllegalArgumentException,
            NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException, UnrecoverableKeyException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        return getVault().decrypt(encodedEncryptedSecretWithSaltAndIV);
    }

    // given a date, an originating accountID, and receiving accountid, and the amount of cash flow
    // find a matching transaction in a sorted list
    // return the index of matching transaction in tList
    // return -1 if no match found or t is a split transaction
    // tList is sorted according to the date, reverse(isSplit), accountid
    // transactions with getMatchID > 0 will not be considered.
    private int findMatchingTransaction(LocalDate date, int fromAccountID, int toAccountID, BigDecimal cashFlow,
                                List<Transaction> tList) {
        for (int j = 0; j < tList.size(); j++) {
            Transaction t1 = tList.get(j);
            if (t1.getTDate().isBefore(date) || (t1.getAccountID() < toAccountID) || t1.isSplit())
                continue;

            if ((t1.getAccountID() > toAccountID) || t1.getTDate().isAfter(date)) {
                // pass the date or the account ID
                return -1;
            }
            if (!t1.isTransfer() || (t1.getMatchID() > 0))
                continue;
            if ((fromAccountID == -t1.getCategoryID()) && (cashFlow.add(t1.cashTransferAmount()).signum() == 0))
                return j;
        }
        return -1;
    }

    private void fixDB() throws DaoException {
        // load all transactions
        final SortedList<Transaction> sortedList = new SortedList<>(transactionList,
                Comparator.comparing(Transaction::getTDate)
                        .reversed().thenComparing(Transaction::isSplit).reversed()  // put split first
                        .thenComparing(Transaction::getAccountID));
        final int nTrans = sortedList.size();
        logger.info("Total " + nTrans + " transactions");

        if (nTrans == 0)
            return; // nothing to do

        final List<Transaction> updateList = new ArrayList<>();  // transactions needs to be updated in DB
        final List<Transaction> unMatchedList = new ArrayList<>(); // (partially) unmatched transactions

        for (int i = 0; i < nTrans; i++) {
            Transaction t0 = sortedList.get(i);
            if (t0.isSplit()) {
                boolean needUpdate = false;
                int unMatched = 0;
                for (int s = 0; s < t0.getSplitTransactionList().size(); s++) {
                    // loop through split transaction list
                    SplitTransaction st = t0.getSplitTransactionList().get(s);
                    if (!st.isTransfer(t0.getAccountID()) || (st.getMatchID() > 0)) {
                        // either not a transfer, or already matched
                        continue;
                    }

                    // transfer split transaction
                    unMatched++;  // we've seen a unmatched
                    boolean modeAgg = false; // default not aggregate
                    int matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -st.getCategoryID(),
                            st.getAmount(), sortedList.subList(i+1, nTrans));
                    if (matchIdx < 0) {
                        // didn't find match, it's possible more than one split transaction transferring
                        // to the same account, the receiving account aggregates all into one transaction.
                        modeAgg = true; // try aggregate mode
                        logger.debug("Aggregate mode");
                        BigDecimal cf = BigDecimal.ZERO;
                        for (int s1 = s; s1 < t0.getSplitTransactionList().size(); s1++) {
                            SplitTransaction st1 = t0.getSplitTransactionList().get(s1);
                            if (st1.getCategoryID().equals(st.getCategoryID()))
                                cf = cf.add(st1.getAmount());
                        }
                        matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -st.getCategoryID(),
                                cf, sortedList.subList(i+1, nTrans));
                    }
                    if (matchIdx >= 0) {
                        // found a match
                        needUpdate = true;
                        unMatched--;
                        Transaction t1 = sortedList.get(i+1+matchIdx);
                        if (modeAgg) {
                            // found a match via modeAgg
                            for (int s1 = s; s1 < t0.getSplitTransactionList().size(); s1++) {
                                SplitTransaction st1 = t0.getSplitTransactionList().get(s1);
                                if (st1.getCategoryID().equals(st.getCategoryID()))
                                    st1.setMatchID(t1.getID());
                            }
                        } else {
                            st.setMatchID(t1.getID());
                        }
                        t1.setMatchID(t0.getID(), st.getID());
                        updateList.add(t1);
                    }
                }
                if (needUpdate) {
                    updateList.add(t0);
                }
                if (unMatched != 0) {
                    unMatchedList.add(t0);
                }
            } else {
                // single transaction
                // loop through the remaining transaction for the same day
                if (!t0.isTransfer() || (t0.getMatchID() > 0)) {
                    continue;
                }
                int matchIdx = findMatchingTransaction(t0.getTDate(), t0.getAccountID(), -t0.getCategoryID(),
                        t0.cashTransferAmount(), sortedList.subList(i+1, nTrans));
                if (matchIdx >= 0) {
                    Transaction t1 = sortedList.get(i+1+matchIdx);
                    t0.setMatchID(t1.getID(), -1);
                    t1.setMatchID(t0.getID(), -1);
                    updateList.add(t0);
                    updateList.add(t1);
                } else {
                    unMatchedList.add(t0);
                }
            }
        }

        int cnt = 0;
        try {
            daoManager.beginTransaction();
            TransactionDao transactionDao = (TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION);
            for (Transaction t : updateList) {
                    transactionDao.update(t);
                    cnt++;
            }
            daoManager.commit();
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }

        String message = "Total " + nTrans + " transactions processed." + "\n"
                + "Found " + updateList.size() + " matching transactions." + "\n"
                + "Updated " + cnt + " transactions." + "\n"
                + "Remain " + unMatchedList.size() + " unmatched transactions.";

        logger.info(message);
    }

    void importFromQIF(File file, String defaultAccountName) throws IOException, ModelException, DaoException {
        final QIFParser qifParser = new QIFParser(defaultAccountName);
        if (qifParser.parseFile(file) < 0)
            throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION,
                    file.getAbsolutePath(), null);

        daoManager.beginTransaction();
        try {
            final AccountDao accountDao = (AccountDao) daoManager.getDao(DaoManager.DaoType.ACCOUNT);
            for (Account account : qifParser.getAccountList()) {
                accountDao.insert(account);
            }
            initAccountList();

            final SecurityDao securityDao = (SecurityDao) daoManager.getDao(DaoManager.DaoType.SECURITY);
            for (Security security : qifParser.getSecurityList()) {
                securityDao.insert(security);
            }
            initSecurityList();

            final Map<String, Security> tickerSecurityMap = new HashMap<>();

            final CategoryDao categoryDao = (CategoryDao) daoManager.getDao(DaoManager.DaoType.CATEGORY);
            for (Category category : qifParser.getCategorySet()) {
                categoryDao.insert(category);
            }
            categoryList.setAll(categoryDao.getAll());

            final TagDao tagDao = (TagDao) daoManager.getDao(DaoManager.DaoType.TAG);
            for (Tag tag : qifParser.getTagSet()) {
                tagDao.insert(tag);
            }
            tagList.setAll(((TagDao) daoManager.getDao(DaoManager.DaoType.TAG)).getAll());

            Map<String, Integer> categoryNameIDMap = new HashMap<>();
            getCategoryList().forEach(c -> categoryNameIDMap.put(c.getName(), c.getID()));
            getAccountList(a -> true).forEach(a -> categoryNameIDMap.put("[" + a.getName() + "]", -a.getID()));
            for (Map.Entry<String, List<Transaction>> entry : qifParser.getCategoryNameTransactionMap().entrySet()) {
                final int categoryID = categoryNameIDMap.getOrDefault(entry.getKey(), 0);
                entry.getValue().forEach(t -> t.setCategoryID(categoryID));
            }

            for (Map.Entry<String, List<SplitTransaction>> entry :
                    qifParser.getCategoryNameSplitTransactionMap().entrySet()) {
                final int categoryID = categoryNameIDMap.getOrDefault(entry.getKey(), 0);
                entry.getValue().forEach(st -> st.setCategoryID(categoryID));
            }

            Map<String, Integer> tagNameIDMap = new HashMap<>();
            getTagList().forEach(t -> tagNameIDMap.put(t.getName(), t.getID()));
            for (Map.Entry<String, List<Transaction>> entry : qifParser.getTagNameTransactionMap().entrySet()) {
                final int tagID = tagNameIDMap.getOrDefault(entry.getKey(), 0);
                entry.getValue().forEach(t -> t.setTagID(tagID));
            }

            for (Map.Entry<String, List<SplitTransaction>> entry :
                    qifParser.getTagNameSplitTransactionMap().entrySet()) {
                final int tagID = tagNameIDMap.getOrDefault(entry.getKey(), 0);
                entry.getValue().forEach(st -> st.setTagID(tagID));
            }

            TransactionDao transactionDao = (TransactionDao) daoManager.getDao(DaoManager.DaoType.TRANSACTION);
            Map<String, Integer> accountNameIDMap = new HashMap<>();
            getAccountList(a -> true).forEach(a -> accountNameIDMap.put(a.getName(), a.getID()));
            Map<String, List<Transaction>> accountNameTransactionMap = qifParser.getAccountNameTransactionMap();
            Map<String, Security> securityNameMap = new HashMap<>();
            getSecurityList().forEach(security -> securityNameMap.put(security.getName(), security));
            List<Pair<Security, Price>> tradePriceList = new ArrayList<>();
            for (Map.Entry<String, List<Transaction>> entry : accountNameTransactionMap.entrySet()) {
                final int accountID = accountNameIDMap.getOrDefault(entry.getKey(), 0);
                if (accountID <= 0)
                    throw new ModelException(ModelException.ErrorCode.INVALID_TRANSACTION,
                            "Bad account name " + entry.getKey(), null);
                for (Transaction t : entry.getValue()) {
                    t.setAccountID(accountID);
                    transactionDao.insert(t);

                    // save trade price
                    Security security = securityNameMap.get(t.getSecurityName());
                    if (security != null) {
                        BigDecimal p = t.getPrice();
                        LocalDate d = t.getTDate();
                        if (p != null && p.signum() > 0) {
                            tradePriceList.add(new Pair<>(security, new Price(d, p)));
                        }
                    }
                }
            }

            SecurityPriceDao securityPriceDao = (SecurityPriceDao) daoManager.getDao(DaoManager.DaoType.SECURITY_PRICE);
            securityPriceDao.mergePricesToDB(tradePriceList); // need to add trade price before the other prices

            // save imported prices last
            final List<Pair<Security, Price>> priceList = new ArrayList<>();
            for (Pair<String, Price> ticker_price : qifParser.getPriceList()) {
                final String ticker = ticker_price.getKey();
                final Price p = ticker_price.getValue();
                final Security security = tickerSecurityMap.computeIfAbsent(ticker,
                        k -> getSecurity(s -> s.getTicker().equals(ticker)).orElse(null));
                if (security == null)
                    throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION, "Bad ticker " + ticker, null);
                priceList.add(new Pair<>(security, p));
            }
            securityPriceDao.mergePricesToDB(priceList);  // this may over write trade prices

            daoManager.commit();

            // reload transaction list
            initTransactionList();
            initAccountList();

            // link up linked transactions
            fixDB();
        } catch (DaoException e) {
            try {
                daoManager.rollback();
            } catch (DaoException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
    }

    String exportToQIF(boolean exportAccount, boolean exportCategory, boolean exportSecurity,
                       boolean exportTransaction, LocalDate fromDate, LocalDate toDate, List<Account> accountList)
            throws DaoException, ModelException {
        final StringBuilder stringBuilder = new StringBuilder();

        if (exportCategory) {
            // export Tags first
            stringBuilder.append("!Type:Tag").append(EOL);
            getTagList().forEach(t -> stringBuilder.append(t.toQIF()));

            stringBuilder.append("!Type:Cat").append(EOL);
            getCategoryList().forEach(c -> stringBuilder.append(c.toQIF()));
        }

        if (exportAccount || accountList.size() > 1) {
            // need to export account information
            stringBuilder.append("!Option:AutoSwitch").append(EOL);
            stringBuilder.append("!Account").append(EOL);
            getAccountList(a -> !a.getName().equals(MainModel.DELETED_ACCOUNT_NAME))
                    .forEach(a -> stringBuilder.append(a.toQIF(false)));
            stringBuilder.append("!Clear:AutoSwitch").append(EOL);
        }

        if (exportSecurity) {
            stringBuilder.append("!Type:Security").append(EOL);
            getSecurityList().forEach(s -> stringBuilder.append(s.toQIF()));
        }

        if (exportTransaction) {
            stringBuilder.append("!Option:AutoSwitch").append(EOL);
            for (Account account : accountList) {
                stringBuilder.append("!Account").append(EOL);
                stringBuilder.append(account.toQIF(true));
                stringBuilder.append("!Type:").append(account.getType().toQIF(true)).append(EOL);

                for (Transaction transaction : account.getTransactionList()) {
                    if (transaction.getTDate().isBefore(fromDate))
                        continue;
                    if (transaction.getTDate().isAfter(toDate))
                        break;
                    stringBuilder.append(transaction.toQIF(this));
                }
            }
        }

        if (exportSecurity) {
            // need to export prices if securities are exported.
            for (Security s : getSecurityList()) {
                if (s.getTicker().isEmpty())
                    continue; // we don't export prices for security without a ticker
                for (Price p : getSecurityPriceList(s)) {
                    final String ticker = s.getTicker();
                    stringBuilder.append(p.toQIF(ticker));
                }
            }
        }

        return stringBuilder.toString();
    }

}
