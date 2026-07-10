package main.core;

import main.dialogs.PasswDialog;
import main.repo.LocalityRepository;
import main.repo.PlaceNameRepository;

/**
 * Composition root: wires the databases and repositories the app runs on.
 * Created once at startup by {@link GUI}; dialogs receive the repositories
 * they need from here instead of reaching into layers or static state.
 */
public final class AppContext {
	public final Database db;
	public final LocalityRepository localities;
	public final PlaceNameRepository placeNames;

	private AppContext(Database db) {
		this.db = db;
		this.localities = new LocalityRepository(db);
		this.placeNames = new PlaceNameRepository(db);
	}

	public static AppContext create() {
		Database db = new Database(() -> {
			String input = new PasswDialog().open();
			return "codeCancel".equals(input) ? null : input;
		});
		DBConnection.install(db);
		return new AppContext(db);
	}
}
