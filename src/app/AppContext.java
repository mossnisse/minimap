package app;

import app.db.Database;
import app.repo.PlaceNameRepository;

import java.util.function.Supplier;

/**
 * Composition root: wires the databases, repositories and services the app
 * runs on. Created once at startup by {@link GUI}; dialogs receive the
 * dependencies they need from here instead of reaching into layers or
 * static state.
 */
public final class AppContext {
	public final Database db;
	public final PlaceNameRepository placeNames;

	private AppContext(Database db) {
		this.db = db;
		this.placeNames = new PlaceNameRepository(db);
	}

	/**
	 * @param passwordPrompt asks the user for the database password, or returns
	 *                       null if they cancelled. The caller supplies it so
	 *                       that this root stays free of any {@code app.ui}
	 *                       dependency.
	 */
	public static AppContext create(Supplier<String> passwordPrompt) {
		return new AppContext(new Database(passwordPrompt));
	}
}
