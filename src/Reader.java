import java.util.LinkedHashSet;
import java.util.Set;

public final class Reader {
    private final String readerId;
    private String fullName;
    private final Set<String> favoriteBookIds = new LinkedHashSet<>();

    public Reader(String readerId, String fullName) {
        this.readerId = LibraryBook.requireText(readerId, "Reader ID");
        this.fullName = LibraryBook.requireText(fullName, "Reader name");
    }

    public String getReaderId() { return readerId; }
    public String getFullName() { return fullName; }
    public void setFullName(String name) { fullName = LibraryBook.requireText(name, "Reader name"); }
    public Set<String> getFavoriteBookIds() { return Set.copyOf(favoriteBookIds); }
    void addFavorite(String id) { favoriteBookIds.add(id); }
    void removeFavorite(String id) { favoriteBookIds.remove(id); }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Reader
                && readerId.equals(((Reader) other).readerId);
    }

    @Override
    public int hashCode() { return readerId.hashCode(); }

    @Override
    public String toString() {
        return readerId + " - " + fullName;
    }
}
