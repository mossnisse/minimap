package app.plugin.collection;

import app.db.Database;
import app.repo.PlaceNameRepository;
import app.ui.CoordinateEntry;
import gis.coords.Coordinate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static app.plugin.collection.CollectionTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateCollectionCoreTest {
    @TempDir Path temp;
    private CollectionDatabase database;
    private CollectionRepository repository;

    @BeforeEach void open() throws Exception {
        database = CollectionDatabase.inMemory("collection_" + UUID.randomUUID(), temp.resolve("collection"));
        repository = new CollectionRepository(database);
    }

    @AfterEach void close() throws Exception { database.close(); }

    @Test void createsSchemaAndKeepsTextOnlyLegacyLocalities() throws Exception {
        assertEquals("NE", repository.setting("accession.prefix", ""));
        long id = repository.saveLocality(new CollectionRepository.Locality(0, "Old handwritten locality", null,
                "SE", "Sweden", "Västerbotten", "Umeå", "Old label text", null, null,
                null, CoordinateSource.TEXT_ONLY, null, null, null));
        CollectionRepository.Locality saved = repository.locality(id);
        assertFalse(saved.hasCoordinate());
        assertEquals(CoordinateSource.TEXT_ONLY, saved.coordinateSource());
    }

    @Test void deletesUnusedLocalitiesAndKeepsTheOnesEventsPointAt() throws Exception {
        long unused = repository.saveLocality(new CollectionRepository.Locality(0, "Typo in the name", null,
                "SE", "Sweden", null, null, null, null, null, null, CoordinateSource.TEXT_ONLY, null, null, null));
        long used = localityWithCoordinate();
        event(used, CollectionKind.INSECT);

        repository.deleteLocality(unused);
        assertEquals(List.of("Carlshemsskogen"), repository.localities().stream().map(CollectionRepository.Locality::name).toList());
        assertThrows(SQLException.class, () -> repository.locality(unused));

        assertEquals(1, repository.localityEventCount(used));
        assertTrue(assertThrows(SQLException.class, () -> repository.deleteLocality(used)).getMessage().contains("1 collection event"));
        assertEquals(1, repository.localities().size());
    }

    @Test void enteredCoordinateSourceTracksHowTheCoordinateWasEntered() {
        assertEquals(CoordinateSource.MAP,
                PrivateCollectionManager.enteredCoordinateSource(CoordinateEntry.Origin.PICKED, null));
        assertEquals(CoordinateSource.MANUAL,
                PrivateCollectionManager.enteredCoordinateSource(CoordinateEntry.Origin.TYPED, CoordinateSource.PHONE));
        assertEquals(CoordinateSource.PHONE,
                PrivateCollectionManager.enteredCoordinateSource(CoordinateEntry.Origin.LOADED, CoordinateSource.PHONE));
        assertEquals(CoordinateSource.MANUAL,
                PrivateCollectionManager.enteredCoordinateSource(CoordinateEntry.Origin.LOADED, null));
    }

    @Test void fileDatabaseIsProjectScopedAndPortable() throws Exception {
        Path project = temp.resolve("dedicated-project");
        try (CollectionDatabase fileDatabase = new CollectionDatabase(project)) {
            CollectionRepository fileRepository = new CollectionRepository(fileDatabase);
            fileRepository.setSetting("owner.fullName", "Project owner");
            assertTrue(Files.isDirectory(project.resolve("private_collection/photos")));
            assertTrue(Files.isDirectory(project.resolve("private_collection/labels")));
            assertTrue(Files.isDirectory(project.resolve("private_collection/exports")));
            assertTrue(Files.isDirectory(project.resolve("private_collection/backups")));
        }
        assertTrue(Files.isRegularFile(project.resolve("private_collection/collection.mv.db")));
        try (CollectionDatabase reopened = new CollectionDatabase(project)) {
            assertEquals("Project owner", new CollectionRepository(reopened).setting("owner.fullName", ""));
        }
    }

    @Test void closedCollectionDatabaseCannotBeReopenedByAnAsyncLayerFetch() throws Exception {
        database.close();
        assertThrows(SQLException.class, database::connection);
    }

    @Test void reservesAssignsAndNeverReoffersCollectionNumbers() throws Exception {
        long locality = localityWithCoordinate(); long event = event(locality, CollectionKind.INSECT);
        List<String> numbers = repository.reserveNumbers(3);
        assertEquals(List.of("NE1", "NE2", "NE3"), numbers);
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, "NE1", "Invertebrates",
                null, 1, "Hane", "Imago/Adult", null, null, ReportIntent.INCLUDE, false));
        assertEquals("NE1", repository.specimen(specimen).accessionNumber());
        assertEquals(List.of("NE2", "NE3"), repository.reservedNumbers());
        assertEquals(List.of("NE4"), repository.reserveNumbers(1));
        assertThrows(Exception.class, () -> repository.saveSpecimen(new CollectionRepository.Specimen(0, event, "NE1",
                null, null, 1, null, null, null, null, ReportIntent.INCLUDE, false)));
    }

    @Test void exportsExactContractAndTracksReportedChanges() throws Exception {
        long locality = localityWithCoordinate(); long event = event(locality, CollectionKind.INSECT);
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Invertebrates",
                "Chyliza vittata", 1, "Hane", "Imago/Adult", "leaf litter", "voucher", ReportIntent.INCLUDE, false));
        repository.addDetermination(specimen, "Chyliza vittata", "Nils Ericson", 2026,
                IdentificationKind.DET, false, null);
        ArtportalenExporter exporter = new ArtportalenExporter(repository);
        ArtportalenExporter.PreparedRow prepared = exporter.prepare(specimen);
        assertTrue(prepared.valid(), prepared.errors().toString());
        assertEquals(59, SlimeRecordsImporter.parseCsv(prepared.csvLine(), ';').getFirst().size());
        assertEquals(ReportStatus.READY, repository.reportStatus(specimen, true, prepared.payloadHash()));

        ArtportalenExporter.ExportResult result = exporter.exportReady(temp.resolve("artportalen.csv"));
        byte[] bytes = Files.readAllBytes(result.path());
        assertArrayEquals(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}, java.util.Arrays.copyOf(bytes, 3));
        assertEquals(59, Files.readString(result.path(), StandardCharsets.UTF_8).lines().findFirst().orElseThrow().split(";", -1).length);
        assertEquals(ReportStatus.EXPORTED, repository.reportStatus(specimen, true, prepared.payloadHash()));
        exporter.confirmLatestExport();
        assertEquals(ReportStatus.REPORTED, repository.reportStatus(specimen, true, prepared.payloadHash()));

        CollectionRepository.Specimen old = repository.specimen(specimen);
        repository.saveSpecimen(new CollectionRepository.Specimen(old.id(), old.eventId(), old.accessionNumber(), old.taxonGroup(),
                old.preliminaryTaxon(), old.quantity(), old.sex(), old.lifeStage(), old.substrate(), "changed comment", old.reportIntent(), false));
        ArtportalenExporter.PreparedRow changed = exporter.prepare(specimen);
        assertEquals(ReportStatus.UPDATE_NEEDED, repository.reportStatus(specimen, true, changed.payloadHash()));
    }

    @Test void keepsEveryUnconfirmedExportBatchSelectable() throws Exception {
        long locality = localityWithCoordinate(); long event = event(locality, CollectionKind.INSECT);
        ArtportalenExporter exporter = new ArtportalenExporter(repository);

        long first = determinedSpecimen(event, "Chyliza vittata");
        ArtportalenExporter.ExportResult firstExport = exporter.exportReady(temp.resolve("first.csv"));
        long second = determinedSpecimen(event, "Chyliza leptogaster");
        ArtportalenExporter.ExportResult secondExport = exporter.exportReady(temp.resolve("second.csv"));

        assertEquals(2, repository.pendingExports().size());
        long firstExportId = repository.pendingExports().stream()
                .filter(e -> e.outputPath().equals(firstExport.path().toString())).findFirst().orElseThrow().id();
        long secondExportId = repository.pendingExports().stream()
                .filter(e -> e.outputPath().equals(secondExport.path().toString())).findFirst().orElseThrow().id();
        exporter.confirmExport(firstExportId);
        assertEquals(List.of(second), repository.pendingExports().stream()
                .flatMap(e -> {
                    try { return repository.exportHashes(e.id()).keySet().stream(); }
                    catch (SQLException ex) { throw new RuntimeException(ex); }
                }).toList());
        exporter.confirmExport(secondExportId);
        assertTrue(repository.pendingExports().isEmpty());
        assertEquals(ReportStatus.REPORTED, repository.reportStatus(first, true, exporter.prepare(first).payloadHash()));
        assertEquals(ReportStatus.REPORTED, repository.reportStatus(second, true, exporter.prepare(second).payloadHash()));
    }

    @Test void rejectsUncertaintyThatArtportalenCannotRepresent() throws Exception {
        long locality = repository.saveLocality(new CollectionRepository.Locality(0, "Broad locality", null,
                "SE", "Sweden", "Västerbotten", "Umeå", null, 63.8, 20.3,
                6001, CoordinateSource.MAP, null, null, null));
        long event = event(locality, CollectionKind.INSECT);
        long specimen = determinedSpecimen(event, "Chyliza vittata");
        ArtportalenExporter.PreparedRow row = new ArtportalenExporter(repository).prepare(specimen);
        assertFalse(row.valid());
        assertTrue(row.errors().stream().anyMatch(e -> e.contains("5000 m maximum")));
    }

    @Test void createsPhysicalLabelLayoutsAndDetectsContentChanges() throws Exception {
        long locality = localityWithCoordinate(); long event = event(locality, CollectionKind.BOTANICAL);
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Mossor",
                "Hylocomium splendens", 1, null, null, "sten", null, ReportIntent.INCLUDE, false));
        LabelGenerator generator = new LabelGenerator(repository);
        LabelGenerator.WrittenSheet botanical = generator.writeSpecimenSheet(List.of(specimen), LabelType.BOTANICAL);
        String html = Files.readString(botanical.path());
        assertTrue(html.contains("width:92mm;height:64mm"));
        assertTrue(html.contains("grid-template-columns:repeat(2,92mm)"));
        LabelGenerator.WrittenSheet insect = generator.writeEventSheet(event, 2);
        assertTrue(Files.readString(insect.path()).contains("width:20mm;height:10mm"));
        String hash = generator.botanicalLabel(specimen).contentHash();
        repository.recordLabelPrint(LabelType.BOTANICAL, specimen, 1, hash, botanical.path().toString());
        assertEquals(LabelStatus.PRINTED, repository.labelStatus(LabelType.BOTANICAL, specimen, hash));
        assertEquals(LabelStatus.CHANGED, repository.labelStatus(LabelType.BOTANICAL, specimen, LabelGenerator.sha256("changed")));
    }

    @Test void dashboardTracksEveryRequiredLabelAndRequiredCopyCount() throws Exception {
        long locality = localityWithCoordinate(); long event = event(locality, CollectionKind.INSECT);
        LabelGenerator generator = new LabelGenerator(repository);
        ArtportalenExporter exporter = new ArtportalenExporter(repository);
        assertEquals(1, repository.dashboard(exporter, generator).unprinted());

        long specimen = determinedSpecimen(event, "Chyliza vittata");
        assertEquals(3, repository.dashboard(exporter, generator).unprinted());
        var eventLabel = generator.eventLabel(event);
        repository.recordLabelPrint(LabelType.EVENT, event, 2, eventLabel.contentHash(), "event.html");
        assertEquals(3, repository.dashboard(exporter, generator).unprinted());
        repository.recordLabelPrint(LabelType.EVENT, event, 3, eventLabel.contentHash(), "event.html");
        assertEquals(2, repository.dashboard(exporter, generator).unprinted());

        var accession = generator.accessionLabel(specimen);
        var determination = generator.determinationLabel(specimen);
        repository.recordLabelPrint(accession.type(), specimen, 1, accession.contentHash(), "accession.html");
        repository.recordLabelPrint(determination.type(), specimen, 1, determination.contentHash(), "determination.html");
        assertEquals(0, repository.dashboard(exporter, generator).unprinted());
    }

    @Test void nearestPlaceDirectionDescribesTheLocalityRelativeToThePlace() throws Exception {
        try (Connection placeDb = DriverManager.getConnection("jdbc:h2:mem:places_" + UUID.randomUUID())) {
            try (var statement = placeDb.createStatement()) {
                statement.execute("CREATE TABLE ortnamnSWTM(NORTH INT,EAST INT,Ortnamn VARCHAR(255))");
                statement.execute("INSERT INTO ortnamnSWTM VALUES (0,0,'Town')");
            }
            Database db = new Database(() -> null) {
                @Override public synchronized Connection h2() { return placeDb; }
            };
            PlaceNameRepository places = new PlaceNameRepository(db);
            PlaceNameRepository.NearestPlace northOfTown = places.findNearest(new Coordinate(1000, 0), 1500);
            assertNotNull(northOfTown);
            assertEquals("N", northOfTown.direction());

            try (var statement = placeDb.createStatement()) {
                statement.execute("DELETE FROM ortnamnSWTM");
                statement.execute("INSERT INTO ortnamnSWTM VALUES (1000,1000,'Outside circle')");
            }
            assertNull(places.findNearest(new Coordinate(0, 0), 1200));
        }
    }

    @Test void importsInsectAndBotanicalRowsAndSkipsRepeatSourceIds() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,coordinateUncertaintyInMeters,verbatimElevation,eventDate,taxonName,organismQuantity,lifeStage,sex,samplingProtocol,Substrate,Habitat,recordedBy,countryCode,country,province,district,locality,isSpecimen,SpecimenNr,occurrenceRemarks,photos\n";
        Path insect = temp.resolve("insect.csv");
        Files.writeString(insect, header + "10,63.79881,20.33348,10,25,2026-07-01 12:30:00,Chyliza vittata,3,Imago/Adult,Hane,Slaghåvning,,forest,Nils Ericson,SE,Sweden,Västerbotten,Umeå,Carlshemsskogen,true,77,tube,\n");
        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.Preview first = importer.preview(insect, CollectionKind.INSECT);
        SlimeRecordsImporter.ImportResult added = importer.commit(first);
        assertEquals(1, added.addedEvents()); assertEquals(0, added.addedSpecimens());
        SlimeRecordsImporter.ImportResult repeat = importer.commit(importer.preview(insect, CollectionKind.INSECT));
        assertEquals(1, repeat.skipped());

        Path plant = temp.resolve("plant.csv");
        Files.writeString(plant, header + "11,64.63008,17.98744,5,0,2026-06-23 09:00:00,Hylocomium splendens,1,,,Observerad,sten,skog,Nils Ericson,SE,Sweden,Lappland,Lycksele,Åliden,true,99,botanical,\n");
        SlimeRecordsImporter.ImportResult botanical = importer.commit(importer.preview(plant, CollectionKind.BOTANICAL));
        assertEquals(1, botanical.addedEvents()); assertEquals(1, botanical.addedSpecimens());
        assertTrue(repository.specimens("").stream().anyMatch(s -> s.accessionNumber().equals("NE99")));
        assertEquals("100", repository.setting("accession.next", ""));
    }

    @Test void importsDistinctBotanicalRowsFromTheSameCollectionEvent() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,organismQuantity,recordedBy,countryCode,country,province,district,locality,SpecimenNr\n";
        Path plants = temp.resolve("plants.csv");
        Files.writeString(plants, header
                + "101,64.63008,17.98744,2026-06-23 09:00:00,Hylocomium splendens,1,Nils Ericson,SE,Sweden,Lappland,Lycksele,Åliden,\n"
                + "102,64.63008,17.98744,2026-06-23 09:00:00,Hylocomium splendens,1,Nils Ericson,SE,Sweden,Lappland,Lycksele,Åliden,\n");

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult first = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));
        assertEquals(1, first.addedEvents());
        assertEquals(2, first.addedSpecimens());
        assertEquals(0, first.skipped());

        SlimeRecordsImporter.ImportResult repeat = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));
        assertEquals(0, repeat.addedSpecimens());
        assertEquals(2, repeat.skipped());
    }

    @Test void importsPortableArchivePhotosAndRejectsTraversalEntries() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,organismQuantity,recordedBy,countryCode,country,province,district,locality,isSpecimen,SpecimenNr,photos\n";
        String row = "20,63.79881,20.33348,2026-07-01 12:30:00,Chyliza vittata,1,Nils Ericson,SE,Sweden,Västerbotten,Umeå,Carlshemsskogen,true,120,voucher.jpg\n";
        Path archive = temp.resolve("portable.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("data.csv")); zip.write((header + row).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("photos/voucher.jpg")); zip.write(new byte[]{1,2,3}); zip.closeEntry();
        }
        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(archive, CollectionKind.BOTANICAL));
        assertEquals(1, result.photosCopied());
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(repository.eventPhotos(repository.events("").getFirst().id()).getFirst()));

        Path unsafe = temp.resolve("unsafe.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(unsafe))) {
            zip.putNextEntry(new ZipEntry("data.csv")); zip.write((header + row.replace("20,", "21,")).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("photos/../evil.jpg")); zip.write(new byte[]{9}); zip.closeEntry();
        }
        assertThrows(Exception.class, () -> importer.preview(unsafe, CollectionKind.INSECT));
        assertFalse(Files.exists(temp.resolve("evil.jpg")));
    }

    @Test void malformedArchiveCsvCleansItsStagingDirectory() throws Exception {
        Path archive = temp.resolve("malformed.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("data.csv"));
            zip.write("decimalLatitude,decimalLongitude,eventDate\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("photos/voucher.jpg"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        assertThrows(Exception.class, () -> importer.preview(archive, CollectionKind.BOTANICAL));
        try (var files = Files.list(database.root())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("import-staging-")));
        }
    }

    private long localityWithCoordinate() throws Exception {
        return repository.saveLocality(new CollectionRepository.Locality(0, "Carlshemsskogen", "Carlshem",
                "SE", "Sweden", "Västerbotten", "Umeå", "Carlshemsskogen", 63.79881, 20.33348,
                10, CoordinateSource.MAP, "Umeå", 3000, "NW"));
    }

    private long event(long locality, CollectionKind kind) throws Exception {
        return repository.saveEvent(new CollectionRepository.Event(0, locality, kind,
                kind == CollectionKind.INSECT ? "T1" : null, LocalDate.of(2026, 7, 1), null,
                null, null, "Europe/Stockholm", null, null, null, 25.0,
                CoordinateSource.LOCALITY_FALLBACK, kind == CollectionKind.INSECT ? "Slaghåvning" : "Observerad",
                null, kind == CollectionKind.INSECT ? 3 : 0, "forest", null, null), List.of("Nils Ericson"));
    }

    private long determinedSpecimen(long event, String taxon) throws Exception {
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Invertebrates",
                taxon, 1, "Hane", "Imago/Adult", null, null, ReportIntent.INCLUDE, false));
        repository.addDetermination(specimen, taxon, "Nils Ericson", 2026,
                IdentificationKind.DET, false, null);
        return specimen;
    }
}
