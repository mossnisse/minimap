package app.plugin.collection;

import app.db.Database;
import app.repo.PlaceNameRepository;
import gis.ui.CoordinateEntry;
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

    @Test void deletesSpecimensAndEmptyEventsAndFreesTheNumberAgain() throws Exception {
        long locality = localityWithCoordinate();
        long envelope = envelope(locality, "6");
        species(envelope, "Hylocomium splendens");
        long second = species(envelope, "Sphagnum girgensohnii");
        assertEquals("NE6-2", repository.specimen(second).accessionNumber());

        assertEquals(2, repository.eventSpecimenCount(envelope));
        assertTrue(assertThrows(SQLException.class, () -> repository.deleteEvent(envelope)).getMessage().contains("2 specimens"));

        repository.deleteSpecimen(second);
        assertThrows(SQLException.class, () -> repository.specimen(second));
        // The freed number is handed out again - nextNumberFor counts specimens, not the pool.
        assertEquals("NE6-2", repository.specimen(species(envelope, "Sphagnum russowii")).accessionNumber());

        long insect = event(locality, CollectionKind.INSECT);
        repository.deleteEvent(insect);
        assertEquals(1, repository.events("").size());
    }

    @Test void keepsSpecimensThatWereAlreadySentToArtportalen() throws Exception {
        long locality = localityWithCoordinate();
        long specimen = determinedSpecimen(event(locality, CollectionKind.INSECT), "Chyliza vittata");
        ArtportalenExporter exporter = new ArtportalenExporter(repository);
        exporter.exportReady(temp.resolve("export.csv"));

        assertTrue(assertThrows(SQLException.class, () -> repository.deleteSpecimen(specimen)).getMessage().contains("Artportalen"));
        assertNotNull(repository.specimen(specimen));
    }

    @Test void importsTheDyntaxaArchiveAndFindsNamesByPrefix() throws Exception {
        DyntaxaImporter.Result result = importChecklist();
        assertEquals(5, result.scientificNames());
        assertEquals(2, result.vernacularNames());
        assertEquals(7, repository.taxonNameCount());
        assertEquals("7", repository.setting("taxonomy.count", ""));

        assertEquals(List.of("Chyliza", "Chyliza atriseta", "Chyliza fuscipennis", "Chyliza vittata"),
                repository.suggestTaxonNames("chyl", 10).stream().map(CollectionRepository.TaxonName::name).sorted().toList());
        assertTrue(repository.suggestTaxonNames("c", 10).isEmpty(), "one letter is too little to suggest from");
        assertTrue(repository.suggestTaxonNames("zz", 10).isEmpty());
    }

    @Test void pointsASynonymAtItsAcceptedNameAndKeepsTheAcceptedId() throws Exception {
        importChecklist();
        CollectionRepository.TaxonName synonym = repository.taxonNamesExactly("Chyliza atriseta", 4).getFirst();
        assertTrue(synonym.synonym());
        assertEquals("Chyliza vittata", synonym.scientificName());
        assertEquals(100123L, synonym.reportedId(), "a synonym is reported under the taxon it is a synonym of");
        assertEquals("heterotypicSynonym", synonym.status());

        CollectionRepository.TaxonName accepted = repository.taxonNamesExactly("Chyliza vittata", 4).getFirst();
        assertFalse(accepted.synonym(), "Dyntaxa points accepted taxa at themselves; that is not a synonym");
        assertEquals(100123L, accepted.reportedId());
    }

    /**
     * The bug this guards: Barbilophozia barbata (Taxon 2191) was recommended as Caloplaca nivalis,
     * because a name's NamnID was read as if it were a DyntaxaID. A name belongs to the taxon its
     * acceptedNameUsageID names, and its own TaxonName id is not a taxon at all.
     */
    @Test void neverConfusesANameIdWithTheTaxonIdThatSharesItsNumber() throws Exception {
        importChecklist();
        CollectionRepository.TaxonName synonym = repository.taxonNamesExactly("Chyliza fuscipennis", 4).getFirst();
        assertTrue(synonym.synonym());
        assertEquals("Chyliza vittata", synonym.scientificName(), "a name resolves to its own taxon, not to the taxon numbered like the name");
        assertEquals(100123L, synonym.reportedId());
        assertNotEquals(319302L, synonym.reportedId(), "319302 is the NamnID; it must never be stored as a DyntaxaID");

        // The unrelated taxon that happens to carry the same number is untouched by any of it.
        CollectionRepository.TaxonName lichen = repository.taxonNamesExactly("Caloplaca nivalis", 4).getFirst();
        assertFalse(lichen.synonym());
        assertEquals(319302L, lichen.reportedId());
        assertEquals("Caloplaca nivalis", lichen.scientificName());
    }

    @Test void translatesASwedishNameToItsScientificName() throws Exception {
        importChecklist();
        CollectionRepository.TaxonName swedish = repository.taxonNamesExactly("Brännässelfluga", 4).getFirst();
        assertEquals(TaxonNameKind.VERNACULAR, swedish.kind());
        assertEquals("Chyliza vittata", swedish.scientificName());

        // A Swedish name hanging off a synonym still has to land on the accepted scientific name.
        CollectionRepository.TaxonName viaSynonym = repository.taxonNamesExactly("gammal nässelfluga", 4).getFirst();
        assertEquals("Chyliza vittata", viaSynonym.scientificName());
        assertEquals(100123L, viaSynonym.reportedId());
    }

    @Test void storesTheDyntaxaIdOnADeterminationAndStillAcceptsFreeText() throws Exception {
        importChecklist();
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event(localityWithCoordinate(), CollectionKind.INSECT),
                null, "Invertebrates", null, 1, null, null, null, null, ReportIntent.INCLUDE, false));
        repository.addDetermination(specimen, "Chyliza vittata", 100123L, "Nils Ericson", 2026, IdentificationKind.DET, false, null);
        assertEquals(100123L, repository.currentDetermination(specimen).dyntaxaId());

        // Field notes carry names Dyntaxa does not have; refusing them would lose the record.
        repository.addDetermination(specimen, "Chyliza sp.", null, "Nils Ericson", 2026, IdentificationKind.DET, true, null);
        assertEquals("Chyliza sp.", repository.currentDetermination(specimen).taxonName());
        assertNull(repository.currentDetermination(specimen).dyntaxaId());
    }

    @Test void replacesTheWholeNameListOnEveryUpdate() throws Exception {
        importChecklist();
        new DyntaxaImporter(repository).importFrom(dyntaxaArchive("newer.zip",
                "urn:lsid:dyntaxa.se:Taxon:200001\turn:lsid:dyntaxa.se:Taxon:200001\tPolytrichum commune\tHedw.\tspecies\taccepted\n", null));
        assertEquals(1, repository.taxonNameCount());
        assertTrue(repository.taxonNamesExactly("Chyliza vittata", 4).isEmpty(), "the previous checklist must be gone");
        assertEquals("Polytrichum commune", repository.taxonNamesExactly("Polytrichum commune", 4).getFirst().name());
    }

    /**
     * A failed import leaves no checklist rather than half of one. The old list is deliberately not
     * preserved: holding it would mean one transaction over a quarter of a million rows, and that is
     * what grew the store past 800 MB and corrupted a project's specimen records.
     */
    @Test void leavesNoHalfCheckListWhenAnUpdateFails() throws Exception {
        importChecklist();
        assertThrows(SQLException.class, () -> repository.replaceTaxonNames("broken.zip", sink -> {
            sink.add(new CollectionRepository.TaxonName("Fine name", null, 1, TaxonNameKind.SCIENTIFIC, null, null, "species", "accepted"));
            sink.add(new CollectionRepository.TaxonName("x".repeat(400), null, 2, TaxonNameKind.SCIENTIFIC, null, null, "species", "accepted"));
        }));
        assertEquals(0, repository.taxonNameCount());
        assertEquals("0", repository.setting("taxonomy.count", "missing"), "the UI must ask for the checklist again");
        assertTrue(repository.taxonNamesExactly("Fine name", 4).isEmpty(), "no partial list may be suggested");
    }

    /** The reason the archive is read as plain tab-separated text: its files declare no quote character. */
    @Test void readsTabSeparatedNamesThatKeepTheirQuotesAndSemicolons() throws Exception {
        new DyntaxaImporter(repository).importFrom(dyntaxaArchive("quoted.zip",
                "urn:lsid:dyntaxa.se:Taxon:300001\turn:lsid:dyntaxa.se:Taxon:300001\tCarex \"nigra\"\t(L.) Reichard; sensu auct.\tspecies\taccepted\n", null));
        CollectionRepository.TaxonName taxon = repository.taxonNamesExactly("Carex \"nigra\"", 4).getFirst();
        assertEquals("Carex \"nigra\"", taxon.name());
        assertEquals("(L.) Reichard; sensu auct.", taxon.authorship());
    }

    @Test void rejectsAnArchiveWithoutATaxonFile() throws Exception {
        Path archive = temp.resolve("empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("dwca-dyntaxa/meta.xml")); zip.write("<archive/>".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(assertThrows(java.io.IOException.class, () -> new DyntaxaImporter(repository).importFrom(archive))
                .getMessage().contains("Taxon file"));
    }

    /** Exercises the upgrade path itself, including the backup copy no released version had ever taken. */
    @Test void upgradesAVersionOneDatabaseAndKeepsABackupCopy() throws Exception {
        Path project = temp.resolve("upgraded");
        Path base = project.resolve("private_collection/collection");
        try (CollectionDatabase fresh = new CollectionDatabase(project)) { assertEquals(0, new CollectionRepository(fresh).taxonNameCount()); }
        String url = "jdbc:h2:file:" + base.toAbsolutePath().normalize().toString().replace('\\', '/') + ";AUTO_SERVER=FALSE";
        try (Connection c = DriverManager.getConnection(url, "sa", ""); java.sql.Statement s = c.createStatement()) {
            s.execute("DROP TABLE taxon_name");
            s.execute("DELETE FROM schema_version WHERE version=2");
        }
        try (CollectionDatabase reopened = new CollectionDatabase(project)) {
            assertEquals(0, new CollectionRepository(reopened).taxonNameCount(), "the upgrade must recreate the table");
            assertTrue(Files.list(reopened.backupsDirectory()).anyMatch(p -> p.getFileName().toString().startsWith("collection-before-v2-")),
                    "an upgrade copies the database aside first");
        }
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
        repository.addDetermination(specimen, "Chyliza vittata", null, "Nils Ericson", 2026,
                IdentificationKind.DET, false, null);
        ArtportalenExporter exporter = new ArtportalenExporter(repository);
        ArtportalenExporter.PreparedRow prepared = exporter.prepare(specimen);
        assertTrue(prepared.complete(), prepared.warnings().toString());
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

    @Test void exportsIncompleteRecordsAndStillTracksThemAsExported() throws Exception {
        long locality = repository.saveLocality(new CollectionRepository.Locality(0, "Text only place", null,
                "SE", "Sweden", "Västerbotten", "Umeå", null, null, null,
                null, CoordinateSource.TEXT_ONLY, null, null, null));
        long event = event(locality, CollectionKind.INSECT);
        // No determination and no coordinate: the record that used to be unexportable.
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Invertebrates",
                "Chyliza sp.", 1, null, null, null, null, ReportIntent.INCLUDE, false));
        ArtportalenExporter exporter = new ArtportalenExporter(repository);
        ArtportalenExporter.PreparedRow row = exporter.prepare(specimen);
        assertFalse(row.complete());
        assertEquals(ReportStatus.INCOMPLETE, repository.reportStatus(specimen, row.complete(), row.payloadHash()));

        ArtportalenExporter.ExportResult result = exporter.exportReady(temp.resolve("incomplete.csv"));
        assertEquals(List.of(specimen), result.specimenIds());
        assertFalse(result.warnings().isEmpty(), "the gaps are reported as warnings");
        assertEquals(59, Files.readString(result.path(), StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow().split(";", -1).length);
        // The second export must not repeat it, and confirmation must still work.
        assertEquals(ReportStatus.EXPORTED, repository.reportStatus(specimen, row.complete(), row.payloadHash()));
        assertThrows(IllegalStateException.class, () -> exporter.exportReady(temp.resolve("again.csv")));
        exporter.confirmLatestExport();
        assertEquals(ReportStatus.REPORTED, repository.reportStatus(specimen, row.complete(), row.payloadHash()));
    }

    @Test void writesOneNamedRowPerSpecimenInTheSameEvent() throws Exception {
        long event = event(localityWithCoordinate(), CollectionKind.INSECT);
        determinedSpecimen(event, "Chyliza vittata");
        // A sibling in the same event carrying only the working name from the field.
        repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Invertebrates",
                "Chyliza leptogaster", 2, null, null, null, null, ReportIntent.INCLUDE, false));

        ArtportalenExporter.ExportResult result = new ArtportalenExporter(repository).exportReady(temp.resolve("event.csv"));
        assertEquals(2, result.specimenIds().size());
        List<String> names = Files.readString(result.path(), StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split(";", -1)[0]).sorted().toList();
        assertEquals(List.of("Chyliza leptogaster", "Chyliza vittata"), names);
    }

    @Test void rejectsUncertaintyThatArtportalenCannotRepresent() throws Exception {
        long locality = repository.saveLocality(new CollectionRepository.Locality(0, "Broad locality", null,
                "SE", "Sweden", "Västerbotten", "Umeå", null, 63.8, 20.3,
                6001, CoordinateSource.MAP, null, null, null));
        long event = event(locality, CollectionKind.INSECT);
        long specimen = determinedSpecimen(event, "Chyliza vittata");
        ArtportalenExporter.PreparedRow row = new ArtportalenExporter(repository).prepare(specimen);
        assertFalse(row.complete());
        assertTrue(row.warnings().stream().anyMatch(e -> e.contains("5000 m maximum")));
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

    /**
     * Two species from one envelope. Their GPS fix and clock time differ - they always do on real phone
     * data - so the collection number is the only thing that can tie the rows together.
     */
    @Test void importGroupsTwoSpeciesFromOneEnvelopeIntoOneEvent() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        Path plants = temp.resolve("envelope.csv");
        Files.writeString(plants, header
                + "120,64.630081,17.987441,2026-06-23 09:00:12,Sphagnum girgensohnii,Nils Ericson,Åliden,true,6\n"
                + "121,64.630097,17.987466,2026-06-23 09:02:48,Polytrichum commune,Nils Ericson,Åliden,true,6\n");

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));
        assertEquals(1, result.addedEvents(), "one envelope is one collection event");
        assertEquals(2, result.addedSpecimens());
        assertEquals(List.of(), result.warnings());

        long event = repository.events("").getFirst().id();
        assertEquals("NE6", repository.event(event).fieldNumber());
        assertEquals(List.of("NE6", "NE6-2"),
                repository.eventSpecimens(event).stream().map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    /** A later export of the same envelope must extend the existing event, not start a second one. */
    @Test void reimportingAnExtendedExportAddsToTheSameEnvelope() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        String first = "130,64.630081,17.987441,2026-06-23 09:00:12,Sphagnum girgensohnii,Nils Ericson,Åliden,true,6\n";
        String second = "131,64.630097,17.987466,2026-06-23 09:02:48,Polytrichum commune,Nils Ericson,Åliden,true,6\n";
        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        importer.commit(importer.preview(Files.writeString(temp.resolve("first.csv"), header + first), CollectionKind.BOTANICAL));

        SlimeRecordsImporter.ImportResult again = importer.commit(
                importer.preview(Files.writeString(temp.resolve("both.csv"), header + first + second), CollectionKind.BOTANICAL));
        assertEquals(0, again.addedEvents());
        assertEquals(1, again.addedSpecimens());
        assertEquals(1, again.skipped(), "the row already imported is recognised");
        assertEquals(List.of("NE6", "NE6-2"), repository.specimens("").stream()
                .map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    /** The original importer put NE6 on the specimen but left its botanical event unnumbered. */
    @Test void extendingALegacyImportAdoptsItsSpecimenNumberOntoTheEvent() throws Exception {
        long locality = localityWithCoordinate();
        long legacyEvent = repository.saveEvent(new CollectionRepository.Event(0, locality, CollectionKind.BOTANICAL,
                null, LocalDate.of(2026, 6, 23), null, null, null, "Europe/Stockholm", null, null, null,
                25.0, CoordinateSource.LOCALITY_FALLBACK, "Observed", null, 0, "forest", null, null),
                List.of("Nils Ericson"));
        repository.saveSpecimen(new CollectionRepository.Specimen(0, legacyEvent, "NE6", "Mosses",
                "Sphagnum girgensohnii", 1, null, null, null, null, ReportIntent.INCLUDE, false));

        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        String added = "132,64.630097,17.987466,2026-06-23 09:02:48,Polytrichum commune,Nils Ericson,Aliden,true,6\n";
        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(
                Files.writeString(temp.resolve("legacy-extended.csv"), header + added), CollectionKind.BOTANICAL));

        assertEquals(0, result.addedEvents());
        assertEquals(1, result.addedSpecimens());
        assertEquals("NE6", repository.event(legacyEvent).fieldNumber());
        assertEquals(List.of("NE6", "NE6-2"), repository.eventSpecimens(legacyEvent).stream()
                .map(CollectionRepository.SpecimenRow::accessionNumber).toList());
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

    /** One envelope, many species: the number belongs to the collection, the species share it by suffix. */
    @Test void envelopeNumbersDeriveFromTheEventAndNeverRenumber() throws Exception {
        long envelope = envelope(localityWithCoordinate(), "6");
        assertEquals("NE6", repository.event(envelope).fieldNumber(), "a bare 6 is stored as the printed form");

        long first = species(envelope, "Sphagnum girgensohnii");
        assertEquals("NE6", repository.specimen(first).accessionNumber(), "one species keeps the plain number");
        assertEquals("NE6-2", repository.specimen(species(envelope, "Polytrichum commune")).accessionNumber());
        assertEquals("NE6-3", repository.specimen(species(envelope, "Dicranum scoparium")).accessionNumber());
        assertEquals("NE6", repository.specimen(first).accessionNumber(), "printed labels must stay valid");

        assertEquals(List.of("NE6", "NE6-2", "NE6-3"),
                repository.eventSpecimens(envelope).stream().map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    @Test void aSingleSpeciesEnvelopeKeepsThePlainNumberOnItsLabel() throws Exception {
        long specimen = species(envelope(localityWithCoordinate(), "7"), "Hylocomium splendens");
        String html = new LabelGenerator(repository).botanicalLabel(specimen).bodyHtml();
        assertTrue(html.contains("NE7"), () -> "expected NE7 on the label: " + html);
        assertFalse(html.contains("NE7-"), () -> "a lone species must not be suffixed: " + html);
    }

    /** An insect tube number is not a collection number, so pinned specimens still draw from the counter. */
    @Test void insectTubeNumbersStillDrawFromTheGlobalCounter() throws Exception {
        long tube = event(localityWithCoordinate(), CollectionKind.INSECT);
        assertEquals("T1", repository.event(tube).fieldNumber(), "a tube number is kept verbatim, not prefixed");
        assertEquals(List.of("NE1", "NE2", "NE3"), repository.createSpecimenBatch(tube, 3).stream()
                .map(id -> { try { return repository.specimen(id).accessionNumber(); } catch (SQLException e) { throw new RuntimeException(e); } }).toList());
    }

    /** The envelope owns its number even before the first species has been added to it. */
    @Test void aWrittenEnvelopeNumberPushesTheGlobalCounterPastIt() throws Exception {
        envelope(localityWithCoordinate(), "250");
        assertEquals(List.of("NE251"), repository.reserveNumbers(1));
    }

    @Test void aPopulatedEnvelopeCannotBeRenumbered() throws Exception {
        long id = envelope(localityWithCoordinate(), "6");
        species(id, "Cladonia rangiferina");
        CollectionRepository.Event old = repository.event(id);

        SQLException error = assertThrows(SQLException.class, () -> repository.saveEvent(
                eventWithNumber(old, "7"), repository.collectors(id)));

        assertTrue(error.getMessage().contains("cannot be changed"));
        assertEquals("NE6", repository.event(id).fieldNumber());
        assertEquals(List.of("NE6"), repository.eventSpecimens(id).stream()
                .map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    @Test void aBotanicalSpecimenCannotBeMovedBetweenEnvelopes() throws Exception {
        long six = envelope(localityWithCoordinate(), "6");
        long specimenId = species(six, "Cladonia rangiferina");
        long seven = envelope(localityWithCoordinate(), "7");
        CollectionRepository.Specimen old = repository.specimen(specimenId);

        SQLException error = assertThrows(SQLException.class, () -> repository.saveSpecimen(
                specimenAtEvent(old, seven)));

        assertTrue(error.getMessage().contains("cannot be moved"));
        assertEquals(six, repository.specimen(specimenId).eventId());
        assertEquals("NE6", repository.specimen(specimenId).accessionNumber());
    }

    @Test void anUnrelatedAccessionPrefixDoesNotAdvanceTheGlobalCounter() throws Exception {
        long event = event(localityWithCoordinate(), CollectionKind.INSECT);
        repository.saveSpecimen(new CollectionRepository.Specimen(0, event, "X2026", "Invertebrates",
                "Test species", 1, null, null, null, null, ReportIntent.INCLUDE, false));
        assertEquals(List.of("NE1"), repository.reserveNumbers(1));
    }

    /** Guards the match against H2's constraint name - a rename there must not leak a raw SQL message. */
    @Test void reusingAnEnvelopeNumberOnAnotherCollectionSaysSoPlainly() throws Exception {
        long locality = localityWithCoordinate();
        envelope(locality, "6");
        assertTrue(assertThrows(SQLException.class, () -> envelope(locality, "6")).getMessage()
                .contains("Collection number already used by another collection: NE6"));
    }

    @Test void collectionNumbersSortNumericallyNotLexically() throws Exception {
        long locality = localityWithCoordinate();
        long six = envelope(locality, "6");
        species(six, "Sphagnum girgensohnii"); species(six, "Polytrichum commune");
        for (int i = 3; i <= 10; i++) species(six, "Species " + i);
        species(envelope(locality, "10"), "Dicranum scoparium");
        species(envelope(locality, "2"), "Pleurozium schreberi");

        assertEquals(List.of("NE2", "NE6", "NE6-2", "NE6-3", "NE6-4", "NE6-5", "NE6-6",
                        "NE6-7", "NE6-8", "NE6-9", "NE6-10", "NE10"),
                repository.specimens("").stream().map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    /** The phone app reuses collection numbers and marks observations, so neither may break a whole import. */
    @Test void reusedCollectionNumberGetsAFreshOneAndObservationsGetNone() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        Path plants = temp.resolve("reused.csv");
        Files.writeString(plants, header
                + "110,64.63008,17.98744,2026-06-23 09:00:00,Citronticka,Nils Ericson,Grössjön,true,6\n"
                + "111,64.61014,17.99316,2026-06-22 16:19:17,Klubbmurkling,Nils Ericson,Bäckmyran,true,6\n"
                + "112,64.60000,17.90000,2026-06-21 08:00:00,Bergabrant,Nils Ericson,Stensele,false,\n");

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));
        assertEquals(3, result.addedEvents());
        assertEquals(2, result.addedSpecimens(), "the isSpecimen=false row must not consume a collection number");
        assertEquals(1, result.warnings().size(), () -> "expected one reuse warning, got " + result.warnings());

        List<String> numbers = repository.specimens("").stream().map(CollectionRepository.SpecimenRow::accessionNumber).sorted().toList();
        assertEquals(2, numbers.size());
        assertTrue(numbers.contains("NE6"), () -> "first row keeps its number: " + numbers);
        assertEquals(2, numbers.stream().distinct().count(), () -> "the reused number must not be handed out twice: " + numbers);
    }

    @Test void twoSpeciesFromAReusedEnvelopeShareOneReplacementEvent() throws Exception {
        long oldEnvelope = envelope(localityWithCoordinate(), "6");
        species(oldEnvelope, "Old collection");
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        Path plants = Files.writeString(temp.resolve("reused-envelope.csv"), header
                + "140,64.61014,17.99316,2026-06-22 16:19:17,First species,Nils Ericson,Backmyran,true,6\n"
                + "141,64.61018,17.99320,2026-06-22 16:22:03,Second species,Nils Ericson,Backmyran,true,6\n");

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));

        assertEquals(1, result.addedEvents());
        assertEquals(2, result.addedSpecimens());
        assertEquals(1, result.warnings().size(), "one replacement number is allocated for the envelope");
        long replacement = repository.events("").stream()
                .filter(e -> LocalDate.of(2026, 6, 22).equals(e.date())).findFirst().orElseThrow().id();
        assertEquals(List.of("NE7", "NE7-2"), repository.eventSpecimens(replacement).stream()
                .map(CollectionRepository.SpecimenRow::accessionNumber).toList());
    }

    @Test void replacementNumberDoesNotAbsorbALaterSourceEnvelope() throws Exception {
        long oldEnvelope = envelope(localityWithCoordinate(), "6");
        species(oldEnvelope, "Old collection");
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,recordedBy,locality,isSpecimen,SpecimenNr\n";
        Path plants = Files.writeString(temp.resolve("two-new-envelopes.csv"), header
                + "150,64.61014,17.99316,2026-06-22 16:19:17,First species,Nils Ericson,Backmyran,true,6\n"
                + "151,64.61118,17.99420,2026-06-22 16:22:03,Second species,Nils Ericson,Other mire,true,7\n");

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        SlimeRecordsImporter.ImportResult result = importer.commit(importer.preview(plants, CollectionKind.BOTANICAL));

        assertEquals(2, result.addedEvents(), "source envelopes 6 and 7 are distinct collections");
        assertEquals(2, result.addedSpecimens());
        assertEquals(List.of("NE7", "NE8"), repository.specimens("").stream()
                .map(CollectionRepository.SpecimenRow::accessionNumber)
                .filter(number -> !number.equals("NE6")).toList());
        assertEquals(1, repository.events("").stream().filter(e -> "NE7".equals(e.fieldNumber())).count());
        assertEquals(1, repository.events("").stream().filter(e -> "NE8".equals(e.fieldNumber())).count());
    }

    /** The extraction guard is a ratio, not an absolute size: same payload, only the bomb is rejected. */
    @Test void archiveGuardRejectsHighExpansionButAllowsIncompressiblePhotos() throws Exception {
        String header = "ID,decimalLatitude,decimalLongitude,eventDate,taxonName,locality\n";
        String row = "30,63.79881,20.33348,2026-07-01,Chyliza vittata,Carlshemsskogen\n";
        byte[] incompressible = new byte[12 * 1024 * 1024];
        new java.util.Random(7).nextBytes(incompressible);
        byte[] zeros = new byte[incompressible.length];

        SlimeRecordsImporter importer = new SlimeRecordsImporter(repository);
        try (var preview = importer.preview(photoArchive(temp.resolve("real.zip"), header + row, incompressible), CollectionKind.INSECT)) {
            assertEquals(1, preview.rows().size());
        }
        Path bomb = photoArchive(temp.resolve("bomb.zip"), header + row, zeros);
        assertTrue(Files.size(bomb) < zeros.length / 10, "zeros must compress far past the ratio limit");
        assertThrows(Exception.class, () -> importer.preview(bomb, CollectionKind.INSECT));
    }

    private static Path photoArchive(Path target, String csv, byte[] photo) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry("data.csv")); zip.write(csv.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("photos/voucher.jpg")); zip.write(photo); zip.closeEntry();
        }
        return target;
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

    /**
     * A stand-in for the Dyntaxa archive: the files are named in mixed case inside a folder, exactly
     * as the real download lays them out, so the importer's filename matching is exercised.
     */
    private Path dyntaxaArchive(String file, String taxonRows, String vernacularRows) throws Exception {
        Path archive = temp.resolve(file);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("dwca-dyntaxa/meta.xml"));
            zip.write("<archive/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("dwca-dyntaxa/Taxon.csv"));
            zip.write(("taxonId\tacceptedNameUsageID\tscientificName\tscientificNameAuthorship\ttaxonRank\ttaxonomicStatus\n" + taxonRows)
                    .getBytes(StandardCharsets.UTF_8));
            if (vernacularRows != null) {
                zip.putNextEntry(new ZipEntry("dwca-dyntaxa/VernacularName.csv"));
                zip.write(("taxonId\tvernacularName\tlanguage\tisPreferredName\n" + vernacularRows).getBytes(StandardCharsets.UTF_8));
            }
        }
        return archive;
    }

    /**
     * The checklist every taxonomy test starts from. It carries the shape that matters: Dyntaxa writes
     * a taxon's other names in the TaxonName namespace, whose numbers run independently of the Taxon
     * ones - here TaxonName:319302 and Taxon:319302 are deliberately the same number for two unrelated
     * organisms, because reading one as the other is what made a liverwort a synonym of a lichen.
     */
    private DyntaxaImporter.Result importChecklist() throws Exception {
        String taxa = "urn:lsid:dyntaxa.se:Taxon:100123\turn:lsid:dyntaxa.se:Taxon:100123\tChyliza vittata\t(Meigen, 1826)\tspecies\taccepted\n"
                + "urn:lsid:dyntaxa.se:TaxonName:319302\turn:lsid:dyntaxa.se:Taxon:100123\tChyliza fuscipennis\tZetterstedt, 1847\tspecies\thomotypicSynonym\n"
                + "urn:lsid:dyntaxa.se:Taxon:319302\turn:lsid:dyntaxa.se:Taxon:319302\tCaloplaca nivalis\t(Körb.) Th.Fr.\tspecies\taccepted\n"
                + "urn:lsid:dyntaxa.se:Taxon:100124\turn:lsid:dyntaxa.se:Taxon:100123\tChyliza atriseta\tLoew, 1866\tspecies\theterotypicSynonym\n"
                + "urn:lsid:dyntaxa.se:Taxon:100200\turn:lsid:dyntaxa.se:Taxon:100200\tChyliza\tFallén, 1820\tgenus\taccepted\n";
        String names = "urn:lsid:dyntaxa.se:Taxon:100123\tbrännässelfluga\tsv\ttrue\n"
                + "urn:lsid:dyntaxa.se:Taxon:100124\tgammal nässelfluga\tsv\tfalse\n";
        return new DyntaxaImporter(repository).importFrom(dyntaxaArchive("dyntaxa.zip", taxa, names));
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

    /** A botanical collection event - the paper envelope - carrying the number written on it. */
    private long envelope(long locality, String number) throws Exception {
        return repository.saveEvent(new CollectionRepository.Event(0, locality, CollectionKind.BOTANICAL,
                number, LocalDate.of(2026, 7, 1), null, null, null, "Europe/Stockholm", null, null, null,
                25.0, CoordinateSource.LOCALITY_FALLBACK, "Observerad", null, 0, "forest", null, null),
                List.of("Nils Ericson"));
    }

    /** One species inside an envelope; a null number means the repository derives it from the event. */
    private long species(long event, String taxon) throws Exception {
        return repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Mossor",
                taxon, 1, null, null, null, null, ReportIntent.INCLUDE, false));
    }

    private static CollectionRepository.Event eventWithNumber(CollectionRepository.Event event, String number) {
        return new CollectionRepository.Event(event.id(), event.localityId(), event.kind(), number,
                event.startDate(), event.endDate(), event.localTime(), event.endTime(), event.timezone(),
                event.latitude(), event.longitude(), event.uncertaintyMeters(), event.elevationMeters(),
                event.coordinateSource(), event.method(), event.trapNumber(), event.expectedCount(),
                event.habitat(), event.notes(), event.preliminaryTaxon());
    }

    private static CollectionRepository.Specimen specimenAtEvent(CollectionRepository.Specimen specimen, long eventId) {
        return new CollectionRepository.Specimen(specimen.id(), eventId, specimen.accessionNumber(), specimen.taxonGroup(),
                specimen.preliminaryTaxon(), specimen.quantity(), specimen.sex(), specimen.lifeStage(), specimen.substrate(),
                specimen.comments(), specimen.reportIntent(), specimen.archived());
    }

    private long determinedSpecimen(long event, String taxon) throws Exception {
        long specimen = repository.saveSpecimen(new CollectionRepository.Specimen(0, event, null, "Invertebrates",
                taxon, 1, "Hane", "Imago/Adult", null, null, ReportIntent.INCLUDE, false));
        repository.addDetermination(specimen, taxon, null, "Nils Ericson", 2026,
                IdentificationKind.DET, false, null);
        return specimen;
    }
}
