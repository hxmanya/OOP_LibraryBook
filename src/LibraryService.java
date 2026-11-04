import java.util.Objects;

// Dependency: collaborators are method parameters, not stored fields.
public final class LibraryService {
    public static final int MAX_HOME_LOANS = 3;
    public boolean borrowBook(Library library, String inventoryId, Reader reader) {
        requireRegisteredReader(library, reader);
        for (LibraryBook existing : library.getBooks()) {
            if (existing instanceof CirculatingBook) {
                CirculatingBook copy = (CirculatingBook) existing;
                if (reader.getReaderId().equals(copy.getBorrowerId())
                        && copy.getDueDate().isBefore(java.time.LocalDate.now())) {
                    throw new IllegalStateException("Return overdue loans before borrowing another copy.");
                }
            }
        }
        long activeLoans = library.getBooks().stream()
                .filter(b -> b instanceof CirculatingBook
                        && reader.getReaderId().equals(((CirculatingBook) b).getBorrowerId())).count();
        if (activeLoans >= MAX_HOME_LOANS) {
            throw new IllegalStateException("Reader has reached the limit of 3 home loans.");
        }
        LibraryBook book = requireBook(library, inventoryId);
        if (!(book instanceof Borrowable)) {
            throw new IllegalArgumentException("This copy cannot be taken home.");
        }
        boolean success = ((Borrowable) book).borrowBook(reader.getReaderId());
        if (success) {
            library.recordActivity("Borrowed " + book.getInventoryId() + " by " + reader.getReaderId());
        }
        return success;
    }

    public boolean returnBook(Library library, String inventoryId) {
        LibraryBook book = requireBook(library, inventoryId);
        if (!(book instanceof Borrowable)) {
            throw new IllegalArgumentException("This copy does not support home loans.");
        }
        boolean success = ((Borrowable) book).returnBook();
        if (success) { library.recordActivity("Returned " + book.getInventoryId()); }
        return success;
    }

    public boolean startReading(Library library, String inventoryId, Reader reader) {
        requireRegisteredReader(library, reader);
        LibraryBook book = requireBook(library, inventoryId);
        for (LibraryBook existing : library.getBooks()) {
            if (existing != book && existing instanceof ReferenceBook
                    && reader.getReaderId().equals(((ReferenceBook) existing).getCurrentReaderId())) {
                throw new IllegalStateException("Finish the current reading session before starting another.");
            }
        }
        if (!(book instanceof InLibraryReadable)) {
            throw new IllegalArgumentException("This copy does not support reading-room sessions.");
        }
        boolean success = ((InLibraryReadable) book).startReading(reader.getReaderId());
        if (success) {
            library.recordActivity("Reading started " + book.getInventoryId() + " by " + reader.getReaderId());
        }
        return success;
    }

    public boolean finishReading(Library library, String inventoryId) {
        LibraryBook book = requireBook(library, inventoryId);
        if (!(book instanceof InLibraryReadable)) {
            throw new IllegalArgumentException("This copy does not support reading-room sessions.");
        }
        boolean success = ((InLibraryReadable) book).finishReading();
        if (success) { library.recordActivity("Reading finished " + book.getInventoryId()); }
        return success;
    }

    public boolean renewLoan(Library library, String inventoryId) {
        LibraryBook book = requireBook(library, inventoryId);
        if (!(book instanceof CirculatingBook)) {
            throw new IllegalArgumentException("This copy does not support loan renewal.");
        }
        for (BorrowRequest request : library.getBorrowRequests().getPendingRequests()) {
            if (book.getInventoryId().equals(request.getInventoryId())) {
                throw new IllegalStateException("Another request is waiting for this copy; renewal is blocked.");
            }
        }
        boolean success = ((CirculatingBook) book).renewLoan();
        if (success) { library.recordActivity("Renewed " + book.getInventoryId()); }
        return success;
    }

    private LibraryBook requireBook(Library library, String inventoryId) {
        Objects.requireNonNull(library, "Library cannot be null.");
        LibraryBook book = library.findBook(inventoryId);
        if (book == null) { throw new IllegalArgumentException("Book was not found."); }
        return book;
    }

    private void requireRegisteredReader(Library library, Reader reader) {
        Objects.requireNonNull(library, "Library cannot be null.");
        if (reader == null) { throw new IllegalArgumentException("Reader was not found."); }
        if (library.findReader(reader.getReaderId()) != reader) {
            throw new IllegalArgumentException("Use the reader registered in this library.");
        }
    }

    public String submitRequest(Library library, Reader reader, String bookId) {
        library.requireReader(reader);
        LibraryBook book = library.requiredBook(bookId);
        if (!(book instanceof Borrowable)) { throw new IllegalArgumentException("This copy cannot be taken home."); }
        if (book instanceof CirculatingBook && reader.getReaderId().equals(((CirculatingBook) book).getBorrowerId())) {
            throw new IllegalStateException("This copy is already on loan to you.");
        }
        for (BorrowRequest existing : library.getBorrowRequests().getPendingRequests()) {
            if (existing.getReader().equals(reader) && existing.getInventoryId().equals(book.getInventoryId())) {
                throw new IllegalStateException("Your request for this copy is already pending.");
            }
        }
        String id = "REQ-" + java.util.UUID.randomUUID();
        library.getBorrowRequests().offer(new BorrowRequest(id, book.getInventoryId(), reader));
        library.recordActivity("Request submitted " + id + " by " + reader.getReaderId());
        return id;
    }
    public String processRequest(Library library, Librarian staff) {
        library.requireLibrarian(staff);
        String result = library.getBorrowRequests().processNext(library, this);
        if (result != null) { library.recordActivity("Staff " + staff.getStaffId() + " processed " + result); }
        return result;
    }
    public void scheduleTask(Library library, Librarian staff, LibraryTask task) {
        library.requireLibrarian(staff); validateTask(library, task);
        library.getTaskDispatcher().offer(task);
        library.recordActivity("Staff " + staff.getStaffId() + " scheduled " + task);
    }
    static void validateTask(Library library, LibraryTask task) {
        Objects.requireNonNull(task, "Task cannot be null.");
        LibraryBook book = library.requiredBook(task.getInventoryId());
        if (task.getType() == LibraryTask.Type.RETURN_BOOK && !(book instanceof Borrowable)
                || task.getType() == LibraryTask.Type.RENEW_LOAN && !(book instanceof CirculatingBook)
                || task.getType() == LibraryTask.Type.FINISH_READING && !(book instanceof InLibraryReadable)) {
            throw new IllegalArgumentException("Task type does not match this book's capabilities.");
        }
    }
    public String processTask(Library library, Librarian staff) {
        library.requireLibrarian(staff);
        String result = library.getTaskDispatcher().dispatchNext(library, this);
        if (result != null) { library.recordActivity("Staff " + staff.getStaffId() + " processed " + result); }
        return result;
    }
    public boolean renewOwnLoan(Library library, Reader reader, String id) {
        library.requireReader(reader);
        LibraryBook book = library.requiredBook(id);
        if (!(book instanceof CirculatingBook)
                || !reader.getReaderId().equals(((CirculatingBook) book).getBorrowerId())) {
            throw new IllegalArgumentException("You may renew only your own active home loan.");
        }
        return renewLoan(library, id);
    }
    public void addBook(Library library, Librarian staff, LibraryBook book) {
        library.requireLibrarian(staff); library.addBook(book);
        library.recordActivity("Catalog add by " + staff.getStaffId());
    }
    public void updateBook(Library library, Librarian staff, String id,
                           String title, String author, String isbn, int year) {
        library.requireLibrarian(staff); library.updateBook(id, title, author, isbn, year);
        library.recordActivity("Catalog update by " + staff.getStaffId());
    }
    public boolean removeBook(Library library, Librarian staff, String id) {
        library.requireLibrarian(staff); return library.removeBook(id);
    }
    public void updateReader(Library library, Librarian staff, String id, String name) {
        library.requireLibrarian(staff);
        Reader reader = library.findReader(id);
        if (reader == null) { throw new IllegalArgumentException("Reader was not found."); }
        reader.setFullName(name); library.recordActivity("Updated reader " + id + " by " + staff.getStaffId());
    }
    public void registerReader(Library library, Librarian staff, Reader reader) {
        library.requireLibrarian(staff); library.registerReader(reader);
        library.recordActivity("Reader registered by " + staff.getStaffId());
    }
    public void registerLibrarian(Library library, Librarian staff, Librarian added) {
        library.requireLibrarian(staff); library.registerLibrarian(added);
        library.recordActivity("Librarian registered by " + staff.getStaffId());
    }
    public void updateLibrarian(Library library, Librarian staff, String id, String name) {
        library.requireLibrarian(staff);
        Librarian changed = library.findLibrarian(id);
        if (changed == null) { throw new IllegalArgumentException("Librarian was not found."); }
        changed.setFullName(name); library.recordActivity("Updated staff " + id + " by " + staff.getStaffId());
    }
    public boolean removeLibrarian(Library library, Librarian staff, String id) {
        library.requireLibrarian(staff);
        if (staff.getStaffId().equals(id)) { throw new IllegalStateException("Cannot remove the active staff account."); }
        return library.removeLibrarian(id);
    }
    public boolean issueAtDesk(Library library, Librarian staff, String id, Reader reader) {
        library.requireLibrarian(staff); boolean result = borrowBook(library, id, reader);
        library.recordActivity("Desk issue by " + staff.getStaffId() + " for " + id + ": " + result); return result;
    }
    public boolean returnAtDesk(Library library, Librarian staff, String id) {
        library.requireLibrarian(staff); boolean result = returnBook(library, id);
        library.recordActivity("Desk return by " + staff.getStaffId() + " for " + id + ": " + result); return result;
    }
    public boolean removeReader(Library library, Librarian staff, String id) {
        library.requireLibrarian(staff); return library.removeReader(id);
    }
    public java.util.List<LibraryBook> sortedBooks(Library library, java.util.Comparator<LibraryBook> comparator) {
        java.util.List<LibraryBook> result = new java.util.ArrayList<>(library.getBooks());
        result.sort(comparator); return result;
    }
}
