/*
 * Copyright (C) 2018-2024.  Guangliang He.  All Rights Reserved.
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

package net.taihuapp.pachira.dao;

import java.sql.SQLException;

public class DaoException extends Exception {
    enum ErrorCode {
        FAIL_TO_INSERT, FAIL_TO_DELETE, FAIL_TO_UPDATE, FAIL_TO_GET, FAIL_TO_MERGE,
        FAIL_TO_OPEN_CONNECTION, FAIL_TO_CLOSE_CONNECTION,
        FAIL_TO_GET_CONNECTION_METADATA, FAIL_TO_BACKUP,
        FAIL_TO_CHANGE_PASSWORD, FAIL_RUN_SCRIPT,
        FAIL_TO_SET_AUTOCOMMIT, FAIL_TO_ROLLBACK,
        DB_DRIVER_NOT_FOUND, MISSING_DB_VERSION,
        DB_FILE_NOT_FOUND, BAD_PASSWORD,
        FAIL_TO_UPDATE_DB, UNIQUENESS_VIOLATED
    }

    private final ErrorCode errorCode;

    static String SQLExceptionToString(SQLException e) {
        StringBuilder s = new StringBuilder();
        while (e != null) {
            s.append("--- SQLException ---" + "  SQL State: ").append(e.getSQLState())
                    .append("  Message:   ").append(e.getMessage()).append(System.lineSeparator());
            e = e.getNextException();
        }
        return s.toString();
    }

    public DaoException(ErrorCode errorCode, String msg, Throwable throwable) {
        super(msg, throwable);
        this.errorCode = errorCode;
    }

    public DaoException(ErrorCode errorCode, String msg) {
        super(msg);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }

    public String toString() {
        String string = "DaoException " + getErrorCode() + System.lineSeparator();
        Throwable cause = getCause();
        if (cause == null) {
            string += "'null'";
        } else if (cause instanceof SQLException)
            string += SQLExceptionToString((SQLException) cause);
        else
            string += cause.toString();
        return string;
    }

    public static DaoException fromSQLException(SQLException sqlException) {
        final DaoException.ErrorCode errorCode;
        final String errMsg;
        switch (sqlException.getErrorCode()) {
            case org.h2.api.ErrorCode.DATABASE_NOT_FOUND_1: //
            case org.h2.api.ErrorCode.DATABASE_NOT_FOUND_WITH_IF_EXISTS_1:
                errorCode = DaoException.ErrorCode.DB_FILE_NOT_FOUND;
                errMsg = "Database file not found";
                break;
            case org.h2.api.ErrorCode.FILE_ENCRYPTION_ERROR_1:
            case org.h2.api.ErrorCode.WRONG_PASSWORD_FORMAT:
            case org.h2.api.ErrorCode.WRONG_USER_OR_PASSWORD:
                errorCode = DaoException.ErrorCode.BAD_PASSWORD;
                errMsg = "Wrong password";
                break;
            case org.h2.api.ErrorCode.DATABASE_ALREADY_OPEN_1: // 90020
                errorCode = DaoException.ErrorCode.FAIL_TO_OPEN_CONNECTION;
                errMsg = "Database is opened by other process.  Please close the other process";
                break;
            case org.h2.api.ErrorCode.DUPLICATE_KEY_1: // 23505
                errorCode = DaoException.ErrorCode.UNIQUENESS_VIOLATED;
                errMsg = "Unique index or primary key violation";
                break;
            default:
                errorCode = DaoException.ErrorCode.FAIL_TO_OPEN_CONNECTION;
                errMsg = "Failed to open database";
                break;
        }
        return new DaoException(errorCode, errMsg, sqlException);
    }
}
