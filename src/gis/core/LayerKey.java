package gis.core;

/**
 * Typed handle for a well-known or transient layer, replacing string-name lookups.
 * Keys use identity semantics: hold them as shared static constants and pass the
 * same instance to every {@link LayerManager} call.
 */
public final class LayerKey<T extends Layer> {
	private final String id;
	private final Class<T> type;

	private LayerKey(String id, Class<T> type) {
		this.id = id;
		this.type = type;
	}

	public static <T extends Layer> LayerKey<T> of(String id, Class<T> type) {
		return new LayerKey<>(id, type);
	}

	public Class<T> type() {
		return type;
	}

	@Override
	public String toString() {
		return id;
	}
}
