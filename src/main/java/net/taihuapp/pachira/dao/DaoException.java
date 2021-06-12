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

package net.taihuapp.pachira.dao;

import java.sql.SQLException;

public class DaoException extends Exception {
    enum ErrorCode {
        FAIL_TO_INSERT, FAIL_TO_DELETE, FAIL_TO_UPDATE, FAIL_TO_GET, FAIL_TO_MERGE,
        FAIL_TO_OPEN_CONNECTION, FAIL_TO_CLOSE_CONNECTION,
        FAIL_TO_GET_CONNECTION_METADATA, FAIL_TO_BACKUP,
        FAIL_TO_CHANGE_PASSWORD,
        FAIL_TO_SET_AUTOCOMMIT, FAIL_TO_ROLLBACK,
        DB_DRIVER_NOT_FOUND, MISSING_DB_VERSION,
        DB_FILE_NOT_FOUND, BAD_PASSWORD
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
        if (cause instanceof SQLException)
            string += SQLExceptionToString((SQLException) cause);
        else
            string += cause.toString();
        return string;
    }
}
