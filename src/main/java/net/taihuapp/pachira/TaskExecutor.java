/*
 * Copyright (C) 2018-2020.  Guangliang He.  All Rights Reserved.
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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskExecutor {

    private final ScheduledExecutorService mExecutorService;

    public TaskExecutor(int poolSize) {
        mExecutorService = Executors.newScheduledThreadPool(poolSize);
    }

    // execute a runnable at a delay
    public void schedule(Runnable runnable, long delay, TimeUnit unit) {
        mExecutorService.schedule(runnable, delay, unit);
    }

    // schedule a runnable to run at minute m of every hour
    public void scheduleHourly(Runnable runnable, int m) {
        if (m < 0 || m > 59)
            throw new IllegalArgumentException();

        mExecutorService.schedule(() -> {
            runnable.run();
            scheduleHourly(runnable, m);
        }, toNextExecution(m), TimeUnit.MILLISECONDS);
    }

    // schedule a runnable to run at given hour:minute everyday
    public void scheduleDaily(Runnable runnable, int m, int h) {
        if (m < 0 || m > 59 || h < 0 || h > 23)
            throw new IllegalArgumentException();

        mExecutorService.schedule(() -> {
            runnable.run();
            scheduleDaily(runnable, m, h);
        }, toNextExecution(m, h), TimeUnit.MILLISECONDS);
    }

    // compute delay to next hourly execution in milliseconds
    long toNextExecution(int m) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextExecution = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(m);
        if (nextExecution.compareTo(now) < 0)
            nextExecution = nextExecution.plusHours(1);
        Duration between = Duration.between(now, nextExecution);
        return between.getSeconds()*1000 + between.getNano()/1000000;
    }

    // compute delay to next daily execution in milliseconds
    long toNextExecution(int m, int h) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextExecution = now.truncatedTo(ChronoUnit.DAYS).plusHours(h).plusMinutes(m);
        if (nextExecution.compareTo(now) < 0)
            nextExecution = nextExecution.plusDays(1);
        Duration between = Duration.between(now, nextExecution);
        return between.getSeconds()*1000 + between.getNano()/1000000;
    }

    public void shutdown() {
        mExecutorService.shutdown();
        try {
            if (!mExecutorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                mExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            mExecutorService.shutdownNow();
        }
    }
}
