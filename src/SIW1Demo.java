import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

// Deterministic assessment walkthrough. Input data is never overwritten.
public final class SIW1Demo {
    private SIW1Demo() { }
    public static void main(String[] args) {
        try {
            Path root = args.length == 1 ? Path.of(args[0]) : Path.of(".");
            LibraryCsvStore store = new LibraryCsvStore();
            Library library = store.load(root.resolve("input_state.csv"));
            LibraryService service = new LibraryService();
            Librarian staff = library.findLibrarian("STAFF-001");
            Reader aman = library.findReader("READER-001"), aida = library.findReader("READER-002");
            System.out.println("=== 1 CONTINUITY AND BUSINESS KEY CONTRACTS ===");
            LibraryApplication.criteriaCheck(library);
            LibraryBook sameId = new ReferenceBook("C001", "Other metadata", "Editor", "isbn", 2000, "Example", "Room 1");
            HashSet<LibraryBook> unique = new HashSet<>(List.of(library.requiredBook("C001"), sameId));
            System.out.println("Cross-subclass equal key is one Set member: " + (unique.size() == 1));
            System.out.println("\n=== 2 NATURAL ORDER AND THREE COMPARATORS ===");
            System.out.println("Natural ID:"); LibraryApplication.printBooks(service.sortedBooks(library, null));
            System.out.println("Title:"); LibraryApplication.printBooks(service.sortedBooks(library, BookComparators.BY_TITLE));
            System.out.println("Newest year:"); LibraryApplication.printBooks(service.sortedBooks(library, BookComparators.BY_YEAR_NEWEST));
            System.out.println("Author/title:"); LibraryApplication.printBooks(service.sortedBooks(library, BookComparators.BY_AUTHOR_TITLE));
            System.out.println("Available home copies: " + BookFilter.availableForLoan(library.getBooks()).size());
            library.getCategoryIndex().forEach((key, books) -> System.out.println("Category " + key + ": " + books.size()));
            System.out.println("\n=== 3 READER TO LIBRARIAN FIFO AND N:M FAVORITES ===");
            library.addFavorite(aman, "C001"); library.addFavorite(aman, "C003"); library.addFavorite(aida, "C001");
            System.out.println("C001 favorited by " + library.getFansOfBook("C001").size() + " readers.");
            System.out.println("Aman favorites " + aman.getFavoriteBookIds().size() + " books.");
            System.out.println("Submit: " + service.submitRequest(library, aman, "C001"));
            System.out.println("Submit: " + service.submitRequest(library, aida, "C002"));
            System.out.println("Submit: " + service.submitRequest(library, aida, "C001"));
            while (!library.getBorrowRequests().isEmpty()) { System.out.println(service.processRequest(library, staff)); }
            System.out.println("Empty FIFO: " + service.processRequest(library, staff));
            System.out.println("\n=== 4 PRIORITY WORKFLOW ===");
            service.borrowBook(library, "C003", aman); service.startReading(library, "R001", aida);
            service.scheduleTask(library, staff, new LibraryTask("TASK-1", LibraryTask.Type.RENEW_LOAN, "C002", 3));
            service.scheduleTask(library, staff, new LibraryTask("TASK-2", LibraryTask.Type.RETURN_BOOK, "C001", 1));
            service.scheduleTask(library, staff, new LibraryTask("TASK-3", LibraryTask.Type.FINISH_READING, "R001", 2));
            service.scheduleTask(library, staff, new LibraryTask("TASK-4", LibraryTask.Type.RETURN_BOOK, "C003", 1));
            while (!library.getTaskDispatcher().isEmpty()) { System.out.println(service.processTask(library, staff)); }
            System.out.println("\n=== 5 SYNCHRONIZED CRUD AND VALIDATION ===");
            reject("Unregistered staff", () -> service.addBook(library, new Librarian("STAFF-001", "Not registered"), sameId));
            reject("Delete active C002 loan", () -> service.removeBook(library, staff, "C002"));
            LibraryBook temporary = new CirculatingBook("TEMP", "Draft", "Author", "isbn", 2020, "T-01");
            service.addBook(library, staff, temporary);
            service.updateBook(library, staff, "TEMP", "Java, \"Collections\"", "Updated Author", "isbn", 2021);
            library.addFavorite(aman, "TEMP"); library.addFavorite(aida, "TEMP");
            service.submitRequest(library, aman, "TEMP");
            service.scheduleTask(library, staff, new LibraryTask("TEMP-TASK", LibraryTask.Type.RETURN_BOOK, "TEMP", 2));
            System.out.println("Before removal: fans=" + library.getFansOfBook("TEMP").size()
                    + ", pending requests=" + library.getBorrowRequests().size() + ", pending tasks=" + library.getTaskDispatcher().size());
            service.removeBook(library, staff, "TEMP");
            System.out.println("After removal: ID absent=" + (library.findBook("TEMP") == null)
                    + ", favorites cleaned=" + !aman.getFavoriteBookIds().contains("TEMP")
                    + ", queues empty=" + (library.getBorrowRequests().isEmpty() && library.getTaskDispatcher().isEmpty()));
            Reader temporaryReader = new Reader("TEMP-READER", "Temporary Reader");
            library.registerReader(temporaryReader);
            service.updateReader(library, staff, "TEMP-READER", "Updated Reader");
            library.addFavorite(temporaryReader, "C003"); service.submitRequest(library, temporaryReader, "C003");
            System.out.println("Remove reader with pending request: " + service.removeReader(library, staff, "TEMP-READER"));
            System.out.println("Reader request cleaned: " + library.getBorrowRequests().isEmpty());
            System.out.println("All indexes consistent: " + library.checkConsistency());
            System.out.println("\n=== 6 CSV SNAPSHOT RELOAD AND REPORTS ===");
            service.submitRequest(library, aman, "C003");
            service.scheduleTask(library, staff, new LibraryTask("PERSIST-TASK", LibraryTask.Type.RETURN_BOOK, "C001", 2));
            Path snapshot = root.resolve("results/demo_state.csv");
            store.save(library, snapshot); store.exportReports(library, root.resolve("results"));
            Library loaded = store.load(snapshot);
            System.out.println("Reloaded statistics equal: " + library.statistics().equals(loaded.statistics()));
            System.out.println("Reloaded pending request count: " + loaded.getBorrowRequests().size());
            System.out.println("Reloaded priority task: " + loaded.getTaskDispatcher().peek());
            System.out.println("Reloaded active C002 borrower: " + ((CirculatingBook) loaded.requiredBook("C002")).getBorrowerId());
            System.out.println("Reloaded C002 renewed: " + ((CirculatingBook) loaded.requiredBook("C002")).isRenewed());
            loaded.statistics().forEach((key, value) -> System.out.println(key + ": " + value));
            System.out.println("Saved results/demo_state.csv, results/output_catalog.csv, results/output_summary.csv");
            System.out.println("SIW1 demonstration completed.");
        } catch (java.io.IOException e) { throw new IllegalStateException("Demonstration failed: " + e.getMessage(), e); }
    }
    private static void reject(String label, Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection: " + label); }
        catch (IllegalArgumentException | IllegalStateException e) { System.out.println(label + " rejected: " + e.getMessage()); }
    }
}
