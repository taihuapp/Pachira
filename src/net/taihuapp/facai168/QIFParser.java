/*
 * Copyright (C) 2017.  Guangliang He.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of FaCai168.
 *
 * FaCai168 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any
 * later version.
 *
 * FaCai168 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.taihuapp.facai168;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ghe on 3/22/15.
 *
 */
class QIFParser {
    // These are the exportable lists show in the QIF99 spec
    // CLASS and TEMPLATE are not being used
    private enum RecordType { CLASS, CAT, MEMORIZED, SECURITY, PRICES, BANK, INVITEM, TEMPLATE, ACCOUNT }

    static class Category {
        private int mID;
        private String mName;  // name of the category
        private String mDescription;  // description
        private boolean mIsIncome; // income category flag
        // mTaxRefNum = -1 for non tax related
        //               0 for tax related but no valid ref num
        //              >0 actual tax ref number
        private int mTaxRefNum;  // Tax reference number (for tax-related items,
        private BigDecimal mBudgetAmount; // budget amount

        public Category() {
            mID = -1;
            mName = "";
            mDescription = "";
            mTaxRefNum = -1;
            mIsIncome = true;
        }

        void setID(int id) { mID = id; }
        int getID() { return mID; }
        public void setName(String n) { mName = n; }
        public String getName() { return mName; }
        void setDescription(String d) { mDescription = d; }
        String getDescription() { return mDescription; }
        void setIsTaxRelated(boolean t) {
            if (t && (getTaxRefNum() < 0)) {
                setTaxRefNum(0); // set it to be tax related
            } else if (!t) {
                setTaxRefNum(-1); // set it to be non tax related
            }
        }
        public boolean isTaxRelated() { return getTaxRefNum() >= 0; }
        void setTaxRefNum(int r) { mTaxRefNum = r; }
        int getTaxRefNum() { return mTaxRefNum; }
        void setIsIncome(boolean i) { mIsIncome = i;}
        boolean isIncome() { return mIsIncome; }
        void setBudgetAmount(BigDecimal b) { mBudgetAmount = b; }
        BigDecimal getBudgetAmount() { return mBudgetAmount; }
        static Category fromQIFLines(List<String> lines)  {
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

        public String toString() { return "[" + mName + "," + mDescription + "]" ;}
    }

    // starting from lines.get(startIdx) seek the line number of the next line
    // equals match
    // if not found, -1 is returned
    private static int findNextMatch(List<String> lines, int startIdx, String match) {
        for (int i = startIdx; i < lines.size(); i++) {
            if (lines.get(i).equals(match)) {
                return i;
            }
        }
        return -1;
    }

    static class Security {
        private String mName;
        private String mSymbol;
        private String mType;
        private String mGoal;

        // constructor
        public Security() {
            mName = "";
            mSymbol = "";
            mType = "";
            mGoal = "";
        }

        void setName(String n) { mName = n; }
        String getName() { return mName; }
        void setSymbol(String s) { mSymbol = s; }
        String getSymbol() { return mSymbol; }
        void setType(String t) { mType = t; }
        String getType() { return mType; }
        void setGoal(String g) { mGoal = g; }
        String getGoal() { return mGoal; }

        static Security fromQIFLines(List<String> lines) {
            Security security = new Security();
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'N':
                        security.setName(l.substring(1));
                        break;
                    case 'S':
                        security.setSymbol(l.substring(1));
                        break;
                    case 'T':
                        security.setType(l.substring(1));
                        break;
                    case 'G':
                        security.setGoal((l.substring(1)));
                        break;
                    default:
                        return null;
                }
            }
            return security;
        }
    }

    static class Account {
        private String mName;  // name of the account
        private String mType;  // Type of the account
        private BigDecimal mSalesTaxRate; // sales tax rate for tax account
        private String mDescription; // description of the account
        private BigDecimal mCreditLimit;  // for credit card account
        private BigDecimal mBalance; // account balance

        // default constructor set members to default values
        public Account(String name, String type) {
            mName = name;
            mType = type;
            mSalesTaxRate = BigDecimal.ZERO;
            mDescription = "";
            mCreditLimit = BigDecimal.ZERO;
            mBalance = BigDecimal.ZERO;
        }

        // setters and getters
        void setName(String n) { mName = n; }
        String getName() { return mName; }
        void setType(String t) { mType = t; }
        String getType() { return mType; }
        void setSalesTaxRate(BigDecimal r) { mSalesTaxRate = r; }
        BigDecimal getSalesTaxRate() { return mSalesTaxRate; }
        void setDescription(String d) { mDescription = d; }
        String getDescription() { return mDescription; }
        void setCreditLimit(BigDecimal c) { mCreditLimit = c; }
        BigDecimal getCreditLimit() { return mCreditLimit; }
        void setBalance(BigDecimal b) { mBalance = b; }
        BigDecimal getBalance() { return mBalance; }

        static Account fromQIFLines(List<String> lines) {
            Account account = new Account("", "");
            for (String l : lines) {
                switch (l.charAt(0)) {
                    case 'N':
                        account.setName(l.substring(1));
                        break;
                    case 'T':
                        account.setType(l.substring(1));
                        break;
                    case 'R':
                        account.setSalesTaxRate(new BigDecimal(l.substring(1)));
                        break;
                    case 'D':
                        account.setDescription(l.substring(1));
                        break;
                    case 'L':
                        account.setCreditLimit(new BigDecimal(l.substring(1).replace(",","")));
                        break;
                    default:
                        // bad formatted record, return null
                        return null;
                }
            }
            return account;
        }
    }

    static class BankTransaction {
        // split bank transaction
        static class SplitBT {
            private String mCategory; // split category
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
        private char mCleared;  // 0 for not present, 1 for *, 2 for X
        private String mCheckNumber; // check number or ref, such as ATM, etc, so string is used
        private String mPayee;
        private String mMemo;
        private List<String> mAddressList; // QIF says up to 6 lines.
        private String mCategory; // L line if matches [*], then transfer, otherwise, category
        private List<SplitBT> mSplitList;
        private String[] mAmortizationLines;

        // default constructor
        BankTransaction() {
            mCleared = ' ';
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
        void setCleared(char c) { mCleared = c; }
        void setReference(String r) { mCheckNumber = r; }
        void setPayee(String p) { mPayee = p; }
        void setMemo(String m) { mMemo = m; }
        void addAddress(String a) { mAddressList.add(a); }
        void setCategory(String c) { mCategory = c; }
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
        int getCleared() { return mCleared; }
        String getCategoryOrTransfer() { return mCategory; }
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
                        bt.setCleared(l.charAt(1));
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
                        bt.setCategory(l.substring(1));
                        break;
                    case 'S':
                        if (splitBT != null) {
                            bt.addSplit(splitBT);
                        }
                        splitBT = new SplitBT();
                        splitBT.setCategory(l.substring(1));
                        break;
                    case 'E':
                        if (splitBT == null) {
                            System.err.println("Bad formatted BankTransactionSplit " +
                                    lines.toString());
                            return null;
                        } else {
                            splitBT.setMemo(l.substring(1));
                        }
                        break;
                    case '$':
                        if (splitBT == null) {
                            System.err.println("Bad formatted BankTransactionSplit " +
                                    lines.toString());
                            return null;
                        } else {
                            splitBT.setAmount(new BigDecimal(l.substring(1).replace(",","")));
                        }
                        break;
                    case 'F':
                        System.err.println("F flag in BankTransaction not implemented "
                                + lines.toString());
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
                        System.err.println("BankTransaction Offending line: " + l);
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
            STKSPLIT, XIN, XOUT, DEPOSIT, WITHDRAW //, WITHDRWX
        }

        private String mAccountName;
        private LocalDate mDate;
        private Action mAction;
        private String mSecurityName;
        private BigDecimal mPrice;
        private BigDecimal mQuantity;
        private char mCleared; // C line
        private String mTransferReminderText; // P line
        private String mMemo;
        private BigDecimal mCommission;
        private String mCategoryOrTransfer; // L line
        private BigDecimal mTAmount; // not sure what's the difference between
        private BigDecimal mUAmount; // T and U amounts
        private BigDecimal mAmountTransferred; // $ line

        TradeTransaction() {
            mCleared = ' ';
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
        void setCleared(char c) { mCleared = c; }
        void setTransferReminderText(String t) { mTransferReminderText = t; }
        void setMemo(String m) { mMemo = m; }
        void setCommission(BigDecimal c) { mCommission = c; }
        void setCategoryOrTransfer(String ct) { mCategoryOrTransfer = ct; }
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
        int getCleared() { return mCleared; }
        String getCategoryOrTransfer() { return mCategoryOrTransfer; }
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
                        tt.setCleared(l.charAt(1));
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
                        // todo this is for diagonose only, should be removed later
                        if (actionStr == null || actionStr.startsWith("MISC"))
                            System.out.println(lines);
                        String[] tokens = l.substring(1).split("\\|");
                        if (tokens.length > 1) {
                            System.err.println(lines);
                            System.err.println("Multiple tokens seen at Category line: " + l +
                                    ", importing as " + tokens[tokens.length-1]);
                        }
                        tt.setCategoryOrTransfer(tokens[tokens.length-1]);
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
                        System.err.println("TradeTransaction: Offending line: " + l);
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
                        actionStr = "XIN";
                        break;
                    case "WITHDRWX":
                        actionStr = "XOUT";
                        break;
                }
                tt.setAction(Action.valueOf(actionStr));
            }
            return tt;
        }
    }

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

    static class Price {
        private String mSecurity;
        private LocalDate mDate;
        private BigDecimal mPrice;

        // setters
        void setSecurity(String s) { mSecurity = s; }
        void setDate(LocalDate d) { mDate = d; }
        void setPrice(BigDecimal p) { mPrice = p; }
        String getSecurity() { return mSecurity; }
        LocalDate getDate() { return mDate; }
        BigDecimal getPrice() { return mPrice; }


        static Price fromQIFLines(List<String> lines) {
            if (lines.size() > 1) {
                System.err.println("Price record, expected 1 line, got " + lines.size());
                return null;
            }
            String tokens[] = lines.get(0).split(",");
            if (tokens.length != 3) {
                System.err.println("Expect 3 ',' separated fields, got " + tokens.length);
                return null;
            }
            Price price = new Price();
            price.setSecurity(tokens[0].replace("\"", ""));
            price.setDate(parseDate(tokens[2].replace("\"", "").trim()));

            // the actual price has two possible formats:
            // decimal, xxx.yyyy
            // fraction [x ]y/z
            int idx0 = tokens[1].indexOf('/');
            if (idx0 == -1) {
                // not a fraction
                if (tokens[1].length() > 0) {
                    price.setPrice(new BigDecimal(tokens[1]));
                } else {
                    price.setPrice(BigDecimal.ZERO);
                }
            } else {
                int whole, num, den, idx1;
                den = Integer.valueOf(tokens[1].substring(idx0 + 1));
                idx1 = tokens[1].indexOf(' ');
                if (idx1 == -1) {
                    // no space
                    whole = 0;
                    num = Integer.valueOf(tokens[1].substring(0,idx0));
                } else {
                    whole = Integer.valueOf(tokens[1].substring(0, idx1));
                    num = Integer.valueOf(tokens[1].substring(idx1+1, idx0));
                }
                price.setPrice((new BigDecimal(whole)).add((new BigDecimal(num)).divide(new BigDecimal(den),
                        MainApp.PRICE_FRACTION_LEN, RoundingMode.HALF_UP)));
            }
            return price;
        }
    }

    private String mDefaultAccountName;
    private List<Account> mAccountList;
    private List<Category> mCategoryList;
    private List<Security> mSecurityList;
    private List<BankTransaction> mBankTransactionList;
    private List<TradeTransaction> mTradeTransactionList;
    private List<MemorizedTransaction> mMemorizedTransactionList;
    private List<Price> mPriceList;

    private void matchTransferTransaction() {
        // TODO: 4/6/16
        // seems more work is needed here
        List<BankTransaction> btList = getBankTransactionList();
        List<TradeTransaction> ttList = getTradeTransactionList();

        Iterator<BankTransaction> btIterator = btList.iterator();
        System.out.println("Number of bt = " + btList.size());
        //while (btIterator.hasNext()) {
            //System.out.println("bt " + btIterator.next());
        //}

        Iterator<TradeTransaction> ttIterator = ttList.iterator();
        System.out.println("Number of tt = " + ttList.size());
        //while (ttIterator.hasNext()) {
        //    System.out.println("tt " + ttIterator.next());
        //}

    }

    // public constructor
    QIFParser(String dan) {
        mDefaultAccountName = dan;
        mAccountList = new ArrayList<>();
        mCategoryList = new ArrayList<>();
        mSecurityList = new ArrayList<>();
        mBankTransactionList = new ArrayList<>();
        mTradeTransactionList = new ArrayList<>();
        mMemorizedTransactionList = new ArrayList<>();
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
    int parseFile(File qif) throws IOException {
        List<String> allLines = Files.readAllLines(qif.toPath());
        int nLines = allLines.size();
        if (nLines == 0)
            return 0;

        // last line should either be "^" or "!Clear:AutoSwitch"
        String lastLine = allLines.get(nLines-1);
        if (!lastLine.equals("^") && !lastLine.equals("!Clear:AutoSwitch")) {
            throw new IOException("Bad formatted file");
        }

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
        // set boolean autoSwitch to be true at start, do nothing when encounts Option!AutoSwitch
        // set autoSwitch to be false when encounts Clear:AutoSwitch.
        boolean autoSwitch = true;
        RecordType currentRecordType = null;
        int i = 0;
        Account account = null;
        while (i < nLines) {
            String line = allLines.get(i);
            switch (line) {
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
                    int j = findNextMatch(allLines, i, "^");
                    if (j == -1) {
                        System.err.println("Bad formatted file.  Can't find '^'");
                        return -1;
                    }
                    switch (currentRecordType) {
                        case CAT:
                            Category category = Category.fromQIFLines(allLines.subList(i, j));
                            if (category != null) {
                                mCategoryList.add(category);
                            } else {
                                System.err.println("Bad formatted Category text: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        case ACCOUNT:
                            account = Account.fromQIFLines(allLines.subList(i, j));
                            if (account != null) {
                                if (autoSwitch) {
                                    mAccountList.add(account);
                                } else {
                                    // todo
                                    // what should I do here?
                                }
                            } else {
                                System.err.println("Bad formatted Account record: "
                                        + allLines.subList(i,j).toString());
                            }
                            i = j;
                            break;
                        case SECURITY:
                            Security security = Security.fromQIFLines(allLines.subList(i,j));
                            if (security != null) {
                                mSecurityList.add(security);
                            } else {
                                System.err.println("Bad formatted Security record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case BANK:
                            BankTransaction bt = BankTransaction.fromQIFLines(allLines.subList(i,j));
                            if (bt != null) {
                                if (account != null) {
                                    bt.setAccountName(account.getName());
                                } else {
                                    bt.setAccountName(getDefaultAccountName());
                                }
                                mBankTransactionList.add(bt);
                            } else {
                                System.err.println("Bad formatted BankTransaction record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case INVITEM:
                            TradeTransaction tt = TradeTransaction.fromQIFLines(allLines.subList(i,j));
                            if (tt != null) {
                                if (account != null) {
                                    tt.setAccountName(account.getName());
                                } else {
                                    tt.setAccountName(getDefaultAccountName());
                                }
                                mTradeTransactionList.add(tt);
                            } else {
                                System.err.println("Bad formatted TradeTransaction record: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case MEMORIZED:
                            MemorizedTransaction mt = MemorizedTransaction.fromQIFLines(allLines.subList(i,j));
                            if (mt != null) {
                                mMemorizedTransactionList.add(mt);
                            } else {
                                System.err.println("Bad formatted MemorizedTransaction: "
                                        + allLines.subList(i,j));
                            }
                            i = j;
                            break;
                        case PRICES:
                            Price price = Price.fromQIFLines(allLines.subList(i,j));
                            if (price != null) {
                                mPriceList.add(price);
                            } else {
                                System.err.println("Bad formatted Price record: "
                                        + allLines.subList(i, j));
                            }
                            i = j;
                            break;
                        default:
                            System.err.println(currentRecordType.toString() + " Not implemented yet");
                            return -1;
                    }
                    break;  // break out the switch
            }
            i++;
        }

        // done with the file, now match Transfer Transactions
        matchTransferTransaction();
        return 0;
    }

    List<Account> getAccountList() { return mAccountList; }
    List<Security> getSecurityList() { return mSecurityList; }
    List<Category> getCategoryList() { return mCategoryList; }
    List<Price> getPriceList() { return mPriceList; }
    List<BankTransaction> getBankTransactionList() { return mBankTransactionList; }
    List<TradeTransaction> getTradeTransactionList() { return mTradeTransactionList; }
    private String getDefaultAccountName() { return mDefaultAccountName; }
}
