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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateScheduleTest {

    @BeforeEach
    void setUp() { System.out.println("setup"); }

    @AfterEach
    public void tearDown() { System.out.println("tearDown"); }

    @Test
    void myTest() {
        final LocalDate s = LocalDate.of(2021, 1, 1);
        final LocalDate e = LocalDate.of(2035, 12, 31);
        boolean[] tfArray = new boolean[]{ true, false };

        for (DateSchedule.BaseUnit bu : DateSchedule.BaseUnit.values()) {
            for (int np = 1; np < 4; np++) { // number of periods
                for (int d = 0; d < 60; d++) { //
                    System.out.print(".");
                    for (boolean isDOM : tfArray) {
                        for (boolean isFwd : tfArray) {
                            LocalDate dueDate = s.plusDays(d);
                            DateSchedule dateSchedule = new DateSchedule(bu, np, dueDate, e, isDOM, isFwd);
                            String description = "BaseUnit: " + bu + ", NP = " + np
                                    + ", s = " + dateSchedule.getStartDate()
                                    + ", e = " + dateSchedule.getEndDate()
                                    + ", isDOM = " + isDOM + ", isFwd = " + isFwd;

                            while (!dueDate.isAfter(e)) {
                                LocalDate nextDueDate = dateSchedule.getNextDueDate(dueDate);
                                LocalDate prevNextDueDate = dateSchedule.getPrevDueDate(nextDueDate);
                                try {
                                    assertEquals(prevNextDueDate, dueDate);
                                } catch (AssertionError exception) {
                                    System.err.println(description);
                                    System.err.println("prev(next(" + dueDate + ")) = " + prevNextDueDate + " != "
                                            + dueDate);
                                    throw exception;
                                }

                                dueDate = nextDueDate;
                            }
                        }
                    }
                }
                System.out.println();
            }
        }
    }
}