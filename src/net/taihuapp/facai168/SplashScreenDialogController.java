package net.taihuapp.facai168;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.time.LocalDateTime;

/**
 * Created by ghe on 3/21/17.
 *
 */
public class SplashScreenDialogController {
    private Stage mStage;
    private MainApp mMainApp;

    @FXML
    private TextArea mTextArea;
    @FXML
    private CheckBox mAgreeCheckBox;
    @FXML
    private Button mContinueButton;
    @FXML
    private Button mStopButton;

    void setMainApp(MainApp mainApp, Stage stage) {
        mMainApp = mainApp;
        mStage = stage;
    }

    @FXML
    private void handleContinue() {
        mMainApp.putAcknowledgeTimeStamp(LocalDateTime.now());
        System.out.println("OK");
        mStage.close();
    }

    @FXML
    private void handleStop() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void initialize() {
        // this two paragraphs are copied from GPL.
        mTextArea.setText("THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY APPLICABLE LAW. "
                + "EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR OTHER PARTIES PROVIDE "
                + "THE PROGRAM “AS IS” WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, "
                + "BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR "
                + "PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM IS WITH YOU. "
                + "SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF ALL NECESSARY SERVICING, REPAIR "
                + "OR CORRECTION."
                + "\n\n"
                + "IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING WILL ANY COPYRIGHT "
                + "HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS THE PROGRAM AS PERMITTED ABOVE, BE "
                + "LIABLE TO YOU FOR DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL "
                + "DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED "
                + "TO LOSS OF DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD "
                + "PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS), EVEN IF SUCH "
                + "HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.");
        mContinueButton.disableProperty().bind(mAgreeCheckBox.selectedProperty().not());
        mStopButton.disableProperty().bind(mAgreeCheckBox.selectedProperty());
    }
}
