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

import com.webcohesion.ofx4j.domain.data.banking.AccountType;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountInfo;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponse;
import com.webcohesion.ofx4j.domain.data.common.BalanceInfo;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionList;
import com.webcohesion.ofx4j.domain.data.common.TransactionType;
import com.webcohesion.ofx4j.io.DefaultHandler;
import com.webcohesion.ofx4j.io.OFXParseException;
import com.webcohesion.ofx4j.io.nanoxml.NanoXMLOFXReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class OFXBankStatementReader {

    private static final Logger mLogger = LogManager.getLogger(OFXBankStatementReader.class);
    private String mWarning;

    static Date parseOFXDateTime(String dtString) {
        // According to OFX spec p89 on https://www.ofx.net/downloads/OFX%202.2.pdf
        // The complete date time form is YYYYMMDDHHMMSS.XXX[gmt offset[:tz name]]
        // But fields from right can be omitted.  If time zone info is missing, default is GMT
        // If time is missing, default is 12:00 AM
        // So any of the following are legit form
        // YYYYMMDD
        // YYYYMMDDHHMMSS
        // YYYYMMDDHHMMSS.XXX

        // first see if time zone info is in the dtString
        TimeZone TZ = TimeZone.getTimeZone("GMT");  // default GMT
        int parseLen = dtString.length();
        if (dtString.endsWith("]")) {
            // we have time zone info in a braket
            int braPos = dtString.indexOf('[');
            int endPos = dtString.indexOf(':');
            if (endPos < 0) // no :
                endPos = dtString.length();
            int tzOffset = Integer.parseInt(dtString.substring(braPos+1, endPos-1));
            TZ.setRawOffset(tzOffset*60*60*1000);
            parseLen = braPos-1;
        }

        final String fullFormat = "yyyyMMddHHmmss.SSS"; // without time zone
        SimpleDateFormat sdf = new SimpleDateFormat(fullFormat.substring(0, parseLen));
        sdf.setTimeZone(TZ);

        Date date = null;
        try {
            date = sdf.parse(dtString.substring(0, parseLen));
        } catch (ParseException e) {
            mLogger.error("OFXDate parse problem with input " + dtString, e);
        }
        return date;
    }

    public BankStatementResponse readOFXStatement(InputStream is) throws IOException, OFXParseException {
        final NanoXMLOFXReader reader = new NanoXMLOFXReader();
        final Stack<Map<String, List<Object>>> aggregateStack = new Stack<>();
        final Map<String, List<String>> headers = new HashMap<>();
        final TreeMap<String, List<Object>> root = new TreeMap<>();
        aggregateStack.push(root);

        reader.setContentHandler(getNewDefaultHandler(headers, aggregateStack));
        reader.parse(is);

        String currencyCode, acctID, acctType, bankID;
        TransactionList transactionList = new TransactionList();
        BalanceInfo ledgerBalance = new BalanceInfo();

        TreeMap<String, List<Object>> ofxMap, bankMsgMap, stmttrnMap, stmtMap, bankAcctMap;
        TreeMap<String, List<Object>> bankTranListMap, ledgerBalMap;

        ofxMap = (TreeMap<String, List<Object>>) root.get("OFX").get(0);
        if (ofxMap.containsKey("BANKMSGSRSV1"))
            bankMsgMap = (TreeMap<String, List<Object>>) ofxMap.get("BANKMSGSRSV1").get(0);
        else
            bankMsgMap = (TreeMap<String, List<Object>>) ofxMap.get("BANKMSGSRSV2").get(0);

        stmttrnMap = (TreeMap<String, List<Object>>) bankMsgMap.get("STMTTRNRS").get(0);
        stmtMap = (TreeMap<String, List<Object>>) stmttrnMap.get("STMTRS").get(0);
        currencyCode = (String) stmtMap.get("CURDEF").get(0);

        bankAcctMap = (TreeMap<String, List<Object>>) stmtMap.get("BANKACCTFROM").get(0);
        acctID = (String) bankAcctMap.get("ACCTID").get(0);
        acctType = (String) bankAcctMap.get("ACCTTYPE").get(0);
        bankID = (String) bankAcctMap.get("BANKID").get(0);

        bankTranListMap = (TreeMap<String, List<Object>>) stmtMap.get("BANKTRANLIST").get(0);
        String dtStart, dtEnd;
        dtStart = (String) bankTranListMap.get("DTSTART").get(0);
        dtEnd = (String) bankTranListMap.get("DTEND").get(0);
        List<Object> stmttrnList = bankTranListMap.get("STMTTRN");
        List<Transaction> transactions = new ArrayList<>();
        transactionList.setTransactions(transactions);
        transactionList.setStart(parseOFXDateTime(dtStart));
        transactionList.setEnd(parseOFXDateTime(dtEnd));

        Set<String> unimplementedTags = new HashSet<>();
        for (Object o : stmttrnList) {
            Transaction t = new Transaction();
            transactions.add(t);
            for (String keyString : ((TreeMap<String, List<Object>>) o).keySet()) {
                String valueString = (String) ((TreeMap<String, List<Object>>)o).get(keyString).get(0);
                switch (keyString) {
                    case "DTPOSTED":
                    case "DTUSER":
                        t.setDatePosted(parseOFXDateTime(valueString));
                        break;
                    case "FITID":
                        t.setId(valueString);
                        break;
                    case "NAME":
                        t.setName(valueString);
                        break;
                    case "TRNAMT":
                        t.setBigDecimalAmount(new BigDecimal(valueString));
                        break;
                    case "TRNTYPE":
                        t.setTransactionType(TransactionType.valueOf(valueString));
                        break;
                    case "MEMO":
                        t.setMemo(valueString);
                        break;
                    case "CHECKNUM":
                        t.setCheckNumber(valueString);
                        break;
                    default:
                        unimplementedTags.add(keyString);
                }
            }
        }

        if (!unimplementedTags.isEmpty()) {
            StringBuilder stringBuilder = new StringBuilder("The following tags are not implemented:\n");
            for (String s : unimplementedTags) {
                stringBuilder.append("  ").append(s).append("\n");
            }
            mWarning = stringBuilder.toString();
        } else {
            mWarning = null;
        }

        BankAccountDetails accountDetails = new BankAccountDetails();
        accountDetails.setAccountNumber(acctID);
        accountDetails.setAccountType(AccountType.valueOf(acctType));
        accountDetails.setBankId(bankID);

        BankAccountInfo accountInfo = new BankAccountInfo();
        accountInfo.setBankAccount(accountDetails);

        ledgerBalMap = (TreeMap<String, List<Object>>) stmtMap.get("LEDGERBAL").get(0);
        ledgerBalance.setAsOfDate(parseOFXDateTime((String) ledgerBalMap.get("DTASOF").get(0)));
        ledgerBalance.setAmount(Double.parseDouble((String) ledgerBalMap.get("BALAMT").get(0)));

        BankStatementResponse statement = new BankStatementResponse();
        statement.setAccount(accountDetails);
        statement.setCurrencyCode(currencyCode);
        statement.setLedgerBalance(ledgerBalance);
        statement.setTransactionList(transactionList);

        // build AccountStatement object here
        return statement;
    }

    private DefaultHandler getNewDefaultHandler(final Map<String, List<String>> headers,
                                                final Stack<Map<String, List<Object>>> aggregateStack) {
        return new DefaultHandler() {
            @Override
            public void onHeader(String name, String value) {
                mLogger.debug(name + ":" + value);
                List<String> list = headers.computeIfAbsent(name, k -> new ArrayList<>());
                list.add(value);
            }

            @Override
            public void onElement(String name, String value) {
                mLogger.debug("onElement " + aggregateStack.size());
                char[] tabs = new char[aggregateStack.size()*2];
                Arrays.fill(tabs, ' ');
                mLogger.debug(new String(tabs) + name + "=" + value);

                List<Object> list = aggregateStack.peek().computeIfAbsent(name, k -> new ArrayList<>());
                list.add(value);
            }

            @Override
            public void startAggregate(String aggregateName) {
                mLogger.debug("startAggregate " + aggregateName + " " + aggregateStack.size());
                char[] tabs = new char[aggregateStack.size()*2];
                Arrays.fill(tabs, ' ');
                mLogger.debug(new String(tabs) + aggregateName + " {");

                TreeMap<String, List<Object>> aggregate = new TreeMap<>();
                List<Object> list = aggregateStack.peek().computeIfAbsent(aggregateName, k -> new ArrayList<>());
                list.add(aggregate);
                aggregateStack.push(aggregate);
            }

            @Override
            public void endAggregate(String aggregateName) {
                mLogger.debug("endAggregate " + aggregateName + " " + aggregateStack.size());
                aggregateStack.pop();

                char[] tabs = new char[aggregateStack.size()*2];
                Arrays.fill(tabs, ' ');
                mLogger.debug(new String(tabs) + "}");
            }
        };
    }

    public String getWarning() { return mWarning; }
}
