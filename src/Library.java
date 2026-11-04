import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Aggregate root and in-memory repository behind LibraryService.
// Registry/index changes are centralized here rather than duplicated in controllers.
public final class Library {
    private final String name;
    private final List<LibraryBook> books = new ArrayList<>();
    private final Map<String, LibraryBook> booksById = new LinkedHashMap<>();
    private final Map<String, Set<LibraryBook>> booksByCategory = new LinkedHashMap<>();
    private final Map<String, Reader> readersById = new LinkedHashMap<>();
    private final Map<String, Librarian> staffById = new LinkedHashMap<>();
    private final Map<String, Set<String>> fansByBook = new LinkedHashMap<>();
    private final ActivityLog activityLog = new ActivityLog();
    private final BorrowRequestQueue borrowRequests = new BorrowRequestQueue();
    private final PriorityTaskDispatcher taskDispatcher = new PriorityTaskDispatcher();

    public Library(String name) { this.name = LibraryBook.requireText(name, "Library name"); }
    public String getName() { return name; }
    public List<LibraryBook> getBooks() { return List.copyOf(books); }
    public List<Reader> getReaders() { return List.copyOf(readersById.values()); }
    public List<Librarian> getLibrarians() { return List.copyOf(staffById.values()); }
    public List<String> getActivityHistory() { return activityLog.getEntries(); }
    public BorrowRequestQueue getBorrowRequests() { return borrowRequests; }
    public PriorityTaskDispatcher getTaskDispatcher() { return taskDispatcher; }

    // Hash-based lookup is expected O(1); it is not a guaranteed worst-case bound.
    public LibraryBook findBook(String id) {
        return booksById.get(LibraryBook.requireText(id, "Inventory ID"));
    }
    public Reader findReader(String id) {
        return readersById.get(LibraryBook.requireText(id, "Reader ID"));
    }
    public Librarian findLibrarian(String id) {
        return staffById.get(LibraryBook.requireText(id, "Staff ID"));
    }
    public Map<String, Set<LibraryBook>> getCategoryIndex() {
        Map<String, Set<LibraryBook>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<LibraryBook>> entry : booksByCategory.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
    public Set<String> getCategoryNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(booksByCategory.keySet()));
    }
    public Set<LibraryBook> findBooksByCategory(String name) {
        Set<LibraryBook> result = booksByCategory.get(LibraryBook.requireText(name, "Category"));
        return result == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(result));
    }
    public Set<String> getFansOfBook(String id) {
        Set<String> fans = fansByBook.get(id);
        return fans == null ? Set.of() : Set.copyOf(fans);
    }
    private String category(LibraryBook book) { return book.getClass().getSimpleName(); }

    public void addBook(LibraryBook book) {
        Objects.requireNonNull(book, "Book cannot be null.");
        if (booksById.containsKey(book.getInventoryId())) {
            throw new IllegalArgumentException("Inventory ID already exists.");
        }
        books.add(book);
        booksById.put(book.getInventoryId(), book);
        booksByCategory.computeIfAbsent(category(book), key -> new LinkedHashSet<>()).add(book);
        recordActivity("Added book " + book.getInventoryId());
    }
    public void registerReader(Reader reader) {
        Objects.requireNonNull(reader, "Reader cannot be null.");
        if (readersById.putIfAbsent(reader.getReaderId(), reader) != null) {
            throw new IllegalArgumentException("Reader ID already exists.");
        }
        recordActivity("Registered reader " + reader.getReaderId());
    }
    public void registerLibrarian(Librarian staff) {
        Objects.requireNonNull(staff, "Librarian cannot be null.");
        if (staffById.putIfAbsent(staff.getStaffId(), staff) != null) {
            throw new IllegalArgumentException("Staff ID already exists.");
        }
        recordActivity("Registered librarian " + staff.getStaffId());
    }
    public void updateBook(String id, String title, String author, String isbn, int year) {
        LibraryBook book = requiredBook(id);
        String t = LibraryBook.requireText(title, "Title");
        String a = LibraryBook.requireText(author, "Author");
        String i = LibraryBook.requireText(isbn, "ISBN");
        if (year < 1 || year > java.time.Year.now().getValue()) {
            throw new IllegalArgumentException("Publication year is invalid.");
        }
        // Validate every input before touching the live indexed object.
        book.setTitle(t); book.setAuthor(a); book.setIsbn(i); book.setPublicationYear(year);
        recordActivity("Updated book " + id);
    }
    public boolean removeBook(String id) {
        id = LibraryBook.requireText(id, "Inventory ID");
        LibraryBook book = findBook(id);
        if (book == null) { return false; }
        if (book instanceof CirculatingBook && !((CirculatingBook) book).isAvailable()) {
            throw new IllegalStateException("Cannot remove a borrowed book.");
        }
        if (book instanceof ReferenceBook && ((ReferenceBook) book).isInUse()) {
            throw new IllegalStateException("Cannot remove a book being read.");
        }
        books.remove(book);
        booksById.remove(id);
        Set<LibraryBook> categoryBooks = booksByCategory.get(category(book));
        categoryBooks.remove(book);
        if (categoryBooks.isEmpty()) { booksByCategory.remove(category(book)); }
        Set<String> fans = fansByBook.remove(id);
        if (fans != null) {
            for (String readerId : fans) { readersById.get(readerId).removeFavorite(id); }
        }
        for (BorrowRequest request : borrowRequests.getPendingRequests()) {
            if (id.equals(request.getInventoryId())) { recordActivity("Request cancelled " + request + " | COPY REMOVED"); }
        }
        int cancelled = borrowRequests.cancelForBook(id) + taskDispatcher.cancelForBook(id);
        recordActivity("Removed book " + id + "; cancelled pending items=" + cancelled);
        return true;
    }
    public boolean removeReader(String id) {
        id = LibraryBook.requireText(id, "Reader ID");
        Reader reader = findReader(id);
        if (reader == null) { return false; }
        for (LibraryBook book : books) {
            if (book instanceof CirculatingBook
                    && id.equals(((CirculatingBook) book).getBorrowerId())
                    || book instanceof ReferenceBook
                    && id.equals(((ReferenceBook) book).getCurrentReaderId())) {
                throw new IllegalStateException("Reader still has an active loan or reading session.");
            }
        }
        for (String bookId : new LinkedHashSet<>(reader.getFavoriteBookIds())) { removeFavorite(reader, bookId); }
        readersById.remove(id);
        int cancelled = borrowRequests.cancelForReader(id);
        recordActivity("Removed reader " + id + "; cancelled requests=" + cancelled);
        return true;
    }
    public boolean removeLibrarian(String id) {
        id = LibraryBook.requireText(id, "Staff ID");
        if (!staffById.containsKey(id)) { return false; }
        if (staffById.size() == 1) { throw new IllegalStateException("The last librarian cannot be removed."); }
        staffById.remove(id); recordActivity("Removed librarian " + id); return true;
    }
    public void addFavorite(Reader reader, String bookId) {
        requireReader(reader); LibraryBook book = requiredBook(bookId);
        reader.addFavorite(book.getInventoryId());
        fansByBook.computeIfAbsent(book.getInventoryId(), key -> new LinkedHashSet<>()).add(reader.getReaderId());
        recordActivity("Favorite added " + reader.getReaderId() + " -> " + book.getInventoryId());
    }
    public void removeFavorite(Reader reader, String bookId) {
        bookId = LibraryBook.requireText(bookId, "Inventory ID");
        requireReader(reader);
        reader.removeFavorite(bookId);
        Set<String> fans = fansByBook.get(bookId);
        if (fans != null) {
            fans.remove(reader.getReaderId());
            if (fans.isEmpty()) { fansByBook.remove(bookId); }
        }
        recordActivity("Favorite removed " + reader.getReaderId() + " -> " + bookId);
    }
    public List<LibraryBook> getFavorites(Reader reader) {
        requireReader(reader);
        List<LibraryBook> result = new ArrayList<>();
        for (String id : reader.getFavoriteBookIds()) { result.add(booksById.get(id)); }
        result.sort(BookComparators.BY_TITLE); return result;
    }
    public void requireReader(Reader reader) {
        if (reader == null || readersById.get(reader.getReaderId()) != reader) {
            throw new IllegalArgumentException("Use the reader registered in this library.");
        }
    }
    public void requireLibrarian(Librarian staff) {
        if (staff == null || staffById.get(staff.getStaffId()) != staff) {
            throw new IllegalArgumentException("A registered librarian is required.");
        }
    }
    public LibraryBook requiredBook(String id) {
        LibraryBook book = findBook(id);
        if (book == null) { throw new IllegalArgumentException("Book was not found."); }
        return book;
    }
    public Map<String, Integer> statistics() {
        Map<String, Integer> result = new LinkedHashMap<>();
        int loans = 0, reading = 0, overdue = 0;
        for (LibraryBook book : books) {
            if (book instanceof CirculatingBook && !((CirculatingBook) book).isAvailable()) {
                loans++;
                if (((CirculatingBook) book).getDueDate().isBefore(LocalDate.now())) { overdue++; }
            }
            if (book instanceof ReferenceBook && ((ReferenceBook) book).isInUse()) { reading++; }
        }
        result.put("Books", books.size()); result.put("Readers", readersById.size());
        result.put("Librarians", staffById.size()); result.put("Active home loans", loans);
        result.put("Overdue home loans", overdue); result.put("Reading sessions", reading);
        result.put("Available home copies", BookFilter.availableForLoan(books).size());
        result.put("Pending requests", borrowRequests.size()); result.put("Pending tasks", taskDispatcher.size());
        result.put("Favorite links", fansByBook.values().stream().mapToInt(Set::size).sum());
        return result;
    }
    public boolean checkConsistency() {
        if (books.size() != booksById.size()) { return false; }
        Set<LibraryBook> grouped = new LinkedHashSet<>();
        for (Map.Entry<String, Set<LibraryBook>> group : booksByCategory.entrySet()) {
            for (LibraryBook book : group.getValue()) {
                if (!group.getKey().equals(category(book)) || booksById.get(book.getInventoryId()) != book) { return false; }
                grouped.add(book);
            }
        }
        if (grouped.size() != books.size()) { return false; }
        for (LibraryBook book : books) { if (booksById.get(book.getInventoryId()) != book) { return false; } }
        for (Reader reader : readersById.values()) {
            for (String id : reader.getFavoriteBookIds()) {
                if (!booksById.containsKey(id) || !getFansOfBook(id).contains(reader.getReaderId())) { return false; }
            }
        }
        for (Map.Entry<String, Set<String>> entry : fansByBook.entrySet()) {
            if (!booksById.containsKey(entry.getKey())) { return false; }
            for (String readerId : entry.getValue()) {
                Reader reader = readersById.get(readerId);
                if (reader == null || !reader.getFavoriteBookIds().contains(entry.getKey())) { return false; }
            }
        }
        return true;
    }
    void recordActivity(String action) { activityLog.record(action); }
    void restoreHistory(List<String> entries) { activityLog.restoreEntries(entries); }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof Library && name.equals(((Library) other).name);
    }
    @Override public int hashCode() { return name.hashCode(); }
}
