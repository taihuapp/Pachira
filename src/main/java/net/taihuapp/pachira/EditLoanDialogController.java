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
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.regex.Pattern;


public class EditLoanDialogController {

    private static final Pattern DOLLAR_CENT_REG_EX = Pattern.compile("^(0|[1-9][,\\d]*)?(\\.\\d{0,2})?$");
    private static final Pattern DECIMAL_REG_EX = Pattern.compile("^(0|[1-9]\\d*)?(\\.\\d*)?$");
    private static final Pattern INTEGER_REG_EX = Pattern.compile("^([1-9]+\\d*)?$");

    private static final BigDecimalStringConverter DOLLAR_CENT_2_STRING_CONVERTER = new BigDecimalStringConverter() {
        @Override
        public BigDecimal fromString(String s) {
            try {
                return s == null ? null : (BigDecimal) MainModel.DOLLAR_CENT_2_FORMAT.parse(s);
            } catch (ParseException e) {
                return null;
            }
        }

        @Override
        public String toString(BigDecimal b) {
            return b == null ? null : MainModel.DOLLAR_CENT_2_FORMAT.format(b);
        }
    };

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
    private TableColumn<Loan.PaymentItem, BigDecimal> principalPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, BigDecimal> interestPaymentTableColumn;
    @FXML
    private TableColumn<Loan.PaymentItem, BigDecimal> balanceTableColumn;

    void setMainModel(MainModel mainModel, Loan loan) {
        this.mainModel = mainModel;
        this.loan = loan;

        nameTextField.textProperty().bindBidirectional(this.loan.getNameProperty());
        descriptionTextField.textProperty().bindBidirectional(this.loan.getDescriptionProperty());

        TextFormatter<BigDecimal> originalAmountFormatter = new TextFormatter<>(DOLLAR_CENT_2_STRING_CONVERTER,null,
                c -> DOLLAR_CENT_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);

        originalAmountTextField.setTextFormatter(originalAmountFormatter);
        originalAmountFormatter.valueProperty().bindBidirectional(this.loan.getOriginalAmountProperty());

        TextFormatter<BigDecimal> interestRateFormatter = new TextFormatter<>(new BigDecimalStringConverter(), null,
                c -> DECIMAL_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        interestRateTextField.setTextFormatter(interestRateFormatter);
        interestRateFormatter.valueProperty().bindBidirectional(this.loan.getInterestRateProperty());

        compoundingPeriodChoiceBox.getItems().setAll(Loan.Period.values());
        compoundingPeriodChoiceBox.valueProperty().bindBidirectional(this.loan.getCompoundingPeriodProperty());
        paymentPeriodChoiceBox.getItems().setAll(Loan.Period.values());
        paymentPeriodChoiceBox.valueProperty().bindBidirectional(this.loan.getPaymentPeriodProperty());

        TextFormatter<Integer> numberOfPaymentsFormatter = new TextFormatter<>(new IntegerStringConverter(), null,
                c -> INTEGER_REG_EX.matcher(c.getControlNewText()).matches() ? c : null);
        numberOfPaymentsTextField.setTextFormatter(numberOfPaymentsFormatter);
        numberOfPaymentsFormatter.valueProperty().bindBidirectional(this.loan.getNumberOfPaymentsProperty());

        firstPaymentDatePicker.valueProperty().bindBidirectional(this.loan.getFirstPaymentDateProperty());

        paymentScheduleTableView.setItems(loan.getPaymentSchedule());
        seqNumTableColumn.setCellValueFactory(cd -> cd.getValue().getSequenceIDProperty());
        paymentDateTableColumn.setCellValueFactory(cd -> cd.getValue().getDateProperty());
        principalPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getPrincipalAmountProperty());
        principalPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(MainModel.DOLLAR_CENT_2_FORMAT.format(item));
            }
        });
        interestPaymentTableColumn.setCellValueFactory(cd -> cd.getValue().getInterestAmountProperty());
        interestPaymentTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(MainModel.DOLLAR_CENT_2_FORMAT.format(item));
            }
        });
        balanceTableColumn.setCellValueFactory(cd -> cd.getValue().getBalanceAmountProperty());
        balanceTableColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty)
                    setText("");
                else
                    setText(MainModel.DOLLAR_CENT_2_FORMAT.format(item));
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
