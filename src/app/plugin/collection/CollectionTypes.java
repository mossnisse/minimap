package app.plugin.collection;

/** Shared domain vocabulary for the private-collection plugin. */
public final class CollectionTypes {
    private CollectionTypes() {}

    public enum CollectionKind { INSECT, BOTANICAL }
    public enum CoordinateSource { PHONE, MAP, MANUAL, LOCALITY_FALLBACK, TEXT_ONLY }
    public enum IdentificationKind { DET, CONF }
    public enum ReportIntent { INCLUDE, EXCLUDE }
    public enum ReportStatus { EXCLUDED, INCOMPLETE, READY, EXPORTED, REPORTED, UPDATE_NEEDED }
    public enum LabelType { EVENT, DETERMINATION, ACCESSION, BOTANICAL }
    public enum LabelStatus { NOT_PRINTED, PRINTED, CHANGED }
    /** Which kind of name a taxon_name row holds - the scientific one, or a vernacular ("Swedish") one. */
    public enum TaxonNameKind { SCIENTIFIC, VERNACULAR }
}
