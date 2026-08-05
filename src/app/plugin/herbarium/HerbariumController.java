package app.plugin.herbarium;

import app.BusyCursor;
import java.awt.Window;

public interface HerbariumController extends BusyCursor {
    void enterMoveMode(EditLocalityDialog dialog);
    void cancelMoveMode();
    void trackWindow(Window window);
}
