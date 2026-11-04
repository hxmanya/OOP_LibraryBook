import java.time.Year;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BookFilter {
    private BookFilter() { }

    public static List<LibraryBook> byTitle(List<LibraryBook> books, String text) {
        Objects.requireNonNull(books, "Books cannot be null.");
        String query = LibraryBook.requireText(text, "Title query").toLowerCase(Locale.ROOT);
        List<LibraryBook> result = new ArrayList<>();
        for (LibraryBook book : books) {
            if (book.getTitle().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(book);
            }
        }
        return result;
    }

    public static List<LibraryBook> byAuthor(List<LibraryBook> books, String text) {
        Objects.requireNonNull(books, "Books cannot be null.");
        String query = LibraryBook.requireText(text, "Author query").toLowerCase(Locale.ROOT);
        List<LibraryBook> result = new ArrayList<>();
        for (LibraryBook book : books) {
            if (book.getAuthor().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(book);
            }
        }
        return result;
    }

    public static List<LibraryBook> publishedFrom(List<LibraryBook> books, int year) {
        Objects.requireNonNull(books, "Books cannot be null.");
        if (year < 1 || year > Year.now().getValue()) {
            throw new IllegalArgumentException("Filter year is invalid.");
        }
        List<LibraryBook> result = new ArrayList<>();
        for (LibraryBook book : books) {
            if (book.getPublicationYear() >= year) { result.add(book); }
        }
        return result;
    }

    public static List<LibraryBook> availableForLoan(List<LibraryBook> books) {
        Objects.requireNonNull(books, "Books cannot be null.");
        // Remove from a local copy, never from the library catalog.
        List<LibraryBook> result = new ArrayList<>(books);
        Iterator<LibraryBook> iterator = result.iterator();
        while (iterator.hasNext()) {
            LibraryBook book = iterator.next();
            if (!(book instanceof CirculatingBook)
                    || !((CirculatingBook) book).isAvailable()) {
                iterator.remove();
            }
        }
        return result;
    }
}
