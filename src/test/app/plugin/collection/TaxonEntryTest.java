package app.plugin.collection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static app.plugin.collection.CollectionTypes.TaxonNameKind;
import static org.junit.jupiter.api.Assertions.*;

/** Typing a species name against the Dyntaxa checklist. Headless: the suggestion popup is never shown. */
class TaxonEntryTest {
    @TempDir Path temp;
    private CollectionDatabase database;
    private CollectionRepository repository;
    private TaxonEntry entry;
    private JTextField field;
    private JLabel status;

    @BeforeEach void open() throws Exception {
        database = CollectionDatabase.inMemory("taxon_" + UUID.randomUUID(), temp.resolve("collection"));
        repository = new CollectionRepository(database);
        entry = new TaxonEntry(repository, "Taxon");
        // The row order is part of formRows()'s contract: label, field, spacer, status.
        Object[] rows = entry.formRows();
        field = (JTextField) rows[1];
        status = (JLabel) rows[3];
    }

    @AfterEach void close() throws Exception { database.close(); }

    private void checklist() throws Exception {
        repository.replaceTaxonNames("test.zip", sink -> {
            sink.add(new CollectionRepository.TaxonName("Chyliza vittata", "(Meigen, 1826)", 100123,
                    TaxonNameKind.SCIENTIFIC, null, null, "species", "accepted"));
            sink.add(new CollectionRepository.TaxonName("Chyliza atriseta", "Loew, 1866", 100124,
                    TaxonNameKind.SCIENTIFIC, 100123L, "Chyliza vittata", "species", "heterotypicSynonym"));
            sink.add(new CollectionRepository.TaxonName("brännässelfluga", null, 100123,
                    TaxonNameKind.VERNACULAR, 100123L, "Chyliza vittata", "species", null));
            // Dyntaxa is not unique about every name, and guessing one of these would store the wrong id.
            sink.add(new CollectionRepository.TaxonName("Tvillingnamn", null, 500001,
                    TaxonNameKind.SCIENTIFIC, null, null, "species", "accepted"));
            sink.add(new CollectionRepository.TaxonName("Tvillingnamn", null, 500002,
                    TaxonNameKind.SCIENTIFIC, null, null, "species", "accepted"));
        });
    }

    @Test void saysTheSpeciesListIsEmptyBeforeAnyImport() {
        entry.setName("Chyliza vittata");
        assertTrue(status.getText().contains("No species list imported"), status.getText());
        assertEquals("Chyliza vittata", entry.name(), "the name is kept whatever the list says");
        assertNull(entry.dyntaxaId());
    }

    @Test void keepsFreeTextThatIsNotInDyntaxaAndReportsNoId() throws Exception {
        checklist();
        entry.setName("Chyliza sp.");
        assertEquals("Chyliza sp.", entry.name());
        assertNull(entry.dyntaxaId());
        assertTrue(status.getText().contains("Not in Dyntaxa"), status.getText());
    }

    @Test void namesTheAcceptedTaxonWithItsId() throws Exception {
        checklist();
        entry.setName("chyliza vittata");
        assertEquals(100123L, entry.dyntaxaId(), "matching ignores case");
        assertTrue(status.getText().contains("Dyntaxa 100123"), status.getText());
    }

    @Test void showsTheAcceptedNameWhenASynonymIsTyped() throws Exception {
        checklist();
        entry.setName("Chyliza atriseta");
        assertTrue(status.getText().startsWith("Synonym (heterotypicSynonym)"), status.getText());
        assertTrue(status.getText().contains("Chyliza vittata"), status.getText());
        assertEquals(100123L, entry.dyntaxaId(), "a synonym is recorded under the taxon it is a synonym of");
        assertEquals("Chyliza atriseta", entry.name(), "typing a synonym must not rewrite what was typed");
    }

    @Test void replacesASwedishNameWithTheScientificOneWhenTheSuggestionIsPicked() throws Exception {
        checklist();
        List<CollectionRepository.TaxonName> found = entry.lookup("bränn");
        assertEquals(1, found.size());
        assertEquals(TaxonNameKind.VERNACULAR, found.getFirst().kind());
        entry.choose(found.getFirst());
        assertEquals("Chyliza vittata", entry.name());
        assertEquals(100123L, entry.dyntaxaId());
    }

    @Test void forgetsTheIdWhenThePickedNameIsEditedAfterwards() throws Exception {
        checklist();
        entry.choose(entry.lookup("chyliza v").getFirst());
        assertEquals(100123L, entry.dyntaxaId());
        field.setText(entry.name() + " agg.");
        assertNull(entry.dyntaxaId(), "an edited name must not keep the id of the one that was picked");
    }

    @Test void storesNoIdWhileANameIsAmbiguous() throws Exception {
        checklist();
        entry.setName("Tvillingnamn");
        assertNull(entry.dyntaxaId());
        assertTrue(status.getText().startsWith("2 taxa in Dyntaxa"), status.getText());

        // Picking one of them settles it.
        entry.choose(entry.lookup("tvilling").getFirst());
        assertNotNull(entry.dyntaxaId());
    }

    @Test void suggestsScientificNamesBeforeSynonymsAndVernacularOnes() throws Exception {
        checklist();
        assertEquals(List.of("Chyliza vittata", "Chyliza atriseta"),
                entry.lookup("chyliza").stream().map(CollectionRepository.TaxonName::name).toList());
    }
}
