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

import javafx.util.Pair;
import org.apache.log4j.Logger;

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

    private static final Logger mLogger = Logger.getLogger(QIFParser.class);

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
                                "Bad formatted BankTransactionSplit " + lines.toString(), null);
                    }
                    splitTransaction.setMemo(l.substring(1));
                    break;
                case '$':
                    if (splitTransaction == null) {
                        throw new ModelException(ModelException.ErrorCode.QIF_PARSE_EXCEPTION,
                                "Bad formatted BankTransactionSplit " + lines.toString(), null);
                    }
                    splitTransaction.setAmount(new BigDecimal(l.substring(1).replace(",","")));
                    break;
                case 'F':
                    mLogger.warn("F flag in BankTransaction not implemented " + lines.toString());
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

/*
    static class BankTransaction {
        // split bank transaction
        static class SplitBT {
            private String mCategory; // split category
            private String mTag; // split tag
            private String mMemo; // split memo
            private BigDecimal mAmount; // split amount
            private BigDecimal mPercentage; // % of split if % is used

            // public constructor
            SplitBT() {
                mCategory = "";
                mMemo = "";
                mAmount = BigDecimal.ZERO;
                mPercentage = BigDecimal.ZERO;
            }

            // setters and getters
            void setCategory(String c) { mCategory = c; }
            String getCategory() { return mCategory; }
            void setTag(String t) { mTag = t; }
            String getTag() { return mTag; }
            void setMemo(String m) { mMemo = m; }
            String getMemo() { return mMemo; }
            void setAmount(BigDecimal a) { mAmount = a; }
            BigDecimal getAmount() { return mAmount; }
            void setPercentage(BigDecimal p) { mPercentage = p; }
            BigDecimal getPercentage() { return mPercentage; }
        }

        private String mAccountName;
        private LocalDate mDate;
        private BigDecimal mTAmount;
        private BigDecimal mUAmount;  // not sure what's the difference between T and U amounts
        private Transaction.Status mStatus;
        private String mCheckNumber = ""; // check number or ref, such as ATM, etc, so string is used
        private String mPayee = "";
        private String mMemo = "";
        private final List<String> mAddressList; // QIF says up to 6 lines.
        private String mCategory; // L line if matches [*], then transfer, otherwise, category
        private String mTag; // L line may contain tag as well
        private final List<SplitBT> mSplitList;
        private String[] mAmortizationLines;

        // default constructor
        BankTransaction() {
            mStatus = Transaction.Status.UNCLEARED; // default
            mAddressList = new ArrayList<>();
            mSplitList = new ArrayList<>();
            mAmortizationLines = null;
        }

        @Override
        public String toString() {
            return "BankTransaction{" +
                    "mAccountName='" + mAccountName + '\'' +
                    ", mDate=" + mDate +
                    ", mTAmount=" + mTAmount +
                    ", mPayee='" + mPayee + '\'' +
                    ", mMemo='" + mMemo + '\'' +
                    ", mCategory='" + mCategory + '\'' +
                    '}';
        }

        // setters
        void setAccountName(String a) { mAccountName = a; }
        void setDate(LocalDate d) { mDate = d; }
        void setTAmount(BigDecimal t) { mTAmount = t; }
        void setUAmount(BigDecimal u) { mUAmount = u; }
        void setStatus(char c) {
            switch (c) {
                case 'c':
                case '*':
                    mStatus = Transaction.Status.CLEARED;
                    break;
                case 'X':
                case 'R':
                    mStatus = Transaction.Status.RECONCILED;
                    break;
                default:
                    mStatus = Transaction.Status.UNCLEARED;
                    break;
            }
        }
        void setReference(String r) { mCheckNumber = r; }
        void setPayee(String p) { mPayee = p; }
        void setMemo(String m) { mMemo = m; }
        void addAddress(String a) { mAddressList.add(a); }
        void setCategory(String c) { mCategory = c; }
        void setTag(String t) { mTag = t; }
        void addSplit(SplitBT s) { mSplitList.add(s); }
        void setAmortizationLine(int i, String l) {
            if (mAmortizationLines == null) mAmortizationLines = new String[7];
            mAmortizationLines[i] = l;
        }

        // getters
        String getAccountName() { return mAccountName; }
        LocalDate getDate() { return mDate; }
        BigDecimal getTAmount() { return mTAmount; }
        BigDecimal getUAmount() { return mUAmount; }
        Transaction.Status getStatus() { return mStatus; }

        String getCategoryOrTransfer() { return mCategory; }
        String getTag() { return mTag; }
        String getReference() { return mCheckNumber; }
        String getMemo() { return mMemo; }
        String getPayee() { return mPayee; }
        List<SplitBT> getSplitList() { return mSplitList; }
        List<String> getAddressList() { return mAddressList; }
        String[] getAmortizationLines() { return mAmortizationLines; }

        static BankTransaction fromQIFLines(List<String> lines) {
            BankTransaction bt = new BankTransaction();
            SplitBT splitBT = null;
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'D':
                        bt.setDate(parseDate(l.substring(1)));
                        break;
                    case 'T':
                        bt.setTAmount(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case 'U':
                        bt.setUAmount(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case 'C':
                        bt.setStatus(l.charAt(1));
                        break;
                    case 'N':
                        bt.setReference(l.substring(1));
                        break;
                    case 'P':
                        bt.setPayee(l.substring(1));
                        break;
                    case 'M':
                        bt.setMemo(l.substring(1));
                        break;
                    case 'A':
                        bt.addAddress(l.substring(1));
                        break;
                    case 'L':
                        String[] names = l.substring(1).split("/");
                        if (!names[0].isEmpty())
                            bt.setCategory(names[0]);
                        if (names.length > 1 && !names[1].isEmpty())
                            bt.setTag(names[1]);
                        break;
                    case 'S':
                        if (splitBT != null) {
                            bt.addSplit(splitBT);
                        }
                        splitBT = new SplitBT();
                        String[] names0 = l.substring(1).split("/");
                        if (!names0[0].isEmpty())
                            splitBT.setCategory(names0[0]);
                        if (names0.length > 1 && !names0[1].isEmpty())
                            splitBT.setTag(names0[1]);
                        break;
                    case 'E':
                        if (splitBT == null) {
                            mLogger.error("Bad formatted BankTransactionSplit " + lines.toString());
                            return null;
                        } else {
                            splitBT.setMemo(l.substring(1));
                        }
                        break;
                    case '$':
                        if (splitBT == null) {
                            mLogger.error("Bad formatted BankTransactionSplit " + lines.toString());
                            return null;
                        } else {
                            splitBT.setAmount(new BigDecimal(l.substring(1).replace(",","")));
                        }
                        break;
                    case 'F':
                        mLogger.error("F flag in BankTransaction not implemented " + lines.toString());
                        break;
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        // 7 lines of amortization record, ignored for now
                        bt.setAmortizationLine(Character.getNumericValue(l.charAt(0))-1, l.substring(1));
                        break;
                    default:
                        mLogger.error("BankTransaction Offending line: " + l);
                        return null;
                }
            }
            if (splitBT != null) {
                bt.addSplit(splitBT);
            }
            return bt;
        }
    }

    static class TradeTransaction {

        enum Action { BUY, CGLONG, CGMID, CGSHORT, DIV, INTINC, MISCEXP,
            MISCINC, REINVDIV, REINVINT, REINVLG, REINVMD, REINVSH,
            RTRNCAP, RTRNCAPX, SELL, SHRSIN, SHRSOUT, SHTSELL, SHTSELLX,
            STKSPLIT, DEPOSIT, WITHDRAW
        }

        private String mAccountName;
        private LocalDate mDate;
        private Action mAction;
        private String mSecurityName;
        private BigDecimal mPrice;
        private BigDecimal mQuantity;
        private Transaction.Status mStatus;
        private String mTransferReminderText; // P line
        private String mMemo = "";
        private BigDecimal mCommission;
        private String mCategoryOrTransfer; // L line
        private String mTag;
        private BigDecimal mTAmount; // not sure what's the difference between
        private BigDecimal mUAmount; // T and U amounts
        private BigDecimal mAmountTransferred; // $ line

        TradeTransaction() {
            mStatus = Transaction.Status.UNCLEARED;
            mCommission = BigDecimal.ZERO;
            mPrice = BigDecimal.ZERO; // shouldn't leave it as null
        }

        // setters
        void setAccountName(String a) { mAccountName = a;}
        void setDate(LocalDate d) { mDate = d; }
        void setAction(Action action) { mAction = action; }
        void setSecurityName(String n) { mSecurityName = n; }
        void setPrice(BigDecimal p) { mPrice = p; }
        void setQuantity(BigDecimal q) { mQuantity = q; }
        void setStatus(char c) {
            switch (c) {
                case 'c':
                case '*':
                    mStatus = Transaction.Status.CLEARED;
                    break;
                case 'R':
                case 'X':
                    mStatus = Transaction.Status.RECONCILED;
                    break;
                default:
                    mStatus = Transaction.Status.UNCLEARED;
                    break;
            }
        }
        void setTransferReminderText(String t) { mTransferReminderText = t; }
        void setMemo(String m) { mMemo = m; }
        void setCommission(BigDecimal c) { mCommission = c; }
        void setCategoryOrTransfer(String ct) { mCategoryOrTransfer = ct; }
        void setTag(String t) { mTag = t; }
        void setTAmount(BigDecimal t) { mTAmount = t; }
        void setUAmount(BigDecimal u) { mUAmount = u; }
        void setAmountTransferred(BigDecimal a) { mAmountTransferred = a; }

        // getters
        String getAccountName() { return mAccountName; }
        LocalDate getDate() { return mDate; }
        BigDecimal getTAmount() { return mTAmount; }
        BigDecimal getUAmount() { return mUAmount; }
        Action getAction() { return mAction; }
        String getSecurityName() { return mSecurityName; }
        Transaction.Status getStatus() { return mStatus; }
        String getCategoryOrTransfer() { return mCategoryOrTransfer; }
        String getTag() { return mTag; }
        String getMemo() { return mMemo; }
        BigDecimal getPrice() { return mPrice; }
        BigDecimal getQuantity() { return mQuantity; }
        BigDecimal getCommission() { return mCommission; }

        static TradeTransaction fromQIFLines(List<String> lines) {
            TradeTransaction tt = new TradeTransaction();
            String actionStr = null;
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'D':
                        tt.setDate(parseDate(l.substring(1)));
                        break;
                    case 'N':
                        actionStr = l.substring(1).toUpperCase();
                        break;
                    case 'Y':
                        tt.setSecurityName(l.substring(1));
                        break;
                    case 'I':
                        tt.setPrice(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case 'Q':
                        tt.setQuantity(new BigDecimal(l.substring(1).replace(",", "")));
                        break;
                    case 'C':
                        tt.setStatus(l.charAt(1));
                        break;
                    case 'P':
                        tt.setTransferReminderText(l.substring(1));
                        break;
                    case 'M':
                        tt.setMemo(l.substring(1));
                        break;
                    case 'O':
                        tt.setCommission(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case 'L':
                        String[] names = l.substring(1).split("/");
                        if (!names[0].isEmpty()) {
                            String[] tokens = names[0].split("\\|");
                            if (tokens.length > 1) {
                                mLogger.error(lines + "\nMultiple tokens seen at Category line: "
                                        + l + ", importing as " + tokens[tokens.length - 1]);
                            }
                            tt.setCategoryOrTransfer(tokens[tokens.length - 1]);
                        }
                        if ((names.length > 1) && !names[1].isEmpty())
                            tt.setTag(names[1]);
                        break;
                    case 'T':
                        tt.setTAmount(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case 'U':
                        tt.setUAmount(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    case '$':
                        tt.setAmountTransferred(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    default:
                        mLogger.error("TradeTransaction: Offending line: " + l);
                        return null;

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
                        if (tt.getCategoryOrTransfer() == null || !(tt.getCategoryOrTransfer().startsWith("[")
                                && tt.getCategoryOrTransfer().endsWith("]"))) {
                            // it's a transfer transaction, but transfer account is not set
                            // set to DELETED_ACCOUNT_NAME
                            tt.setCategoryOrTransfer("[" + MainApp.DELETED_ACCOUNT_NAME + "]");
                        }
                        break;
                    case "CASH":
                        BigDecimal tAmount = tt.getTAmount();
                        boolean isTransfer = (tt.getCategoryOrTransfer() != null) &&
                                tt.getCategoryOrTransfer().startsWith("[") &&
                                tt.getCategoryOrTransfer().endsWith("]");
                        if (tAmount != null && tAmount.signum() < 0) {
                            actionStr = "WITHDRAW";
                            tt.setTAmount(tAmount.negate());
                            BigDecimal uAmount = tt.getUAmount();
                            if (uAmount != null)
                                tt.setUAmount(uAmount.negate());
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
                tt.setAction(Action.valueOf(actionStr));
            }
            return tt;
        }
    }
*/

/*
    private static class MemorizedTransaction {
        enum Type { INVESTMENT, EPAYMENT, CHECK, PAYMENT, DEPOSIT }

        private Type mType;
        private BigDecimal mQQuantity; // number of new shares for a split
        private BigDecimal mRQuantity; // number of old shares for a split
        private BankTransaction mBT;
        private TradeTransaction mTT;


        MemorizedTransaction() {
            mBT = null;
            mTT = null;
        }

        // getters
        public Type getType() { return mType; }

        //setters
        void setType(char t) {
            switch (t) {
                case 'I':
                    mType = Type.INVESTMENT;
                    break;
                case 'E':
                    mType = Type.EPAYMENT;
                    break;
                case 'C':
                    mType = Type.CHECK;
                    break;
                case 'P':
                    mType = Type.PAYMENT;
                    break;
                case 'D':
                    mType = Type.DEPOSIT;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type [" + t + "] for MemorizedType");
            }
        }
        void setQQuantity(BigDecimal q) { mQQuantity = q; }
        void setRQuantity(BigDecimal r) { mRQuantity = r; }
        void setTransactionDetails(List<String> lines) {
            if (getType() == Type.INVESTMENT) {
                mTT = TradeTransaction.fromQIFLines(lines);
            } else {
                mBT = BankTransaction.fromQIFLines(lines);
            }
        }

        static MemorizedTransaction fromQIFLines(List<String> lines) {
            List<String> unParsedLines = new ArrayList<>();
            MemorizedTransaction mt = new MemorizedTransaction();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'K':
                        mt.setType(l.charAt(1));
                        break;
                    case 'Q':
                        mt.setQQuantity(new BigDecimal(l.substring(1).replace(",", "")));
                        break;
                    case 'R':
                        mt.setRQuantity(new BigDecimal(l.substring(1).replace(",", "")));
                        break;
                    default:
                        unParsedLines.add(l);
                        break;
                }
            }

            if (unParsedLines.size() > 0) {
                mt.setTransactionDetails(unParsedLines);
            }
            return mt;
        }
    }
*/

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
                    MainApp.PRICE_FRACTION_LEN, RoundingMode.HALF_UP));
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
/*
    private final List<BankTransaction> mBankTransactionList;
    private final List<TradeTransaction> mTradeTransactionList;
    private final List<MemorizedTransaction> mMemorizedTransactionList;
*/

/*
    private void matchTransferTransaction() {
        // TODO: 4/6/16
        // seems more work is needed here
        List<BankTransaction> btList = getBankTransactionList();
        List<TradeTransaction> ttList = getTradeTransactionList();

        Iterator<BankTransaction> btIterator = btList.iterator();
        mLogger.info("Number of bt = " + btList.size());
        //while (btIterator.hasNext()) {
            //System.out.println("bt " + btIterator.next());
        //}

        Iterator<TradeTransaction> ttIterator = ttList.iterator();
        mLogger.info("Number of tt = " + ttList.size());
        //while (ttIterator.hasNext()) {
        //    System.out.println("tt " + ttIterator.next());
        //}

    }
*/

    // public constructor
    QIFParser(String dan) {
        mDefaultAccountName = dan;
        mAccountList = new ArrayList<>();
        mCategorySet = new HashSet<>();
        mTagSet = new HashSet<>();
        mSecurityList = new ArrayList<>();
/*
        mBankTransactionList = new ArrayList<>();
        mTradeTransactionList = new ArrayList<>();
        mMemorizedTransactionList = new ArrayList<>();
*/
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
                                        + allLines.subList(i,j).toString());
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
/*
                            MemorizedTransaction mt = MemorizedTransaction.fromQIFLines(allLines.subList(i,j));
                            mMemorizedTransactionList.add(mt);
*/
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
                            mLogger.error(currentRecordType.toString() + " Not implemented yet");
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
/*
    List<BankTransaction> getBankTransactionList() { return mBankTransactionList; }
    List<TradeTransaction> getTradeTransactionList() { return mTradeTransactionList; }
*/
    private String getDefaultAccountName() { return mDefaultAccountName; }
}
