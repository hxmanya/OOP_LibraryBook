import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

// A Library creates and owns this object; it never exposes the log itself.
public final class ActivityLog {
    private final List<String> entries = new ArrayList<>();
    private final String logId = UUID.randomUUID().toString();

    ActivityLog() { }

    void record(String action) {
        entries.add(Instant.now() + " | " + LibraryBook.requireText(action, "Action"));
    }

    public List<String> getEntries() {
        return List.copyOf(entries);
    }

    void restoreEntries(List<String> saved) {
        entries.clear();
        entries.addAll(saved);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ActivityLog
                && logId.equals(((ActivityLog) other).logId);
    }

    @Override
    public int hashCode() { return logId.hashCode(); }
}
