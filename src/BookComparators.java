import java.util.Comparator;

public final class BookComparators {
    private BookComparators() { }

    public static final Comparator<LibraryBook> BY_TITLE = Comparator
            .comparing(LibraryBook::getTitle, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(LibraryBook::getInventoryId);
    public static final Comparator<LibraryBook> BY_YEAR_NEWEST = Comparator
            .comparingInt(LibraryBook::getPublicationYear).reversed()
            .thenComparing(BY_TITLE);
    public static final Comparator<LibraryBook> BY_AUTHOR_TITLE = Comparator
            .comparing(LibraryBook::getAuthor, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(BY_TITLE);
}
