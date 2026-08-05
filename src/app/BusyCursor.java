package app;

/**
 * The cursor slice of the main window that dialogs and plugins may drive.
 * It lives here rather than in {@code app.ui} so that plugins can depend on
 * the shell's contract without any package pointing back at the shell — that
 * back-edge is what used to make {@code app.ui} and {@code app.plugin} a cycle.
 * Implemented by {@code app.ui.GUI}.
 */
public interface BusyCursor {
    void setCursorWait();
    void setCursorDefault();
}
