import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

// Flat src/ structure and the earlier demonstration entry points are preserved.
public final class LibraryApplication {
    private final Scanner input = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
    private final LibraryCsvStore store = new LibraryCsvStore();
    private final LibraryService service = new LibraryService();
    private final Path directory;
    private Library library;

    private LibraryApplication(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--demo")) { SIW1Demo.main(new String[0]); return; }
        Path directory = args.length == 2 && args[0].equals("--data-dir") ? Path.of(args[1]) : Path.of(".");
        LibraryApplication app = new LibraryApplication(directory);
        try { app.run(); }
        catch (EndOfInput e) {
            System.out.println("\nInput ended. Saving current state.");
            if (app.library != null) { app.save(false); }
        } catch (IOException e) {
            System.err.println("Cannot load library: " + e.getMessage());
            System.err.println("Working directory: " + directory.toAbsolutePath());
            System.err.println("Set IntelliJ Working directory to the project folder containing input_state.csv.");
            System.exit(1);
        }
    }
    private void run() throws IOException {
        System.out.println("LIBRARYBOOK SIW1 | local console application");
        System.out.println("Choose an existing account to simulate its role; this demo has no authentication.");
        Path source = directory.resolve("input_state.csv");
        if (Files.exists(directory.resolve("output_state.csv"))) {
            int choice = number("1 = fresh input CSV; 2 = resume saved output CSV: ", 1, 2);
            if (choice == 2) { source = directory.resolve("output_state.csv"); }
        }
        library = store.load(source);
        System.out.println("Loaded " + library.getBooks().size() + " books from " + source.getFileName());
        while (true) {
            System.out.println("\nHOME | " + library.getName());
            System.out.println("1 Catalog/search/sort | 2 Reader | 3 Librarian | 4 Criteria check | 5 Save/export | 6 Reload CSV | 0 Save and exit");
            int choice = number("> ", 0, 6);
            if (choice == 0) {
                if (save(true)) { System.out.println("Goodbye."); return; }
                System.out.println("Save failed; application remains open so you can retry.");
            } else if (choice == 1) { action(this::catalog); }
            else if (choice == 2) {
                System.out.println("Registered readers: " + library.getReaders());
                String readerId = line("Reader ID: ");
                if (readerId.isEmpty()) { System.out.println("Reader ID cannot be blank."); continue; }
                Reader reader = library.findReader(readerId);
                if (reader == null) { System.out.println("Reader not found. Ask a librarian to register you."); }
                else { readerMenu(reader); }
            } else if (choice == 3) {
                System.out.println("Registered librarians: " + library.getLibrarians());
                String staffId = line("Staff ID: ");
                if (staffId.isEmpty()) { System.out.println("Staff ID cannot be blank."); continue; }
                Librarian staff = library.findLibrarian(staffId);
                if (staff == null) { System.out.println("Librarian not found."); }
                else { staffMenu(staff); }
            } else if (choice == 4) { criteriaCheck(library); }
            else if (choice == 5) { save(true); }
            else { action(this::reload); }
        }
    }
    private void readerMenu(Reader reader) {
        while (true) {
            System.out.println("\nREADER | " + reader);
            System.out.println("1 Catalog | 2 Submit loan request | 3 My pending requests | 4 My loans/readings | 5 Renew my loan");
            System.out.println("6 Add favorite | 7 Favorites | 8 Remove favorite | 9 Edit my name | 10 Start reading | 11 Finish my reading | 12 Cancel my request | 0 Back");
            int choice = number("> ", 0, 12);
            if (choice == 0) { return; }
            action(() -> {
                switch (choice) {
                    case 1: catalog(); break;
                    case 2: System.out.println("Submitted " + service.submitRequest(library, reader, line("Book ID: "))); break;
                    case 3:
                        boolean found = false;
                        for (BorrowRequest request : library.getBorrowRequests().getPendingRequests()) {
                            if (request.getReader().equals(reader)) { System.out.println(request); found = true; }
                        }
                        if (!found) { System.out.println("No pending requests. Each request is attempted once by a librarian."); }
                        System.out.println("Processed request outcomes from the saved activity history:");
                        for (String event : library.getActivityHistory()) {
                            if ((event.contains(" processed REQ-") || event.contains("Request cancelled REQ-"))
                                    && event.contains(" | " + reader.getReaderId() + " | ")) {
                                System.out.println(event);
                            }
                        }
                        break;
                    case 4: myLoans(reader); break;
                    case 5: System.out.println("Renewed: " + service.renewOwnLoan(library, reader, line("Book ID: "))); break;
                    case 6: library.addFavorite(reader, line("Book ID: ")); System.out.println("Favorite saved."); break;
                    case 7: printBooks(library.getFavorites(reader)); break;
                    case 8: library.removeFavorite(reader, line("Book ID: ")); System.out.println("Favorite removed if present."); break;
                    case 9:
                        reader.setFullName(line("New full name: ")); library.recordActivity("Reader updated own name " + reader.getReaderId()); break;
                    case 10: System.out.println("Reading started: " + service.startReading(library, line("Reference book ID: "), reader)); break;
                    case 11:
                        LibraryBook book = library.requiredBook(line("Reference book ID: "));
                        if (!(book instanceof ReferenceBook) || !reader.getReaderId().equals(((ReferenceBook) book).getCurrentReaderId())) {
                            throw new IllegalArgumentException("You may finish only your own reading session.");
                        }
                        System.out.println("Reading finished: " + service.finishReading(library, book.getInventoryId())); break;
                    case 12:
                        boolean cancelled = library.getBorrowRequests().cancelOwn(line("Request ID: "), reader.getReaderId());
                        if (cancelled) { library.recordActivity("Reader cancelled own request " + reader.getReaderId()); }
                        System.out.println("Cancelled: " + cancelled); break;
                    default: throw new IllegalStateException("Unsupported menu item.");
                }
            });
        }
    }
    private void staffMenu(Librarian staff) {
        while (true) {
            System.out.println("\nLIBRARIAN | " + staff);
            System.out.println("1 Catalog | 2 Add book | 3 Edit book | 4 Remove book | 5 Readers | 6 Register reader | 7 Edit reader | 8 Remove reader");
            System.out.println("9 Issue at desk | 10 Return | 11 Reading desk | 12 FIFO requests | 13 Priority tasks | 14 Statistics/history");
            System.out.println("15 Save/export | 16 Relocate copy | 17 Staff registry | 18 Criteria check | 0 Back");
            int choice = number("> ", 0, 18);
            if (choice == 0) { return; }
            action(() -> {
                library.requireLibrarian(staff);
                switch (choice) {
                    case 1: catalog(); break;
                    case 2: addBook(staff); break;
                    case 3: editBook(staff); break;
                    case 4:
                        String id = line("Book ID: ");
                        if (confirm("Remove copy " + id + " and its pending requests/favorites?")) {
                            System.out.println("Removed: " + service.removeBook(library, staff, id));
                        }
                        break;
                    case 5: for (Reader r : library.getReaders()) { System.out.println(r); } break;
                    case 6:
                        Reader r = new Reader(line("New reader ID: "), line("Full name: "));
                        service.registerReader(library, staff, r); break;
                    case 7: service.updateReader(library, staff, line("Reader ID: "), line("New full name: ")); break;
                    case 8:
                        String readerId = line("Reader ID: ");
                        if (confirm("Remove reader " + readerId + "?")) { System.out.println("Removed: " + service.removeReader(library, staff, readerId)); }
                        break;
                    case 9:
                        Reader borrower = library.findReader(line("Reader ID: "));
                        System.out.println("Issued: " + service.issueAtDesk(library, staff, line("Book ID: "), borrower)); break;
                    case 10: System.out.println("Returned: " + service.returnAtDesk(library, staff, line("Book ID: "))); break;
                    case 11:
                        int mode = number("1 Start; 2 Finish: ", 1, 2);
                        if (mode == 1) {
                            Reader visitor = library.findReader(line("Reader ID: "));
                            System.out.println("Started: " + service.startReading(library, line("Reference book ID: "), visitor));
                        } else { System.out.println("Finished: " + service.finishReading(library, line("Reference book ID: "))); }
                        break;
                    case 12: fifo(staff); break;
                    case 13: tasks(staff); break;
                    case 14:
                        library.statistics().forEach((key, value) -> System.out.println(key + ": " + value));
                        List<String> history = library.getActivityHistory();
                        System.out.println("Last 20 activity records:");
                        for (int i = Math.max(0, history.size() - 20); i < history.size(); i++) { System.out.println(history.get(i)); }
                        break;
                    case 15: save(true); break;
                    case 16:
                        LibraryBook copy = library.requiredBook(line("Book ID: "));
                        String location = line("New shelf/reading room: ");
                        if (copy instanceof CirculatingBook) { ((CirculatingBook) copy).setShelfCode(location); }
                        else { ((ReferenceBook) copy).setReadingRoom(location); }
                        library.recordActivity("Relocated " + copy.getInventoryId() + " by " + staff.getStaffId()); break;
                    case 17: staffRegistry(staff); break;
                    case 18: criteriaCheck(library); break;
                    default: throw new IllegalStateException("Unsupported menu item.");
                }
            });
        }
    }
    private void catalog() {
        int filter = number("Filter: 1 All; 2 Title; 3 Author; 4 Published from; 5 Available home copies; 6 Category; 7 ID lookup: ", 1, 7);
        List<LibraryBook> books = library.getBooks();
        switch (filter) {
            case 2: books = BookFilter.byTitle(books, line("Title contains: ")); break;
            case 3: books = BookFilter.byAuthor(books, line("Author contains: ")); break;
            case 4: books = BookFilter.publishedFrom(books, number("From year: ", 1, java.time.Year.now().getValue())); break;
            case 5: books = BookFilter.availableForLoan(books); break;
            case 6:
                System.out.println("Categories: " + library.getCategoryNames());
                books = new ArrayList<>(library.findBooksByCategory(line("Category name: "))); break;
            case 7:
                LibraryBook found = library.findBook(line("Inventory ID: "));
                books = found == null ? List.of() : List.of(found); break;
            default: break;
        }
        int order = number("Sort: 1 Natural ID; 2 Title; 3 Year newest; 4 Author/title: ", 1, 4);
        List<LibraryBook> result = new ArrayList<>(books);
        Comparator<LibraryBook> comparator = order == 2 ? BookComparators.BY_TITLE
                : order == 3 ? BookComparators.BY_YEAR_NEWEST
                : order == 4 ? BookComparators.BY_AUTHOR_TITLE : Comparator.naturalOrder();
        result.sort(comparator); printBooks(result);
    }
    public static void printBooks(List<LibraryBook> books) {
        System.out.println("ID | Title | Author | Year | Status");
        for (LibraryBook book : books) {
            String status = book instanceof CirculatingBook
                    ? (((CirculatingBook) book).isAvailable() ? "AVAILABLE HOME COPY" : "ON LOAN")
                    : (((ReferenceBook) book).isInUse() ? "READING" : "REFERENCE ONLY");
            System.out.println(book.getInventoryId() + " | " + book.getTitle() + " | " + book.getAuthor()
                    + " | " + book.getPublicationYear() + " | " + status);
        }
        System.out.println("Total: " + books.size());
    }
    private void myLoans(Reader reader) {
        int count = 0;
        for (LibraryBook book : library.getBooks()) {
            if (book instanceof CirculatingBook && reader.getReaderId().equals(((CirculatingBook) book).getBorrowerId())) {
                CirculatingBook copy = (CirculatingBook) book;
                System.out.println(copy.getInventoryId() + " | " + copy.getTitle() + " | due=" + copy.getDueDate()
                        + " | renewed=" + copy.isRenewed() + " | overdue=" + copy.getDueDate().isBefore(java.time.LocalDate.now())); count++;
            } else if (book instanceof ReferenceBook && reader.getReaderId().equals(((ReferenceBook) book).getCurrentReaderId())) {
                System.out.println(book.getInventoryId() + " | " + book.getTitle() + " | reading in " + ((ReferenceBook) book).getReadingRoom()); count++;
            }
        }
        if (count == 0) { System.out.println("No active loans or reading sessions."); }
    }
    private void addBook(Librarian staff) {
        int type = number("Type: 1 Circulating; 2 Reference: ", 1, 2);
        String id = line("Inventory ID: "), title = line("Title: "), author = line("Author: "), isbn = line("ISBN: ");
        int year = number("Publication year: ", 1, java.time.Year.now().getValue());
        String location = line("Shelf code/reading room: ");
        LibraryBook book = type == 1 ? new CirculatingBook(id, title, author, isbn, year, location)
                : new ReferenceBook(id, title, author, isbn, year, line("Subject: "), location);
        service.addBook(library, staff, book); System.out.println("Book added.");
    }
    private void editBook(Librarian staff) {
        LibraryBook book = library.requiredBook(line("Book ID: "));
        System.out.println("Keep ID and type stable. Press Enter to keep a field.");
        String title = keep("Title", book.getTitle()), author = keep("Author", book.getAuthor()), isbn = keep("ISBN", book.getIsbn());
        String year = keep("Year", "" + book.getPublicationYear());
        String subject = book instanceof ReferenceBook
                ? LibraryBook.requireText(keep("Subject", ((ReferenceBook) book).getSubject()), "Subject") : null;
        service.updateBook(library, staff, book.getInventoryId(), title, author, isbn, Integer.parseInt(year));
        if (subject != null) { ((ReferenceBook) book).setSubject(subject); }
        System.out.println("Book updated; all index entries refer to the same updated object.");
    }
    private void fifo(Librarian staff) {
        for (BorrowRequest request : library.getBorrowRequests().getPendingRequests()) { System.out.println(request); }
        int mode = number("1 Process next; 2 Process all; 3 List only: ", 1, 3);
        if (mode == 1) { String result = service.processRequest(library, staff); System.out.println(result == null ? "Queue is empty." : result); }
        if (mode == 2) {
            while (!library.getBorrowRequests().isEmpty()) { System.out.println(service.processRequest(library, staff)); }
            System.out.println("FIFO queue is empty.");
        }
    }
    private void tasks(Librarian staff) {
        for (LibraryTask task : library.getTaskDispatcher().getPendingTasks()) { System.out.println(task); }
        int mode = number("1 Schedule; 2 Process next; 3 Process all; 4 List only: ", 1, 4);
        if (mode == 1) {
            int kind = number("Type: 1 Return; 2 Renew; 3 Finish reading: ", 1, 3);
            LibraryTask.Type type = LibraryTask.Type.values()[kind - 1];
            String id = line("Book ID: "); int priority = number("Priority: 1 urgent; 2 normal; 3 low: ", 1, 3);
            service.scheduleTask(library, staff, new LibraryTask("TASK-" + java.util.UUID.randomUUID(), type, id, priority));
            System.out.println("Task scheduled.");
        } else if (mode == 2) {
            String result = service.processTask(library, staff); System.out.println(result == null ? "Dispatcher is empty." : result);
        } else if (mode == 3) {
            while (!library.getTaskDispatcher().isEmpty()) { System.out.println(service.processTask(library, staff)); }
            System.out.println("Priority queue is empty.");
        }
    }
    private void staffRegistry(Librarian current) {
        for (Librarian staff : library.getLibrarians()) { System.out.println(staff); }
        int mode = number("1 Add; 2 Edit; 3 Remove; 4 List only: ", 1, 4);
        if (mode == 4) { return; }
        String id = line("Staff ID: ");
        if (mode == 1) { service.registerLibrarian(library, current, new Librarian(id, line("Full name: "))); }
        else if (mode == 2) {
            Librarian staff = library.findLibrarian(id);
            if (staff == null) { throw new IllegalArgumentException("Librarian was not found."); }
            service.updateLibrarian(library, current, id, line("New name: "));
        } else {
            if (current.getStaffId().equals(id)) { throw new IllegalStateException("Cannot remove the active staff account."); }
            System.out.println("Removed: " + service.removeLibrarian(library, current, id));
        }
    }
    public static void criteriaCheck(Library library) {
        System.out.println("=== SIW1 CRITERIA CHECK ===");
        System.out.println("List registry size: " + library.getBooks().size());
        System.out.println("Category Map<type, Set<LibraryBook>>: " + library.getCategoryIndex().keySet());
        if (!library.getBooks().isEmpty()) {
            LibraryBook first = library.getBooks().get(0);
            LibraryBook sameKey = new CirculatingBook(first.getInventoryId(), "Same business key", "Example", "isbn", 2000, "X");
            HashSet<LibraryBook> set = new HashSet<>(); set.add(first); set.add(sameKey);
            System.out.println("equals by ID: " + first.equals(sameKey));
            System.out.println("Equal hash codes: " + (first.hashCode() == sameKey.hashCode()));
            System.out.println("HashSet size for two equal IDs: " + set.size());
            System.out.println("compareTo same ID: " + first.compareTo(sameKey));
            System.out.println("Primary Map lookup returns original object: " + (library.findBook(first.getInventoryId()) == first));
        }
        System.out.println("Comparable: inventory ID; 3 Comparators: title, newest year, author/title.");
        System.out.println("N:M favorite links: " + library.statistics().get("Favorite links"));
        System.out.println("FIFO requests=" + library.getBorrowRequests().size() + "; priority tasks=" + library.getTaskDispatcher().size());
        System.out.println("All registry/index/favorite links consistent: " + library.checkConsistency());
    }
    private void reload() throws IOException {
        int mode = number("Load: 1 Original input CSV; 2 Saved output CSV: ", 1, 2);
        if (!confirm("Replace current in-memory state with the selected CSV?")) { return; }
        Library candidate = store.load(directory.resolve(mode == 1 ? "input_state.csv" : "output_state.csv"));
        library = candidate; System.out.println("CSV loaded successfully; no partial import was applied.");
    }
    private boolean save(boolean verbose) {
        try {
            store.save(library, directory.resolve("output_state.csv"));
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("SAVE FAILED. Changes remain in memory: " + e.getMessage()); return false;
        }
        try { store.exportReports(library, directory); }
        catch (IOException e) {
            System.out.println("State saved, but report export failed: " + e.getMessage()); return true;
        }
        if (verbose) { System.out.println("Saved output_state.csv, output_catalog.csv, output_summary.csv and output_activity.csv; original input untouched."); }
        return true;
    }
    private void action(Action action) {
        try { action.run(); save(false); }
        catch (IllegalArgumentException | IllegalStateException | IOException e) { System.out.println("Action rejected: " + e.getMessage()); }
    }
    private String line(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) { throw new EndOfInput(); }
        return input.nextLine().trim();
    }
    private int number(String prompt, int min, int max) {
        while (true) {
            try { int value = Integer.parseInt(line(prompt)); if (value >= min && value <= max) { return value; } }
            catch (NumberFormatException e) { /* Re-prompt rather than terminate the application. */ }
            System.out.println("Enter a whole number from " + min + " to " + max + ".");
        }
    }
    private String keep(String label, String current) {
        String value = line(label + " [" + current + "]: "); return value.isEmpty() ? current : value;
    }
    private boolean confirm(String message) { return line(message + " Type yes to confirm: ").equalsIgnoreCase("yes"); }
    @FunctionalInterface private interface Action { void run() throws IOException; }
    private static final class EndOfInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
