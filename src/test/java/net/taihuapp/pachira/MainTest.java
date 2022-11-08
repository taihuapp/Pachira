/*
 * Copyright (C) 2018-2022.  Guangliang He.  All Rights Reserved.
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

import net.taihuapp.pachira.dao.DaoException;
import net.taihuapp.pachira.dao.DaoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static net.taihuapp.pachira.Transaction.TradeAction.*;
import static org.junit.jupiter.api.Assertions.fail;

public class MainTest {
    @BeforeEach
    void setup() { System.out.println("MainTest setup"); }

    @AfterEach
    void tearDown() { System.out.println("MainTest tearDown"); }

    @Test
    void testRegEx() {
        final Pattern currencyPattern = RegExUtil.getCurrencyInputRegEx(Currency.getInstance("USD"), false);
        // these are strings should be accepted
        for (String s : List.of("", "0", "0.", "0.12", "123,456.78")) {
            if (!currencyPattern.matcher(s).matches())
                fail("'" + s + "' should be accepted but rejected");
        }

        // these are strings should be rejected
        for (String s : List.of("0,.3", "0.1a", "123.456", "0.12.345")) {
            if (currencyPattern.matcher(s).matches())
                fail("'" + s + "' should be rejected but accepted");
        }
    }

    @Test
    void myTest() {
        try {
            // create a database
            final String dbPostfix = DaoManager.getDBPostfix();
            final Path dbFilePath = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")),
                    "PachiraMainTest", dbPostfix);
            final String dbFileName = dbFilePath.toString();  // with postfix
            MainModel mainModel = new MainModel(dbFileName.substring(0, dbFileName.length()-dbPostfix.length()),
                    "11111111", true);

            // add two securities for testing
            mainModel.mergeSecurity(new Security(-1, "A", "Test Stock A", Security.Type.STOCK));
            mainModel.mergeSecurity(new Security(-1, "A1", "New Class for Test Stock A",
                    Security.Type.STOCK));
            mainModel.mergeSecurity(new Security(-1, "Z", "Test Stock Z", Security.Type.STOCK));
            assert(mainModel.getSecurityList().size() == 3);
            mainModel.getSecurityList().forEach(s -> System.out.println(s.getID() + " " + s.getTicker()));

            // create an investment account
            final Account account = new Account(-1, Account.Type.BROKERAGE, "Brokerage",
                    "Test brokerage account", false, Integer.MAX_VALUE, null, BigDecimal.ZERO);
            mainModel.insertUpdateAccount(account);
            final Optional<Account> accountOptional = mainModel.getAccount(a -> a.getID() == account.getID());
            assert(accountOptional.isPresent());

            // add a couple of securities
            final Security securityA = mainModel.getSecurity(s -> s.getTicker().equals("A")).orElse(null);
            assert(securityA != null);

            final Security securityZ = mainModel.getSecurity(s -> s.getTicker().equals("Z")).orElse(null);
            assert(securityZ != null);

            // start trading
            final int aid = account.getID();

            // on 1/1/2022, deposit 4794.45 to account
            final Transaction d0 = new Transaction(-1, aid, LocalDate.of(2022, 1, 1),
                    null, DEPOSIT, Transaction.Status.UNCLEARED, "", "", "Deposit",
                    null, null, "initial deposit",
                    null, null, new BigDecimal("4794.45"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, d0, new ArrayList<>());

            // On 1/5/2022, buy 100 Z with $10 commission, total 1010
            final BigDecimal badQuantity = BigDecimal.valueOf(200);
            final Transaction z0 = new Transaction(-1, aid, LocalDate.of(2022, 1, 5),
                    null, BUY, Transaction.Status.UNCLEARED, securityZ.getName(), "", "",
                    badQuantity, null, "",
                    new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("1010"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, z0, new ArrayList<>());

            // testing update
            final Transaction z0Copy = new Transaction(z0);
            final BigDecimal quantity = BigDecimal.valueOf(100);
            z0Copy.setQuantity(quantity);
            mainModel.alterTransaction(z0, z0Copy, new ArrayList<>());

            // we should see 3 entries for the security holding list on 1/6/2022
            assert(mainModel.computeSecurityHoldings(account.getTransactionList(),
                    LocalDate.of(2022, 1, 6), -1).size() == 3);

            // On 1/5/2022, buy 20 A, $3.45 commission, total 2003.45
            final Transaction t0 = new Transaction(-1, aid, LocalDate.of(2022, 1, 5),
                    null, BUY, Transaction.Status.UNCLEARED, securityA.getName(), "", "",
                    new BigDecimal("20"), null, "",
                    new BigDecimal("3.45"), BigDecimal.ZERO, new BigDecimal("2003.45"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, t0, new ArrayList<>());

            // On 1/7/2022, buy 0 A, $3 commission, total 3.00
            final Transaction t1 = new Transaction(-1, aid, LocalDate.of(2022, 1, 7),
                    null, BUY, Transaction.Status.UNCLEARED, securityA.getName(), "", "",
                    BigDecimal.ZERO, null, "",
                    new BigDecimal("3"), BigDecimal.ZERO, new BigDecimal("3"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, t1, new ArrayList<>());

            // On 1/9/2022, buy 25 A with $3 commission, total $2528
            final Transaction t2 = new Transaction(-1, aid, LocalDate.of(2022, 1, 9),
                    null, BUY, Transaction.Status.UNCLEARED, securityA.getName(), "", "",
                    new BigDecimal("25"), null, "",
                    new BigDecimal("3"), BigDecimal.ZERO, new BigDecimal("2528"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, t2, new ArrayList<>());

            // On 1/9/2022
            // sell 8 of A, $1 commission, total $750
            // match 1 on the lot of t0, 1/5/2022
            // match 0 on the lot of t1, 1/7
            // match 7 on the lot of t2
            BigDecimal q = new BigDecimal("8");
            BigDecimal c = new BigDecimal("1");
            BigDecimal amount = new BigDecimal("750");
            final List<MatchInfo> matchInfoList = new ArrayList<>();
            matchInfoList.add(new MatchInfo(t0.getID(), BigDecimal.ONE));
            matchInfoList.add(new MatchInfo(t1.getID(), BigDecimal.ZERO));
            matchInfoList.add(new MatchInfo(t2.getID(), new BigDecimal("7")));
            final Transaction s0 = new Transaction(-1, aid, LocalDate.of(2022, 1, 9),
                    null, SELL, Transaction.Status.UNCLEARED, securityA.getName(), "", "",
                    q, null, "",
                    c, BigDecimal.ZERO, amount,
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, s0, matchInfoList);

            // add a stock split transaction for Z
            final Transaction zSplit = new Transaction(-1, aid, LocalDate.of(2022, 1, 15),
                    null, STKSPLIT, Transaction.Status.UNCLEARED, securityZ.getName(), "", "",
                    new BigDecimal("2"), new BigDecimal("1"), "2 for 1 split",
                    null, BigDecimal.ZERO, new BigDecimal("110"),
                    0, -1, -1, -1, new ArrayList<>(), "");
            mainModel.alterTransaction(null, zSplit, new ArrayList<>());

            // we should have the following transactions
            // d0, z0, t0, t1, t2, s0, zSplit
            assert(account.getTransactionList().size() == 7);

            // compute holdings for 1/8/2022
            final List<SecurityHolding> securityHoldingList0 =
                    mainModel.computeSecurityHoldings(account.getTransactionList(),
                            LocalDate.of(2022, 1, 8), -1);
            assert(securityHoldingList0.size() == 4); // 4 security holdings, A, Z, Cash, Total
            final Optional<SecurityHolding> securityHoldingAOptional = securityHoldingList0.stream()
                    .filter(sh -> sh.getSecurityName().equals(securityA.getName())).findAny();
            assert(securityHoldingAOptional.isPresent());
            final SecurityHolding securityHoldingA = securityHoldingAOptional.get();
            assert(securityHoldingA.getSecurityLotList().size() == 2); // two lots
            assert(securityHoldingA.getSecurityLotList().get(1).getQuantity().signum() == 0); // the 0 lot
            assert(securityHoldingA.getSecurityLotList().get(1).getCostBasis().signum() > 0); // 0 lot with cost basis

            // compute holdings for 1/9/2022, exclude s0
            // we should not see Cash entry
            final List<SecurityHolding> securityHoldingList1 =
                    mainModel.computeSecurityHoldings(account.getTransactionList(),
                            LocalDate.of(2022, 1, 9), s0.getID());
            assert(securityHoldingList1.size() == 4);  // A, Z, cash, total
            assert(securityHoldingList1.stream().anyMatch(sh -> sh.getSecurityName().equals(SecurityHolding.TOTAL)));
            assert(securityHoldingList1.stream().anyMatch(sh -> sh.getSecurityName().equals(SecurityHolding.CASH)));
            final Optional<SecurityHolding> shAOptional1 = securityHoldingList1.stream()
                    .filter(sh -> sh.getSecurityName().equals(securityA.getName())).findAny();
            assert(shAOptional1.isPresent());
            // we expect to see a zero lot with non-zero cost basis
            assert(shAOptional1.get().getSecurityLotList().stream()
                    .anyMatch(l -> (l.getQuantity().signum() == 0) && (l.getCostBasis().signum() > 0)));

            // compute holdings for 1/9/2022, including s0
            final List<SecurityHolding> securityHoldingList2 =
                    mainModel.computeSecurityHoldings(account.getTransactionList(), s0.getTDate(), -1);
            assert(securityHoldingList2.size() == 3); // A, Z, total
            assert(securityHoldingList2.stream().anyMatch(sh -> sh.getSecurityName().equals(SecurityHolding.TOTAL)));
            assert(securityHoldingList2.stream().noneMatch(sh -> sh.getSecurityName().equals(SecurityHolding.CASH)));
            final Optional<SecurityHolding> shAOptional2 = securityHoldingList2.stream()
                    .filter(sh -> sh.getSecurityName().equals(securityA.getName())).findAny();
            assert(shAOptional2.isPresent());
            // we don't expect to see a zero lot with non-zero cost basis
            assert(shAOptional2.get().getSecurityLotList().stream()
                    .noneMatch(l -> (l.getQuantity().signum() == 0) && (l.getCostBasis().signum() > 0)));
            final Optional<SecurityHolding> shZOptional2 = securityHoldingList2.stream()
                    .filter(sh -> sh.getSecurityName().equals(securityZ.getName())).findAny();
            assert(shZOptional2.isPresent());
            assert(shZOptional2.get().getQuantity().compareTo(quantity)== 0);

            final List<SecurityHolding> securityHoldingList3 =
                    mainModel.computeSecurityHoldings(account.getTransactionList(),
                            LocalDate.of(2022, 1, 31), -1);
            final Optional<SecurityHolding> shZOptional3 = securityHoldingList3.stream()
                    .filter(sh -> sh.getSecurityName().equals(securityZ.getName())).findAny();
            assert(shZOptional3.isPresent());
            assert(shZOptional3.get().getQuantity().compareTo(BigDecimal.valueOf(200))== 0);
        } catch (DaoException | ModelException | IOException e) {
            fail(e.toString());
        }
    }
}
