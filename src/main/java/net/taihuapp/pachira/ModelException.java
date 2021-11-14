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

/**
 * Model related exception class
 */
public class ModelException extends Exception {
    enum ErrorCode {
        /**
         * failure in setting up key store
         */
        FAIL_TO_SETUP_KEYSTORE,
        /**
         * the input transaction is not valid
         */
        INVALID_TRANSACTION,
        /**
         * the lotInfo is not valid
         */
        INVALID_LOT_INFO,
        /**
         * the DirectConnection is not valid
         */
        INVALID_DIRECT_CONNECTION,
        /**
         * the account doesn't have DC
         */
        ACCOUNT_NO_DC,
        /**
         * invalid account dc
         */
        INVALID_ACCOUNT_DC,
        /**
         * OFX parse exception
         */
        OFX_PARSE_EXCEPTION,
        /**
         * QIF parse exception
         */
        QIF_PARSE_EXCEPTION,
        /**
         * not finding the loan
         */
        LOAN_NOT_FOUND,
        /**
         * not find payment item on date
         */
        LOAN_PAYMENT_NOT_FOUND
    }

    private final ErrorCode errorCode;

    public ModelException(ErrorCode errorCode, String msg, Throwable throwable) {
        super(msg, throwable);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }

    public String toString() { return "ModelException " + getErrorCode() + System.lineSeparator() + getCause(); }
}
