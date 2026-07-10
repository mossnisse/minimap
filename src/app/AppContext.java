package app;

import app.db.Database;
import app.ui.PasswDialog;
import app.service.SpecimenService;
import app.repo.LocalityRepository;
import app.repo.PlaceNameRepository;

/**
 * Composition root: wires the databases, repositories and services the app
 * runs on. Created once at startup by {@link GUI}; dialogs receive the
 * dependencies they need from here instead of reaching into layers or
 * static state.
 */
public final class AppContext {
	public final Database db;
	public final LocalityRepository localities;
	public final PlaceNameRepository placeNames;
	public final SpecimenService specimens;

	private AppContext(Database db) {
		this.db = db;
		this.localities = new LocalityRepository(db);
		this.placeNames = new PlaceNameRepository(db);
		this.specimens = new SpecimenService(db);
	}

	public static AppContext create() {
		Database db = new Database(() -> {
			String input = new PasswDialog().open();
			return "codeCancel".equals(input) ? null : input;
		});
		return new AppContext(db);
	}
}
