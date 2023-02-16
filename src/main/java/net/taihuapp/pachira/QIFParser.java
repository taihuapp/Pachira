/*
 * Copyright (C) 2018-2023.  Guangliang He.  All Rights Reserved.
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

import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

class QIFParser {

    private static final Logger mLogger = LogManager.getLogger(QIFParser.class);

    // These are the exportable lists show in the QIF99 spec
    // CLASS and TEMPLATE are not being used
    private enum RecordType { CLASS, CAT, MEMORIZED, SECURITY, PRICES, BANK, INVITEM, TEMPLATE, ACCOUNT, TAG }

    private final Map<String, List<Transaction>> accountNameTransactionMap = new HashMap<>();
    private final Map<String, List<Transaction>> categoryNameTransactionMap = new HashMap<>();
    private final Map<String, List<Transaction>> tagNameTransactionMap = new HashMap<>();
    private final Map<String, List<SplitTransaction>> categoryNameSplitTransactionMap = new HashMap<>();
    private final Map<String, List<SplitTransaction>> tagNameSplitTransactionMap = new HashMap<>();

    Map<String, List<Transaction>> getAccountNameTransactionMap() { return accountNameTransactionMap; }
    Map<String, List<Transaction>> getCategoryNameTransactionMap() { return categoryNameTransactionMap; }
    Map<String, List<Transaction>> getTagNameTransactionMap() { return tagNameTransactionMap; }
    Map<String, List<SplitTransaction>> getCategoryNameSplitTransactionMap() { return categoryNameSplitTransactionMap; }
    Map<String, List<SplitTransaction>> getTagNameSplitTransactionMap() { return tagNameSplitTransactionMap; }

    private static Tag parseTagFromQIFLines(List<String> lines)  {
        Tag tag = new Tag();
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    tag.setName(l.substring(1));
                    break;
                case 'D':
                    tag.setDescription(l.substring(1));
                    break;
                default:
                    return null;
            }
        }
        return tag;
    }

    static Category parseCategoryFromQIFLines(List<String> lines)  {
        Category category = new Category();
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    category.setName(l.substring(1));
                    break;
                case 'D':
                    category.setDescription(l.substring(1));
                    break;
                case 'T':
                    category.setIsTaxRelated(true);
                    break;
                case 'R':
                    category.setTaxRefNum(Integer.parseInt(l.substring(1)));
                    break;
                case 'I':
                    category.setIsIncome(true);
                    break;
                case 'E':
                    category.setIsIncome(false);
                    break;
                case 'B':
                    category.setBudgetAmount(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                default:
                    return null;
            }
        }
        return category;
    }

    // starting from lines.get(startIdx) seek the line number of the next line
    // equals match
    // if not found, -1 is returned
    private static int findNextMatch(List<String> lines, int startIdx) {
        for (int i = startIdx; i < lines.size(); i++) {
            if (lines.get(i).equals("^")) {
                return i;
            }
        }
        return -1;
    }

    static Security parseSecurityFromQIFLines(List<String> lines) {
        Security security = new Security();
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    security.setName(l.substring(1));
                    break;
                case 'S':
                    security.setTicker(l.substring(1));
                    break;
                case 'T':
                    security.setType(Security.Type.fromString(l.substring(1)));
                    break;
                case 'G':
                    // goal is omitted
                    break;
                default:
                    return null;
            }
        }
        return security;
    }

    static Account parseAccountFromQIFLines(List<String> lines) {
        Account.Type type = null;
        String name = "";
        String desc = "";
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'N':
                    name = l.substring(1);
                    break;
                case 'T':
                    type = Account.Type.fromQIF(l.substring(1)).orElse(null);
                    break;
                case 'R':
                case 'L':
                    // not used, skip
                    break;
                case 'D':
                    desc = l.substring(1);
                    break;
                default:
                    // bad formatted record, return null
                    return null;
            }
        }
        if (type == null || name.isEmpty())
            return null;

        return new Account(0, type, name, desc, false, Integer.MAX_VALUE, null, BigDecimal.ZERO);
    }

    private static Transaction.Status mapCharToStatus(char c) {
        switch (c) {
            case 'c':
            case '*':
                return Transaction.Status.CLEARED;
            case 'R':
            case 'X':
                return Transaction.Status.RECONCILED;
            default:
                return Transaction.Status.UNCLEARED;
        }
    }

    Transaction parseTransactionFromBTLines(List<String> lines) throws ModelException {
        Transaction t = new Transaction(-1, LocalDate.MIN, Transaction.TradeAction.WITHDRAW, 0);
        SplitTransaction splitTransaction = null;
        List<SplitTransaction> stList = new ArrayList<>();
        BigDecimal tAmount;
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'D':
                    t.setTDate(parseDate(l.substring(1)));
                    break;
                case 'T':
                case 'U':
                    // T amount is always the same as U amount
                    tAmount = new BigDecimal(l.substring(1).replace(",",""));
                    t.setAmount(tAmount.abs());
                    t.setTradeAction(tAmount.signum() >= 0 ?
                            Transaction.TradeAction.DEPOSIT : Transaction.TradeAction.WITHDRAW );
                    break;
                case 'C':
                    t.setStatus(mapCharToStatus(l.charAt(1)));
                    break;
                case 'N':
                    t.setReference(l.substring(1));
                    break;
                case 'P':
                    t.setPayee(l.substring(1));
                    break;
                case 'M':
                    t.setMemo(l.substring(1));
                    break;
                case 'A':
                    mLogger.warn("Address line ignored: " + l.substring(1));
                    break;
                case 'L':
                    String[] names = l.substring(1).split("/");
                    if (!names[0].isEmpty())
                        categoryNameTransactionMap.computeIfAbsent(names[0], k -> new ArrayList<>()).add(t);
                    if (names.length > 1 && !names[1].isEmpty())
                        tagNameTransactionMap.computeIfAbsent(names[1], k -> new ArrayList<>()).add(t);
                    break;
                case 'S':
                    splitTransaction = new SplitTransaction(-1, 0, -1, "", null, -1);
                    stList.add(splitTransaction);
                    String[] names0 = l.substring(1).split("/");
                    if (!names0[0].isEmpty())
                        categoryNameSplitTransactionMap.computeIfAbsent(names0[0], k -> new ArrayList<>())
                                .add(splitTransaction);
                    if (names0.length > 1 && !names0[1].isEmpty())
                        tagNameSplitTransactionMap.computeIfAbsent(names0[1], k -> new ArrayList<>())
                                .add(splitTransaction);
                    break;
                case 'E':
                    if (splitTransaction == null) {
                        throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION,
                                "Bad formatted BankTransactionSplit " + lines, null);
                    }
                    splitTransaction.setMemo(l.substring(1));
                    break;
                case '$':
                    if (splitTransaction == null) {
                        throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION,
                                "Bad formatted BankTransactionSplit " + lines, null);
                    }
                    splitTransaction.setAmount(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                case 'F':
                    mLogger.warn("F flag in BankTransaction not implemented " + lines);
                    break;
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    // 7 lines of amortization record, ignored for now
                    mLogger.warn("Amortization ignored: " + l.substring(1));
                    break;
                default:
                    mLogger.error("BankTransaction Offending line: " + l);
                    return null;
            }
        }
        if (!stList.isEmpty())
            t.setSplitTransactionList(stList);
        return t;
    }

    Transaction parseTransactionFromTTLines(List<String> lines) throws ModelException {
        Transaction t = new Transaction(-1, LocalDate.MIN, Transaction.TradeAction.WITHDRAW, 0);
        String actionStr = null;
        String categoryName = null;
        BigDecimal tAmount = null;
        for (String l : lines) {
            switch (l.charAt(0)) {
                case 'D':
                    t.setTDate(parseDate(l.substring(1)));
                    break;
                case 'N':
                    actionStr = l.substring(1).toUpperCase();
                    break;
                case 'Y':
                    t.setSecurityName(l.substring(1));
                    break;
                case 'I':
                    // price is ignored
                    break;
                case 'Q':
                    t.setQuantity(new BigDecimal(l.substring(1).replace(",", "")));
                    break;
                case 'C':
                    t.setStatus(mapCharToStatus(l.charAt(1)));
                    break;
                case 'P':
                    // transfer reminder text is ignored
                    break;
                case 'M':
                    t.setMemo(l.substring(1));
                    break;
                case 'O':
                    t.setCommission(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                case 'L':
                    String[] names = l.substring(1).split("/");
                    if (!names[0].isEmpty()) {
                        String[] tokens = names[0].split("\\|");
                        if (tokens.length > 1) {
                            mLogger.error(lines + "\nMultiple tokens seen at Category line: "
                                    + l + ", importing as " + tokens[tokens.length - 1]);
                        }
                        categoryName = tokens[tokens.length-1];
                    }
                    if ((names.length > 1) && !names[1].isEmpty())
                        tagNameTransactionMap.computeIfAbsent(names[1], k -> new ArrayList<>()).add(t);
                    break;
                case 'T':
                    tAmount = new BigDecimal(l.substring(1).replace(",",""));
                    t.setAmount(tAmount);
                    break;
                case 'U':
                    // u amount is ignored
                    break;
                case '$':
                    // amount transferred is ignored
                    break;
                default:
                    throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION, "Bad line: " + l, null);
            }
        }
        if (actionStr != null) {
            switch (actionStr) {
                case "BUYX":
                case "BUYBONDX":
                case "SELLX":
                case "CGLONGX":
                case "CGMIDX":
                case "CGSHORTX":
                case "CVTSHRTX":
                case "DIVX":
                case "INTINCX":
                case "MISCINCX":
                case "MISCEXPX":
                case "MARGINTX":
                case "RTRNCAPX":
                case "SHTSELLX":
                    actionStr = actionStr.substring(0, actionStr.length()-1);
                    if (categoryName == null || !(categoryName.startsWith("[") && categoryName.endsWith("]"))) {
                        // it's a transfer transaction, but transfer account is not set
                        // set to DELETED_ACCOUNT_NAME
                        categoryName = "[" + MainApp.DELETED_ACCOUNT_NAME + "]";
                    }
                    break;
                case "CASH":
                    if (tAmount != null && tAmount.signum() < 0) {
                        actionStr = "WITHDRAW";
                        t.setAmount(tAmount.negate());
                    } else {
                        actionStr = "DEPOSIT";
                    }
                    break;
                case "CONTRIBX":
                case "XIN":
                    actionStr = "DEPOSIT";
                    break;
                case "WITHDRWX":
                case "XOUT":
                    actionStr = "WITHDRAW";
                    break;
            }
            t.setTradeAction(Transaction.TradeAction.valueOf(actionStr));
            if (actionStr.equals("STKSPLIT"))
                t.setOldQuantity(BigDecimal.TEN);

            if (categoryName != null)
                categoryNameTransactionMap.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(t);
        }
        return t;
    }

    // parse ticker symbol and price from QIF lines
    Pair<String, Price> parsePriceFromQIFLines(List<String> lines) {
        if (lines.size() > 1) {
            mLogger.error("Price record, expected 1 line, got " + lines.size());
            return null;
        }
        String[] tokens = lines.get(0).split(",");
        if (tokens.length != 3) {
            mLogger.error("Expect 3 ',' separated fields, got " + tokens.length);
            return null;
        }

        // the actual price has two possible formats:
        // decimal, xxx.yyyy
        // fraction [x ]y/z
        BigDecimal p;
        int idx0 = tokens[1].indexOf('/');
        if (idx0 == -1) {
            // not a fraction
            if (tokens[1].length() > 0) {
                p = new BigDecimal(tokens[1]);
            } else {
                p = BigDecimal.ZERO;
            }
        } else {
            int whole, num, den, idx1;
            den = Integer.parseInt(tokens[1].substring(idx0 + 1));
            idx1 = tokens[1].indexOf(' ');
            if (idx1 == -1) {
                // no space
                whole = 0;
                num = Integer.parseInt(tokens[1].substring(0,idx0));
            } else {
                whole = Integer.parseInt(tokens[1].substring(0, idx1));
                num = Integer.parseInt(tokens[1].substring(idx1+1, idx0));
            }
            p = new BigDecimal(whole).add(new BigDecimal(num).divide(new BigDecimal(den),
                    MainModel.PRICE_QUANTITY_FRACTION_LEN, RoundingMode.HALF_UP));
        }
        return new Pair<>(tokens[0].replace("\"", ""),
                new Price(parseDate(tokens[2].replace("\"", "").trim()), p));
    }

    private final String mDefaultAccountName;
    private final List<Account> mAccountList;
    private final Set<Category> mCategorySet;
    private final Set<Tag> mTagSet;
    private final List<Security> mSecurityList;
    private final List<Pair<String, Price>> mPriceList;

    // public constructor
    QIFParser(String dan) {
        mDefaultAccountName = dan;
        mAccountList = new ArrayList<>();
        mCategorySet = new HashSet<>();
        mTagSet = new HashSet<>();
        mSecurityList = new ArrayList<>();
        mPriceList = new ArrayList<>();
    }

    // parse QIF formatted date
    private static LocalDate parseDate(String s) {
        DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                .appendValue(ChronoField.MONTH_OF_YEAR).appendLiteral('/')
                .appendValue(ChronoField.DAY_OF_MONTH).appendLiteral('/')
                .appendValueReduced(ChronoField.YEAR, 2, 2, 1970).toFormatter();
        return LocalDate.parse(s.replace(' ', '0').replace('\'', '/'), dtf);
    }

    // return -1 for some sort of failure
    //         0 for success
    int parseFile(File qif) throws IOException, ModelException {
        List<String> allLines = Files.readAllLines(qif.toPath());
        int nLines = allLines.size();
        if (nLines == 0)
            return 0;

        // trim off white spaces
        for (int i = 0; i < nLines; i++) {
            String s = allLines.get(i).trim();
            allLines.set(i, s);
        }

        // About AutoSwitch
        // It occurs in these cases:
        // 1.  none
        // 2.  Option:AutoSwitch and then Clear:AutoSwitch
        // 3.  2 + another Option:AutoSwitch, but no more Clea:AutoSwitch
        // The accounts bracketed in Option:AutoSwitch and Clear:AutoSwitch are meant for import
        // set boolean autoSwitch to be true at start, do nothing when encounters Option!AutoSwitch
        // set autoSwitch to be false when encounters Clear:AutoSwitch.
        boolean autoSwitch = true;
        RecordType currentRecordType = null;
        int i = 0;
        Account account = null;
        while (i < nLines) {
            String line = allLines.get(i);
            switch (line) {
                case "!Type:Tag":
                    currentRecordType = RecordType.TAG;
                    break;
                case "!Type:Cat":
                    currentRecordType = RecordType.CAT;
                    break;
                case "!Type:Bank":
                case "!Type:Cash":
                case "!Type:CCard":
                case "!Type:Oth A":
                case "!Type:Oth L":
                    currentRecordType = RecordType.BANK;
                    break;
                case "!Type:Invst":
                    currentRecordType = RecordType.INVITEM;
                    break;
                case "!Account":
                    currentRecordType = RecordType.ACCOUNT;
                    break;
                case "!Clear:AutoSwitch":
                    currentRecordType = null;
                    autoSwitch = false;
                    break;
                case "!Option:AutoSwitch":
                    currentRecordType = null;
                    break;
                case "!Type:Memorized":
                    currentRecordType = RecordType.MEMORIZED;
                    break;
                case "!Type:Prices":
                    currentRecordType = RecordType.PRICES;
                    break;
                case "!Type:Security":
                    currentRecordType = RecordType.SECURITY;
                    break;
                default:
                    // this is a content line, find the end of the record
                    int j = findNextMatch(allLines, i);
                    if (j == -1) {
                        mLogger.error("Bad formatted file.  Can't find '^'");
                        return -1;
                    }
                    if (currentRecordType == null) {
                        throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION,
                                "Bad RecordType at line " + i + " " + j, null);
                    }
                    switch (currentRecordType) {
                        case CAT:
                            Category category = parseCategoryFromQIFLines(allLines.subList(i, j));
                            if (category != null) {
                                mCategorySet.add(category);
                            } else {
                                mLogger.error("Bad formatted Category text: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        case TAG:
                            Tag tag = parseTagFromQIFLines(allLines.subList(i,j));
                            if (tag != null) {
                                mTagSet.add(tag);
                            } else {
                                mLogger.error("Bad formatted Tag text: " + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        case ACCOUNT:
                            account = parseAccountFromQIFLines(allLines.subList(i, j));
                            if (account != null) {
                                if (autoSwitch) {
                                    mAccountList.add(account);
                                }
                            } else {
                                mLogger.error("Bad formatted Account record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case SECURITY:
                            Security security = parseSecurityFromQIFLines(allLines.subList(i,j));
                            if (security != null) {
                                mSecurityList.add(security);
                            } else {
                                mLogger.error("Bad formatted Security record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case BANK:
                            Transaction bt = parseTransactionFromBTLines(allLines.subList(i,j));
                            accountNameTransactionMap.computeIfAbsent(account == null ?
                                    getDefaultAccountName() : account.getName(),k -> new ArrayList<>()).add(bt);
                            i = j;
                            break;
                        case INVITEM:
                            Transaction tt = parseTransactionFromTTLines(allLines.subList(i,j));
                            accountNameTransactionMap.computeIfAbsent(account == null ?
                                    getDefaultAccountName() : account.getName(),k -> new ArrayList<>()).add(tt);
                            i = j;
                            break;
                        case MEMORIZED:
                            i = j;
                            break;
                        case PRICES:
                            Pair<String, Price> ticker_price = parsePriceFromQIFLines(allLines.subList(i,j));
                            if (ticker_price != null) {
                                mPriceList.add(ticker_price);
                            } else {
                                mLogger.error("Bad formatted Price record: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        default:
                            mLogger.error(currentRecordType + " Not implemented yet");
                            return -1;
                    }
                    break;  // break out the switch
            }
            i++;
        }

        // done with the file, now match Transfer Transactions
        // matchTransferTransaction();
        return 0;
    }

    List<Account> getAccountList() { return mAccountList; }
    List<Security> getSecurityList() { return mSecurityList; }
    Set<Category> getCategorySet() { return mCategorySet; }
    Set<Tag> getTagSet() { return mTagSet; }
    List<Pair<String, Price>> getPriceList() { return mPriceList; }
    private String getDefaultAccountName() { return mDefaultAccountName; }
}
