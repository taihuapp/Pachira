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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;

public class SplashScreenDialogController {

    private static final Logger mLogger = LogManager.getLogger(SplashScreenDialogController.class);

    private Instant acknowledgeInstant;

    @FXML
    private Label mApplicationNameLabel;
    @FXML
    private Label mApplicationVersionLabel;
    @FXML
    private TextArea mShortTextArea;
    @FXML
    private TextArea mMultiUseTextArea;
    @FXML
    private CheckBox mAgreeCheckBox;
    @FXML
    private Button mContinueButton;
    @FXML
    private Button mStopButton;

    void setFirstTime(boolean firstTime) {
        mAgreeCheckBox.setSelected(!firstTime);
        mAgreeCheckBox.setVisible(firstTime);
        mStopButton.setVisible(firstTime);

        final String text = readResourceTextFile2String("/text/Disclaimer");
        if (text != null) {
            mShortTextArea.setText(text);
        } else {
            delayedStop();
        }

        // displaying contact info at MultiUseTextArea
        showContactInfo();
    }

    Instant getAcknowledgeDateTime() { return acknowledgeInstant; }

    private void delayedStop() {
        mAgreeCheckBox.setVisible(false);  // don't let user change the checkbox
        mAgreeCheckBox.setSelected(false);  // this will force stop the app when window close
        mContinueButton.setVisible(false); // don't let user continue
        mStopButton.setVisible(true); // stop is the only option
    }

    @FXML
    private void handleContinue() {
        acknowledgeInstant = Instant.now();
        ((Stage) mContinueButton.getScene().getWindow()).close();
    }

    void handleClose() {
        if (!mAgreeCheckBox.selectedProperty().get()) {
            // did not agree, stop now.
            handleStop();
        } else {
            // agreed, continue
            handleContinue();
        }
    }

    @FXML
    void handleStop() {
        // this means user has NOT given an acknowledgement.  Stop everything
        Platform.exit();
        System.exit(0);
    }

    private void showResTextFile(String resFileName) {
        final String warning = "Failed to read text file. Probably a corrupt installation.  Stop now.";
        final String text = readResourceTextFile2String(resFileName);
        if (text != null) {
            mMultiUseTextArea.setText(text);
        } else {
            mMultiUseTextArea.setText(warning);
            delayedStop();
        }
    }

    @FXML
    private void showGPL() {
        showResTextFile("/text/COPYING");
    }

    @FXML
    private void showThirdParty() {
        showResTextFile("/text/ThirdPartyLicense");
    }

    @FXML
    private void showContactInfo() {
        showResTextFile("/text/ContactInfo");
    }

    @FXML
    private void initialize() {
        mContinueButton.disableProperty().bind(mAgreeCheckBox.selectedProperty().not());
        mStopButton.disableProperty().bind(mAgreeCheckBox.selectedProperty());

        mApplicationNameLabel.setText(MainApp.class.getPackage().getImplementationTitle());
        mApplicationVersionLabel.setText(MainApp.class.getPackage().getImplementationVersion());
    }

    private String readResourceTextFile2String(String fileName) {
        final InputStream inputStream = MainApp.class.getResourceAsStream(fileName);
        if (inputStream == null) {
            // failed to open resource file name
            mLogger.error("Failed to open resource " + fileName);
            return null;
        }

        final StringBuilder stringBuilder = new StringBuilder();
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        try {
            String line;
            while ((line = bufferedReader.readLine()) != null)
                stringBuilder.append(line).append(System.lineSeparator());
            return stringBuilder.toString();
        } catch (IOException e) {
            mLogger.error("IOException", e);
            return null;
        }
    }
}
