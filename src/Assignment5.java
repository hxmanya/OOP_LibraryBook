public class Assignment5 {
    public static void main(String[] args) {
        CirculatingBook novel = new CirculatingBook("C001", "1984", "George Orwell",
                "9780451524935", 1949, "A-01");
        CirculatingBook fantasy = new CirculatingBook("C002", "The Hobbit", "J.R.R. Tolkien",
                "9780547928227", 1937, "A-02");
        ReferenceBook dictionary = new ReferenceBook("R001", "Oxford Dictionary of English",
                "Angus Stevenson", "9780199571123", 2010, "English language", "Room 1");
        Reader aman = new Reader("READER-001", "Esenaliev Aman");
        Reader aida = new Reader("READER-002", "Aida Sadykova");
        Library library = new Library("University Library");
        library.addBook(novel);
        library.addBook(fantasy);
        library.addBook(dictionary);
        library.registerReader(aman);
        library.registerReader(aida);
        LibraryService service = new LibraryService();

        System.out.println("=== 1 INHERITANCE AND REALIZATION ===");
        System.out.println("Library: " + library.getName());
        for (LibraryBook book : library.getBooks()) {
            book.displayInfo();
            System.out.println("Loan period: " + book.getLoanPeriodDays() + " days");
            System.out.println("Usage rules: " + book.getUsageRules());
            System.out.println();
        }

        System.out.println("=== 2 AGGREGATION ===");
        System.out.println("Registered reader: " + library.findReader(aman.getReaderId()));
        System.out.println("Same externally created book: " + (library.findBook("C001") == novel));
        System.out.println("Remove idle fantasy copy: " + library.removeBook("C002"));
        System.out.println("Removed object still exists: " + fantasy.getTitle());
        library.addBook(fantasy);
        System.out.println("Re-added copy count: " + library.getBooks().size());

        System.out.println("\n=== 3 DEPENDENCY AND LIBRARY OPERATIONS ===");
        System.out.println("Borrow novel: " + service.borrowBook(library, "C001", aman));
        System.out.println("Second loan rejected: " + !service.borrowBook(library, "C001", aida));
        System.out.println("Renew once: " + service.renewLoan(library, "C001"));
        System.out.println("Second renewal rejected: " + !service.renewLoan(library, "C001"));
        System.out.println("Start dictionary reading: " + service.startReading(library, "R001", aida));
        System.out.println("Second reader rejected: " + !service.startReading(library, "R001", aman));
        System.out.println("Subject matches: " + dictionary.matchesSubject("english language"));
        showRejection("Borrow reference copy", () -> service.borrowBook(library, "R001", aman));
        showRejection("Remove active loan", () -> library.removeBook("C001"));
        showRejection("Move book during reading", () -> dictionary.setReadingRoom("Room 3"));
        showRejection("Unregistered reader", () -> service.borrowBook(library, "C002",
                new Reader("READER-003", "Guest Reader")));
        showRejection("Duplicate inventory ID", () -> library.addBook(novel));
        showRejection("Change inventory ID", () -> novel.setInventoryId("C999"));
        System.out.println("Return novel: " + service.returnBook(library, "C001"));
        System.out.println("Repeated return rejected: " + !service.returnBook(library, "C001"));
        System.out.println("Finish reading: " + service.finishReading(library, "R001"));
        System.out.println("Repeated finish rejected: " + !service.finishReading(library, "R001"));

        System.out.println("\n=== 4 COMPOSITION AND ACTIVITY HISTORY ===");
        int number = 1;
        for (String entry : library.getActivityHistory()) {
            System.out.println(number++ + ". " + entry);
        }
        Library anotherLibrary = new Library("City Library");
        System.out.println("Separate library has its own empty log: "
                + anotherLibrary.getActivityHistory().isEmpty());

        System.out.println("\n=== 5 FINAL STATES ===");
        System.out.println("Novel available: " + novel.isAvailable());
        System.out.println("Novel due date cleared: " + (novel.getDueDate() == null));
        System.out.println("Dictionary in use: " + dictionary.isInUse());
        System.out.println("Books in catalog: " + library.getBooks().size());
        System.out.println("Registered readers: " + library.getReaders().size());
        System.out.println("Demonstration completed.");
    }

    private static void showRejection(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + label);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(label + " rejected: " + e.getMessage());
        }
    }
}
