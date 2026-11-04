import java.util.List;

public class CollectionsDemo {
    public static void main(String[] args) {
        Library library = new Library("University Library");
        LibraryService service = new LibraryService();
        CirculatingBook novel = new CirculatingBook("C001", "1984", "George Orwell",
                "9780451524935", 1949, "A-01");
        CirculatingBook fantasy = new CirculatingBook("C002", "The Hobbit", "J.R.R. Tolkien",
                "9780547928227", 1937, "A-02");
        CirculatingBook cleanCode = new CirculatingBook("C003", "Clean Code", "Robert C. Martin",
                "9780132350884", 2008, "B-01");
        ReferenceBook dictionary = new ReferenceBook("R001", "Oxford Dictionary of English",
                "Angus Stevenson", "9780199571123", 2010, "English language", "Room 1");
        library.addBook(novel);
        library.addBook(fantasy);
        library.addBook(cleanCode);
        library.addBook(dictionary);
        Reader aman = new Reader("READER-001", "Esenaliev Aman");
        Reader aida = new Reader("READER-002", "Aida Sadykova");
        Reader guest = new Reader("GUEST-001", "Guest Reader");
        library.registerReader(aman);
        library.registerReader(aida);

        System.out.println("=== TASK 1 LISTS AND FILTERING ===");
        printBooks("Full catalog", library.getBooks());
        printBooks("Title contains CODE", BookFilter.byTitle(library.getBooks(), "CODE"));
        printBooks("Author contains tolkien", BookFilter.byAuthor(library.getBooks(), "tolkien"));
        printBooks("Published from 2000 inclusive", BookFilter.publishedFrom(library.getBooks(), 2000));
        printBooks("Available for home loan", BookFilter.availableForLoan(library.getBooks()));
        printBooks("No matching title", BookFilter.byTitle(library.getBooks(), "not-in-catalog"));
        System.out.println("Original catalog still has " + library.getBooks().size() + " books.");

        System.out.println("\n=== TASK 2 QUEUE SIMULATION FIFO ===");
        BorrowRequestQueue queue = new BorrowRequestQueue();
        queue.offer(new BorrowRequest("REQ-1", "C001", aman));
        queue.offer(new BorrowRequest("REQ-2", "C001", aida));
        queue.offer(new BorrowRequest("REQ-3", "C002", aida));
        queue.offer(new BorrowRequest("REQ-4", "C003", guest));
        queue.offer(new BorrowRequest("REQ-5", "C003", aman));
        queue.offer(new BorrowRequest("REQ-6", "R001", aman));
        System.out.println("Submitted order: REQ-1, REQ-2, REQ-3, REQ-4, REQ-5, REQ-6");
        System.out.println("Peek without removal: " + queue.peek());
        System.out.println("Queue size after peek: " + queue.size());
        while (!queue.isEmpty()) { System.out.println(queue.processNext(library, service)); }
        System.out.println("Empty FIFO returns null: " + (queue.processNext(library, service) == null));
        printBooks("Available after FIFO processing", BookFilter.availableForLoan(library.getBooks()));

        System.out.println("\n=== TASK 3 PRIORITY TASK DISPATCHER ===");
        System.out.println("Priorities: 1 = urgent, 2 = normal, 3 = low.");
        System.out.println("Start reading session: " + service.startReading(library, "R001", aida));
        PriorityTaskDispatcher dispatcher = new PriorityTaskDispatcher();
        dispatcher.offer(new LibraryTask("TASK-1", LibraryTask.Type.RENEW_LOAN, "C002", 3));
        dispatcher.offer(new LibraryTask("TASK-2", LibraryTask.Type.FINISH_READING, "R001", 2));
        dispatcher.offer(new LibraryTask("TASK-3", LibraryTask.Type.RETURN_BOOK, "C001", 1));
        dispatcher.offer(new LibraryTask("TASK-4", LibraryTask.Type.RETURN_BOOK, "C003", 1));
        System.out.println("Submitted order: TASK-1, TASK-2, TASK-3, TASK-4");
        System.out.println("Peek without removal: " + dispatcher.peek());
        System.out.println("Dispatcher size after peek: " + dispatcher.size());
        while (!dispatcher.isEmpty()) { System.out.println(dispatcher.dispatchNext(library, service)); }
        dispatcher.offer(new LibraryTask("TASK-5", LibraryTask.Type.RETURN_BOOK, "C001", 2));
        System.out.println("Repeated return: " + dispatcher.dispatchNext(library, service));
        System.out.println("Empty dispatcher returns null: "
                + (dispatcher.dispatchNext(library, service) == null));
        System.out.println("Novel available: " + novel.isAvailable());
        System.out.println("Clean Code available: " + cleanCode.isAvailable());
        System.out.println("Fantasy loan renewed: " + fantasy.isRenewed());
        System.out.println("Dictionary in use: " + dictionary.isInUse());
        printBooks("Available after task dispatch", BookFilter.availableForLoan(library.getBooks()));

        System.out.println("\n=== ACTIVITY HISTORY FROM THE EXISTING LIBRARY ===");
        for (String entry : library.getActivityHistory()) { System.out.println(entry); }
        System.out.println("Collections demonstration completed.");
    }

    private static void printBooks(String label, List<LibraryBook> books) {
        System.out.println(label + " (" + books.size() + "):");
        for (LibraryBook book : books) {
            System.out.println("  " + book.getInventoryId() + " | " + book.getTitle()
                    + " | " + book.getAuthor() + " | " + book.getPublicationYear());
        }
        if (books.isEmpty()) { System.out.println("  No books found."); }
    }
}
