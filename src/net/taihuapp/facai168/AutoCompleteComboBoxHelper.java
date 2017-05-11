package net.taihuapp.facai168;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextFormatter;

import java.util.function.Predicate;

/**
 * Created by ghe on 4/26/17.
 *
 */
class AutoCompleteComboBoxHelper<T> {
    private final ComboBox<T> mComboBox;
    private final FilteredList<T> mFilteredItems;
    private boolean mKeyEvent;

    AutoCompleteComboBoxHelper(final ComboBox<T> comboBox) {
        mKeyEvent = false;
        mComboBox = comboBox;
        final ObservableList<T> allItems = mComboBox.getItems();
        mFilteredItems = new FilteredList<>(allItems);
        mComboBox.setItems(mFilteredItems);
        mComboBox.setEditable(true);

        // this line suppose to work around for JavaFX needing an enter to commit combobox edit
        mComboBox.getEditor().setTextFormatter(new TextFormatter<>(TextFormatter.IDENTITY_STRING_CONVERTER));

        mComboBox.getEditor().setOnKeyPressed(event -> {
            // once we are in editing mode, existing selection is not longer valid
            // clear out now.  Otherwise, ComboBox will clear it and causes exception
            mComboBox.getSelectionModel().clearSelection();
            mKeyEvent = true;
        });
        mComboBox.getEditor().setOnKeyReleased(event -> mKeyEvent = false);

        mComboBox.getEditor().textProperty().addListener((obs, ov, nv) -> {
            if (!mKeyEvent)
                return; // if change is not due to keyevent, do nothing

            if (nv == null || nv.isEmpty()) {
                mFilteredItems.setPredicate(item -> true);
                return;
            }

            Predicate<? super T> oldPredicate = mFilteredItems.getPredicate();
            // setPredicate sometime (why?) trigs a change in mComboBox.getEditor().textProperty()
            // we don't want to process that.  set mKeyEvent to false here
            mKeyEvent = false;
            mFilteredItems.setPredicate(item ->
                    mComboBox.getConverter().toString(item).toLowerCase().contains(nv.toLowerCase()));
            if (mFilteredItems.size() == 0) {
                // nv is not contained in any items, reject it, put back ov.
                int c = mComboBox.getEditor().getCaretPosition();
                mComboBox.getEditor().setText(ov);
                mComboBox.getEditor().positionCaret(c);
                mFilteredItems.setPredicate(oldPredicate);
            }
        });
    }
}
