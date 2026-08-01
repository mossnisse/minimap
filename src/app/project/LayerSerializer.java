package app.project;

import app.AppContext;
import app.MapLayers;
import app.plugin.PluginManager;
import app.plugin.herbarium.HerbariumPlugin;
import app.plugin.collection.PrivateCollectionPlugin;
import gis.coords.CoordSystem;
import gis.core.Layer;
import gis.core.LayerKey;
import gis.core.LayerManager;
import gis.core.MapCanvas;
import gis.csv.CsvFile;
import gis.layers.*;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LayerSerializer {
    private static final String HEADER = "MINIMAP_LAYERS\t1";
    private LayerSerializer() {}

    public static void write(Path file, LayerManager manager) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Layer layer : manager.getLayers()) {
            Record record = describe(manager, layer);
            if (record != null) lines.add(record.encode());
        }
        ProjectManager.writeAtomic(file, lines);
    }

    public static void writeDefault(Path file, boolean herbarium) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(new Record("TOPOWEB", "TopoWeb", Color.BLACK.getRGB(), false,
                CoordSystem.SWEREF99TM, 0, 0, "", "", "", "").encode());
        lines.add(new Record("SOCKNAR", "socknar", Color.RED.getRGB(), false,
                CoordSystem.SWEREF99TM, 0, 0, "", "", "", "").encode());
        lines.add(new Record("PROVINSER", "provinser", Color.BLACK.getRGB(), false,
                CoordSystem.SWEREF99TM, 0, 0, "", "", "", "").encode());
        lines.add(new Record("ORTNAMN", "Ortnamnsdb", Color.BLACK.getRGB(), false,
                CoordSystem.SWEREF99TM, 0, 5, "", "", "", "").encode());
        if (herbarium) {
            lines.add(new Record("LOKAL_DB", "LokalDB", Color.BLACK.getRGB(), false,
                    CoordSystem.WGS84, 0, 40, "", "", "", "").encode());
        }
        ProjectManager.writeAtomic(file, lines);
    }

    public static List<String> rebuild(Path file, MapCanvas canvas, AppContext core,
                                       PluginManager plugins) {
        List<String> warnings = new ArrayList<>();
        List<Layer> layers = new ArrayList<>();
        Map<LayerKey<?>, Layer> keyed = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
                throw new IOException("Unsupported or missing layer-manifest header");
            }
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) continue;
                try {
                    addRestored(Record.decode(lines.get(i)), canvas, core, plugins, layers, keyed);
                } catch (Exception e) {
                    warnings.add("Layer " + i + " was skipped: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            warnings.add("Layer manifest could not be read; core defaults were restored: " + e.getMessage());
            addCoreDefaults(canvas, core, layers, keyed);
        }
        canvas.getLayerManager().replaceAll(layers, keyed);
        return warnings;
    }

    private static void addCoreDefaults(MapCanvas canvas, AppContext core, List<Layer> layers,
                                        Map<LayerKey<?>, Layer> keyed) {
        add(layers, keyed, null, MapLayers.topoweb(canvas));
        add(layers, keyed, MapLayers.SOCKNAR, MapLayers.socknar(canvas));
        add(layers, keyed, MapLayers.PROVINSER, MapLayers.provinser(canvas));
        add(layers, keyed, MapLayers.ORTNAMN, MapLayers.ortnamn(canvas, core.placeNames));
    }

    private static void addRestored(Record r, MapCanvas canvas, AppContext core,
                                    PluginManager plugins, List<Layer> layers,
                                    Map<LayerKey<?>, Layer> keyed) throws Exception {
        Layer layer;
        LayerKey<?> key = null;
        switch (r.type) {
            case "TOPOWEB" -> layer = MapLayers.topoweb(canvas);
            case "TOPOWEB_LANTMATERIET" -> layer = MapLayers.topowebLantmateriet(canvas);
            case "OSM" -> layer = MapLayers.osm(canvas);
            case "SOCKNAR" -> { layer = MapLayers.socknar(canvas); key = MapLayers.SOCKNAR; }
            case "PROVINSER" -> { layer = MapLayers.provinser(canvas); key = MapLayers.PROVINSER; }
            case "ORTNAMN" -> { layer = MapLayers.ortnamn(canvas, core.placeNames); key = MapLayers.ORTNAMN; }
            case "LOKAL_DB" -> {
                if (!plugins.isActive(HerbariumPlugin.ID)) return;
                layer = plugins.get(HerbariumPlugin.ID, HerbariumPlugin.class).createLocalityLayer();
                key = MapLayers.LOKAL_DB;
            }
            case "COLLECTION_EVENTS" -> {
                if (!plugins.isActive(PrivateCollectionPlugin.ID)) return;
                layer = plugins.get(PrivateCollectionPlugin.ID, PrivateCollectionPlugin.class).createEventLayer();
                key = MapLayers.COLLECTION_EVENTS;
            }
            case "SHAPEFILE" -> {
                requireFile(r.source);
                layer = new ShapeFileLayer(r.source, canvas, r.crs);
            }
            case "GEOPACKAGE" -> {
                requireFile(r.source);
                layer = new GeoPackageLayer(r.source, r.extra1, canvas, r.crs);
            }
            case "GPX" -> {
                requireFile(r.source);
                layer = new GPXFileLayer(r.source, canvas);
            }
            case "RASTER" -> {
                requireFile(r.source);
                layer = new RasterFileLayer(r.source, canvas);
            }
            case "CSV" -> {
                requireFile(r.source);
                File source = new File(r.source);
                CsvPointLayer csv = new CsvPointLayer(source, CsvFile.read(source.toPath()), r.crs, canvas);
                csv.setNorthColumn(emptyToNull(r.extra1));
                csv.setEastColumn(emptyToNull(r.extra2));
                csv.setLabelColumn(emptyToNull(r.extra3));
                csv.rebuildPoints();
                layer = csv;
            }
            default -> throw new IOException("Unknown type " + r.type);
        }
        CoordSystem builtCrs = layer.getCRS();
        layer.setName(r.name);
        layer.setColor(new Color(r.rgb, true));
        layer.setHidden(r.hidden);
        layer.setCRS(r.crs);
        layer.setMinZoomL(r.minZoom);
        layer.setMaxZoomL(r.maxZoom);
        // The constructors above already read and projected the data; only the
        // file layers whose constructor default differs from the manifest CRS
        // need the (expensive) re-projection.
        if (builtCrs != r.crs) layer.invalidateCache();
        add(layers, keyed, key, layer);
    }

    private static void requireFile(String source) throws IOException {
        if (source == null || source.isBlank() || !new File(source).isFile()) {
            throw new IOException("Missing source file: " + source);
        }
    }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }

    private static void add(List<Layer> layers, Map<LayerKey<?>, Layer> keyed,
                            LayerKey<?> key, Layer layer) {
        layers.add(layer);
        if (key != null) keyed.put(key, layer);
    }

    private static Record describe(LayerManager manager, Layer layer) {
        if (is(manager, MapLayers.RUBIN_MARKER, layer)
                || is(manager, MapLayers.DISTANCE_OVERLAY, layer)
                || is(manager, MapLayers.SEARCH_RESULTS, layer)
                || layer instanceof RubinLayer || layer instanceof DistanceLayer
                || layer instanceof TNGPointFileLayer) return null;

        String type;
        String source = "", e1 = "", e2 = "", e3 = "";
        if (is(manager, MapLayers.LOKAL_DB, layer)) type = "LOKAL_DB";
        else if (is(manager, MapLayers.COLLECTION_EVENTS, layer)) type = "COLLECTION_EVENTS";
        else if (is(manager, MapLayers.ORTNAMN, layer)) type = "ORTNAMN";
        else if (is(manager, MapLayers.PROVINSER, layer)) type = "PROVINSER";
        else if (is(manager, MapLayers.SOCKNAR, layer)) type = "SOCKNAR";
        else if (layer instanceof TopowebLayer value) {
            type = value.getProvider() == TopowebLayer.Provider.LANTMATERIET
                    ? "TOPOWEB_LANTMATERIET" : "TOPOWEB";
        }
        else if (layer instanceof OSMLayer) type = "OSM";
        else if (layer instanceof ShapeFileLayer value) { type = "SHAPEFILE"; source = value.getSourcePath(); }
        else if (layer instanceof GeoPackageLayer value) {
            type = "GEOPACKAGE"; source = value.getFilePath(); e1 = value.getTableName();
        } else if (layer instanceof GPXFileLayer value) { type = "GPX"; source = value.getSourcePath(); }
        else if (layer instanceof RasterFileLayer value) { type = "RASTER"; source = value.getSourcePath(); }
        else if (layer instanceof CsvPointLayer value) {
            type = "CSV"; source = value.getFile().getPath();
            e1 = nullToEmpty(value.getNorthColumn());
            e2 = nullToEmpty(value.getEastColumn());
            e3 = nullToEmpty(value.getLabelColumn());
        } else return null;

        return new Record(type, layer.getName(), layer.getColor().getRGB(), layer.isHidden(),
                layer.getCRS(), layer.getMinZoom(), layer.getMaxZoom(), source, e1, e2, e3);
    }

    private static boolean is(LayerManager manager, LayerKey<?> key, Layer layer) {
        return manager.get(key).map(value -> value == layer).orElse(false);
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private record Record(String type, String name, int rgb, boolean hidden, CoordSystem crs,
                          int minZoom, int maxZoom, String source,
                          String extra1, String extra2, String extra3) {
        String encode() {
            return String.join("\t", type, b64(name), Integer.toString(rgb), Boolean.toString(hidden),
                    crs.name(), Integer.toString(minZoom), Integer.toString(maxZoom), b64(source),
                    b64(extra1), b64(extra2), b64(extra3));
        }

        static Record decode(String line) throws IOException {
            String[] f = line.split("\t", -1);
            if (f.length != 11) throw new IOException("Expected 11 fields, found " + f.length);
            try {
                if (!"true".equals(f[3]) && !"false".equals(f[3])) {
                    throw new IllegalArgumentException("invalid hidden flag");
                }
                CoordSystem crs = CoordSystem.valueOf(f[4]);
                int min = Integer.parseInt(f[5]), max = Integer.parseInt(f[6]);
                if (min < 0 || max < 0) throw new IllegalArgumentException("negative zoom");
                return new Record(f[0], unb64(f[1]), Integer.parseInt(f[2]), Boolean.parseBoolean(f[3]),
                        crs, min, max, unb64(f[7]), unb64(f[8]), unb64(f[9]), unb64(f[10]));
            } catch (Exception e) {
                throw new IOException("Invalid layer record: " + e.getMessage(), e);
            }
        }
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
