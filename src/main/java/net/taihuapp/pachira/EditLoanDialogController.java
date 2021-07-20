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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.regex.Pattern;


public class EditLoanDialogController {

    static final DecimalFormat DOLLAR_CENT_DECIMAL_FORMAT = new DecimalFormat("#,##0.00");

    private static final Pattern dollarCentRegEx = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d{0,2})?$");
    private static final Pattern doubleRegEx = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d*)?$");
    private static final Pattern integerRegEx = Pattern.compile("^([1-9]+\\d*)?$");

    private MainModel mainModel;
    private Loan loan;

    @FXML
    private TextField nameTextField;
    @FXML
    private TextField descriptionTextField;
    @FXML
    private TextField originalAmountTextField;
    @FXML
    private TextField interestRateTextField;
    @FXML
    private ChoiceBox<Loan.Period> compoundingPeriodChoiceBox;
    @FXML
    private ChoiceBox<Loan.Period> paymentPeriodChoiceBox;
    @FXML
    private TextField numberOfPaymentsTextField;
    @FXML
    private DatePicker firstPaymentDatePicker;
    @FXML
    private TableView<Loan.PaymentItem> paymentScheduleTableView;
    @FXML
    private TableColumn<Loan.PaymentItem, Integer> seqNumTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, LocalDate> paymentDateTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, Double> principalPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, Double> interestPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, Double> balanceTableColumn;

    void setMainModel(MainModel mainModel, Loan loan) {
        this.mainModel = mainModel;
        this.loan = loan;

        nameTextField.textProperty().bindBidirectional(this.loan.getNameProperty());
        descriptionTextField.textProperty().bindBidirectional(this.loan.getDescriptionProperty());

        // allow max 2 digits after decimal point
        TextFormatter<Double> originalAmountFormatter = new TextFormatter<>(new DoubleStringConverter(), null,
                c -> dollarCentRegEx.matcher(c.getControlNewText()).matches() ? c : null);
        originalAmountTextField.setTextFormatter(originalAmountFormatter);
        originalAmountFormatter.valueProperty().bindBidirectional(this.loan.getOriginalAmountProperty());

        TextFormatter<Double> interestRateFormatter = new TextFormatter<>(new DoubleStringConverter(), null,
                c -> doubleRegEx.matcher(c.getControlNewText()).matches() ? c : null);
        interestRateTextField.setTextFormatter(interestRateFormatter);
        interestRateFormatter.valueProperty().bindBidirectional(this.loan.getInterestRateProperty());

        compoundingPeriodChoiceBox.getItems().setAll(Loan.Period.values());
        compoundingPeriodChoiceBox.valueProperty().bindBidirectional(this.loan.getCompoundingPeriodProperty());
        paymentPeriodChoiceBox.getItems().setAll(Loan.Period.values());
        paymentPeriodChoiceBox.valueProperty().bindBidirectional(this.loan.getPaymentPeriodProperty());

        TextFormatter<Integer> numberOfPaymentsFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> integerRegEx.matcher(c.getControlNewText()).matches() ? c : null);
        numberOfPaymentsTextField.setTextFormatter(numberOfPaymentsFormatter);
        numberOfPaymentsFormatter.valueProperty().bindBidirectional(this.loan.getNumberOfPaymentsProperty());

        firstPaymentDatePicker.valueProperty().bindBidirectional(this.loan.getFirstPaymentDateProperty());

        paymentScheduleTableView.setItems(loan.getPaymentSchedule());
        seqNumTableColumn.setCellValueFactory(cd -> cd.getValue().getSequenceIDProperty());
        paymentDateTableColumn.setCellValueFactory(cd -> cd.getValue().getDateProperty());
        principalPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getPrincipalAmountProperty());
        principalPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(DOLLAR_CENT_DECIMAL_FORMAT.format(item));
            }
        });
        interestPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getInterestAmountProperty());
        interestPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(DOLLAR_CENT_DECIMAL_FORMAT.format(item));
            }
        });
        balanceTableColumn.setCellValueFactory(cd -> cd.getValue().getBalanceAmountProperty());
        balanceTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(DOLLAR_CENT_DECIMAL_FORMAT.format(item));
            }
        });

        this.loan.getOriginalAmountProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
        this.loan.getInterestRateProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
        this.loan.getCompoundingPeriodProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
        this.loan.getPaymentPeriodProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
        this.loan.getNumberOfPaymentsProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
        this.loan.getFirstPaymentDateProperty().addListener((obs, o, n) -> this.loan.updatePaymentSchedule());
    }

    private Stage getStage() { return (Stage) nameTextField.getScene().getWindow(); }

    @FXML
    private void handleSave() {
        mainModel.mergeLoan(loan);
        getStage().close();
    }

    @FXML
    private void handleCancel() { getStage().close(); }
}
