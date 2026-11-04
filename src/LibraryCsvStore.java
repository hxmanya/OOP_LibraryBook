import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class LibraryCsvStore {
    public static final List<String> HEADER = List.of("recordType", "id", "kind", "name", "author",
            "isbn", "year", "location", "subject", "readerId", "dueDate", "renewed", "priority", "details");

    // Build an isolated candidate; callers replace live state only after complete validation.
    public Library load(Path path) throws IOException {
        List<List<String>> rows = CsvFile.read(path);
        if (rows.isEmpty() || !rows.get(0).equals(HEADER)) { throw new IOException("Unexpected CSV header in " + path); }
        Library library = null;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != HEADER.size()) { throw new IOException("CSV record " + (i + 1) + ": expected 14 fields."); }
            if (!List.of("LIBRARY", "BOOK", "READER", "STAFF", "FAVORITE", "REQUEST", "TASK", "HISTORY").contains(row.get(0))) {
                throw new IOException("CSV record " + (i + 1) + ": unknown record type.");
            }
            if (row.get(0).equals("LIBRARY")) {
                if (library != null) { throw new IOException("Exactly one LIBRARY record is required."); }
                try { library = new Library(row.get(1)); }
                catch (IllegalArgumentException e) { throw new IOException("Invalid library name.", e); }
            }
        }
        if (library == null) { throw new IOException("LIBRARY record is missing."); }
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            try {
                switch (r.get(0)) {
                    case "READER": library.registerReader(new Reader(r.get(1), r.get(3))); break;
                    case "STAFF": library.registerLibrarian(new Librarian(r.get(1), r.get(3))); break;
                    case "BOOK":
                        LibraryBook book;
                        if (r.get(2).equals("CIRCULATING")) {
                            book = new CirculatingBook(r.get(1), r.get(3), r.get(4), r.get(5), Integer.parseInt(r.get(6)), r.get(7));
                        } else if (r.get(2).equals("REFERENCE")) {
                            book = new ReferenceBook(r.get(1), r.get(3), r.get(4), r.get(5), Integer.parseInt(r.get(6)), r.get(8), r.get(7));
                        } else { throw new IllegalArgumentException("Unknown book kind."); }
                        library.addBook(book); break;
                    default: break;
                }
            } catch (IllegalArgumentException | IllegalStateException e) { throw badRecord(i, e); }
        }
        List<String> history = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            try {
                switch (r.get(0)) {
                    case "BOOK":
                        LibraryBook book = library.requiredBook(r.get(1));
                        boolean renewed = bool(r.get(11));
                        if (!r.get(9).isEmpty()) {
                            Reader reader = requiredReader(library, r.get(9));
                            if (book instanceof CirculatingBook) {
                                ((CirculatingBook) book).restoreLoan(reader.getReaderId(), LocalDate.parse(r.get(10)), renewed);
                            } else {
                                if (!r.get(10).isEmpty() || renewed) { throw new IllegalArgumentException("Reference copy cannot have a home-loan date."); }
                                ((ReferenceBook) book).startReading(reader.getReaderId());
                            }
                        } else if (!r.get(10).isEmpty() || renewed) {
                            throw new IllegalArgumentException("Loan details require an existing borrower.");
                        }
                        break;
                    case "FAVORITE": library.addFavorite(requiredReader(library, r.get(1)), r.get(13)); break;
                    case "REQUEST":
                        LibraryBook requested = library.requiredBook(r.get(13));
                        if (!(requested instanceof Borrowable)) { throw new IllegalArgumentException("Request targets a reference copy."); }
                        library.getBorrowRequests().offer(new BorrowRequest(r.get(1), r.get(13), requiredReader(library, r.get(9)))); break;
                    case "TASK":
                        library.requiredBook(r.get(13));
                        LibraryTask task = new LibraryTask(r.get(1), LibraryTask.Type.valueOf(r.get(2)), r.get(13), Integer.parseInt(r.get(12)));
                        LibraryService.validateTask(library, task);
                        library.getTaskDispatcher().offer(task); break;
                    case "HISTORY": history.add(r.get(13)); break;
                    default: break;
                }
            } catch (RuntimeException e) { throw badRecord(i, e); }
        }
        if (library.getLibrarians().isEmpty()) { throw new IOException("At least one librarian must be present."); }
        for (Reader reader : library.getReaders()) {
            int loans = 0, readings = 0;
            for (LibraryBook book : library.getBooks()) {
                if (book instanceof CirculatingBook && reader.getReaderId().equals(((CirculatingBook) book).getBorrowerId())) { loans++; }
                if (book instanceof ReferenceBook && reader.getReaderId().equals(((ReferenceBook) book).getCurrentReaderId())) { readings++; }
            }
            if (loans > LibraryService.MAX_HOME_LOANS || readings > 1) {
                throw new IOException("Reader " + reader.getReaderId() + " exceeds the permitted active loan/session limits.");
            }
        }
        if (!library.checkConsistency()) { throw new IOException("Imported indexes are inconsistent."); }
        library.restoreHistory(history);
        if (history.isEmpty()) { library.recordActivity("Loaded input CSV " + path.getFileName()); }
        return library;
    }
    private IOException badRecord(int index, RuntimeException cause) {
        return new IOException("CSV record " + (index + 1) + ": " + cause.getMessage(), cause);
    }
    private boolean bool(String value) {
        if (value.isEmpty() || value.equals("false")) { return false; }
        if (value.equals("true")) { return true; }
        throw new IllegalArgumentException("Boolean must be true or false.");
    }
    private Reader requiredReader(Library library, String id) {
        Reader reader = library.findReader(id);
        if (reader == null) { throw new IllegalArgumentException("Unknown reader " + id); }
        return reader;
    }
    private List<String> row(String type, String id) {
        List<String> row = new ArrayList<>(java.util.Collections.nCopies(14, ""));
        row.set(0, type); row.set(1, id); return row;
    }
    public void save(Library library, Path path) throws IOException {
        if (!library.checkConsistency()) { throw new IOException("Cannot save inconsistent library indexes."); }
        List<List<String>> rows = new ArrayList<>(); rows.add(HEADER);
        rows.add(row("LIBRARY", library.getName()));
        for (Reader reader : library.getReaders()) {
            List<String> r = row("READER", reader.getReaderId()); r.set(3, reader.getFullName()); rows.add(r);
            for (String id : reader.getFavoriteBookIds()) { r = row("FAVORITE", reader.getReaderId()); r.set(13, id); rows.add(r); }
        }
        for (Librarian staff : library.getLibrarians()) {
            List<String> r = row("STAFF", staff.getStaffId()); r.set(3, staff.getFullName()); rows.add(r);
        }
        for (LibraryBook book : library.getBooks()) {
            List<String> r = row("BOOK", book.getInventoryId());
            r.set(3, book.getTitle()); r.set(4, book.getAuthor()); r.set(5, book.getIsbn()); r.set(6, String.valueOf(book.getPublicationYear()));
            if (book instanceof CirculatingBook) {
                CirculatingBook copy = (CirculatingBook) book;
                r.set(2, "CIRCULATING"); r.set(7, copy.getShelfCode()); r.set(11, String.valueOf(copy.isRenewed()));
                if (!copy.isAvailable()) {
                    requiredReader(library, copy.getBorrowerId());
                    r.set(9, copy.getBorrowerId()); r.set(10, copy.getDueDate().toString());
                }
            } else if (book instanceof ReferenceBook) {
                ReferenceBook copy = (ReferenceBook) book;
                r.set(2, "REFERENCE"); r.set(7, copy.getReadingRoom()); r.set(8, copy.getSubject());
                if (copy.isInUse()) { requiredReader(library, copy.getCurrentReaderId()); r.set(9, copy.getCurrentReaderId()); }
            } else { throw new IOException("Unsupported book subclass: " + book.getClass().getName()); }
            rows.add(r);
        }
        for (BorrowRequest request : library.getBorrowRequests().getPendingRequests()) {
            library.requireReader(request.getReader()); library.requiredBook(request.getInventoryId());
            List<String> r = row("REQUEST", request.getRequestId()); r.set(9, request.getReader().getReaderId());
            r.set(13, request.getInventoryId()); rows.add(r);
        }
        for (LibraryTask task : library.getTaskDispatcher().getPendingTasks()) {
            library.requiredBook(task.getInventoryId());
            List<String> r = row("TASK", task.getTaskId()); r.set(2, task.getType().name());
            r.set(12, String.valueOf(task.getPriority())); r.set(13, task.getInventoryId()); rows.add(r);
        }
        int i = 1;
        for (String entry : library.getActivityHistory()) { List<String> r = row("HISTORY", "" + i++); r.set(13, entry); rows.add(r); }
        CsvFile.writeAtomic(path, rows);
    }
    public void exportReports(Library library, Path directory) throws IOException {
        List<List<String>> catalog = new ArrayList<>();
        catalog.add(List.of("inventoryId", "type", "title", "author", "year", "status", "readerId", "dueDate", "favoriteCount"));
        List<LibraryBook> sorted = new ArrayList<>(library.getBooks()); sorted.sort(BookComparators.BY_TITLE);
        for (LibraryBook book : sorted) {
            String status = "AVAILABLE", readerId = "", date = "";
            if (book instanceof CirculatingBook && !((CirculatingBook) book).isAvailable()) {
                CirculatingBook copy = (CirculatingBook) book;
                status = copy.getDueDate().isBefore(LocalDate.now()) ? "OVERDUE" : "ON_LOAN";
                readerId = copy.getBorrowerId(); date = copy.getDueDate().toString();
            } else if (book instanceof ReferenceBook && ((ReferenceBook) book).isInUse()) {
                status = "READING"; readerId = ((ReferenceBook) book).getCurrentReaderId();
            }
            catalog.add(Arrays.asList(book.getInventoryId(), book.getClass().getSimpleName(), reportText(book.getTitle()),
                    reportText(book.getAuthor()), "" + book.getPublicationYear(), status, readerId, date,
                    "" + library.getFansOfBook(book.getInventoryId()).size()));
        }
        CsvFile.writeAtomic(directory.resolve("output_catalog.csv"), catalog);
        List<List<String>> summary = new ArrayList<>(); summary.add(List.of("metric", "value"));
        for (Map.Entry<String, Integer> entry : library.statistics().entrySet()) {
            summary.add(List.of(entry.getKey(), entry.getValue().toString()));
        }
        CsvFile.writeAtomic(directory.resolve("output_summary.csv"), summary);
        List<List<String>> history = new ArrayList<>(); history.add(List.of("sequence", "activity"));
        int number = 1;
        for (String entry : library.getActivityHistory()) { history.add(List.of("" + number++, reportText(entry))); }
        CsvFile.writeAtomic(directory.resolve("output_activity.csv"), history);
    }
    private String reportText(String text) {
        return !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }
}
