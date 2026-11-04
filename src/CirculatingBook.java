import java.time.LocalDate;

public class CirculatingBook extends LibraryBook implements Borrowable {
    private String shelfCode;
    private String borrowerId;
    private LocalDate dueDate;
    private boolean renewed;

    public CirculatingBook(String inventoryId, String title, String author,
                           String isbn, int publicationYear, String shelfCode) {
        super(inventoryId, title, author, isbn, publicationYear);
        this.shelfCode = requireText(shelfCode, "Shelf code");
    }

    public String getShelfCode() { return shelfCode; }
    public String getBorrowerId() { return borrowerId; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isRenewed() { return renewed; }
    public boolean isAvailable() { return borrowerId == null; }

    // Persistence-only restoration; the CSV loader validates referenced readers.
    void restoreLoan(String readerId, LocalDate date, boolean wasRenewed) {
        borrowerId = requireText(readerId, "Borrower ID");
        dueDate = java.util.Objects.requireNonNull(date, "Due date cannot be null.");
        renewed = wasRenewed;
    }

    public void setShelfCode(String shelfCode) {
        this.shelfCode = requireText(shelfCode, "Shelf code");
    }

    @Override
    public int getLoanPeriodDays() {
        return 14;
    }

    @Override
    public String getUsageRules() {
        return "Take home for 14 days; one 14-day renewal before overdue.";
    }

    @Override
    public boolean borrowBook(String readerId) {
        String validReaderId = requireText(readerId, "Reader ID");
        if (!isAvailable()) {
            return false;
        }
        borrowerId = validReaderId;
        dueDate = LocalDate.now().plusDays(getLoanPeriodDays());
        renewed = false;
        return true;
    }

    @Override
    public boolean returnBook() {
        if (isAvailable()) {
            return false;
        }
        borrowerId = null;
        dueDate = null;
        renewed = false;
        return true;
    }

    // Unique behavior: extend an active loan once, if it is not overdue.
    public boolean renewLoan() {
        if (isAvailable() || renewed || LocalDate.now().isAfter(dueDate)) {
            return false;
        }
        dueDate = dueDate.plusDays(getLoanPeriodDays());
        renewed = true;
        return true;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("Type: Circulating book");
        System.out.println("Home shelf: " + shelfCode);
        System.out.println("Available for loan: " + isAvailable());
        if (!isAvailable()) {
            System.out.println("Borrower: " + borrowerId);
            System.out.println("Due date: " + dueDate);
            System.out.println("Renewed: " + renewed);
        }
    }
}
