package app.project;

/** A project-owned editor or dialog that may contain unsaved work. */
public interface ProjectCloseParticipant {
    String description();
    boolean isDirty();
    boolean save();
    void discard();
    void close();
}
