package net.taihuapp.facai168;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Created by ghe on 5/24/16.
 *
 */
public class SecurityListDialogController {
    @FXML
    private TableView<Security> mSecurityTableView;
    @FXML
    private TableColumn<Security, String> mSecurityNameColumn;
    @FXML
    private TableColumn<Security, String> mSecurityTickerColumn;
    @FXML
    private TableColumn<Security, Security.Type> mSecurityTypeColumn;

    void setMainApp(MainApp mainApp) {
        mSecurityTableView.setEditable(true);
        mSecurityTableView.setItems(mainApp.getSecurityList());

        mSecurityNameColumn.setCellValueFactory(cellData->cellData.getValue().getNameProperty());
        mSecurityTickerColumn.setCellValueFactory(cellData->cellData.getValue().getTickerProperty());
        mSecurityTypeColumn.setCellValueFactory(cellData->cellData.getValue().getTypeProperty());
    }
}
