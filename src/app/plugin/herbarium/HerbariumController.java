package app.plugin.herbarium;

import app.ui.BusyCursor;
import app.ui.EditLocalityDialog;
import java.awt.Window;

public interface HerbariumController extends BusyCursor {
    void enterMoveMode(EditLocalityDialog dialog);
    void cancelMoveMode();
    void trackWindow(Window window);
}
