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

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.taihuapp.pachira.dao.DaoException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The class for all those dialog methods
 */
public class DialogUtil {
    // no need to instantiate it
    private DialogUtil() {}

    /**
     *
     * @param prompt - information to prompt user with
     * @param mode - ENTER, NEW, CHANGE
     * @return for creating password, empty string is in [0] and the new password is in [1]
     *         for entering password, empty string is in [0] and the password is in [1]
     *         for changing password, the old password is in [0] and the new one in [1]
     * @throws IOException - when load fxml fails.
     */
    static List<String> showPasswordDialog(Stage stage, String prompt, PasswordDialogController.MODE mode)
            throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainApp.class.getResource("/view/PasswordDialog.fxml"));

        Stage dialogStage = new Stage();
        dialogStage.setTitle(prompt);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(stage);
        dialogStage.setScene(new Scene(loader.load()));
        PasswordDialogController controller = loader.getController();
        controller.setDialogStage(dialogStage);
        controller.setMode(mode);
        dialogStage.showAndWait();

        return controller.getPasswords();
    }

    static void showWarningDialog(Stage stage, String title, String header, String content) {
        final Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.initOwner(stage);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.setResizable(true);
        alert.showAndWait();
    }

    // http://code.makery.ch/blog/javafx-dialogs-official/
    static void showExceptionDialog(Stage initOwner, String title, String header, String content, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(initOwner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        if (e != null) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);

            Label label = new Label("The exception stacktrace was:");
            TextArea textArea = new TextArea(stringWriter.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
        }
        alert.setResizable(true);
        alert.showAndWait();
    }

    static void showInformationDialog(Stage stage, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // work around for non resizable alert dialog truncates message
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(480, 320);

        alert.showAndWait();
    }

    /**
     * display a confirmation dialog
     * @param title - dialog title
     * @param header - dialog header
     * @param content - dialog content
     * @return true if OK button is clicked, false otherwise
     */
    static boolean showConfirmationDialog(Stage stage, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    static void showSpecifyLotsDialog(MainModel mainModel, Stage parent, Transaction t,
                                      List<SecurityHolding.MatchInfo> matchInfoList) throws IOException, DaoException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainApp.class.getResource("/view/SpecifyLotsDialog.fxml"));

        Stage dialogStage = new Stage();
        dialogStage.setTitle("Specify Lots...");
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(parent);
        dialogStage.setScene(new Scene(loader.load()));
        SpecifyLotsDialogController controller = loader.getController();
        controller.setMainModel(mainModel, t, matchInfoList);
        dialogStage.showAndWait();
    }

    static List<SplitTransaction> showSplitTransactionsDialog(MainModel mainModel, Stage parent, int accountID,
                                                              List<SplitTransaction> stList,
                                                              BigDecimal netAmount) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainApp.class.getResource("/view/SplitTransactionsDialog.fxml"));

        Stage dialogStage = new Stage();
        dialogStage.setTitle("Split Transaction");
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(parent);
        dialogStage.setScene(new Scene(loader.load()));
        dialogStage.setUserData(false);
        SplitTransactionsDialogController controller = loader.getController();
        controller.setMainModel(mainModel, accountID, stList, netAmount);
        dialogStage.showAndWait();
        return controller.getSplitTransactionList();
    }

    // return transaction id or -1 for failure
    // The input transaction is not changed.
    static int showEditTransactionDialog(MainModel mainModel, Stage parent, Transaction transaction,
                                         List<Account> accountList, Account defaultAccount,
                                         List<Transaction.TradeAction> taList)
            throws IOException, DaoException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation((MainApp.class.getResource("/view/EditTransactionDialog.fxml")));

        Stage dialogStage = new Stage();
        dialogStage.setTitle("Enter Transaction:");
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(parent);
        dialogStage.setScene(new Scene(loader.load()));

        EditTransactionDialogControllerNew controller = loader.getController();
        controller.setMainModel(mainModel, transaction, accountList, defaultAccount, taList);
        dialogStage.showAndWait();
        return controller.getTransactionID();
    }
}
