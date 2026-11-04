import java.time.Year;

public abstract class LibraryBook implements Comparable<LibraryBook> {
    private String inventoryId;
    private String title;
    private String author;
    private String isbn;
    private int publicationYear;

    protected LibraryBook(String inventoryId, String title, String author,
                          String isbn, int publicationYear) {
        setInventoryId(inventoryId);
        setTitle(title);
        setAuthor(author);
        setIsbn(isbn);
        setPublicationYear(publicationYear);
    }

    public final String getInventoryId() { return inventoryId; }
    public final String getTitle() { return title; }
    public final String getAuthor() { return author; }
    public final String getIsbn() { return isbn; }
    public final int getPublicationYear() { return publicationYear; }

    public final void setInventoryId(String inventoryId) {
        String validId = requireText(inventoryId, "Inventory ID");
        // Catalog identity must remain stable after construction.
        if (this.inventoryId != null && !this.inventoryId.equals(validId)) {
            throw new IllegalStateException("Inventory ID cannot be changed once assigned.");
        }
        this.inventoryId = validId;
    }

    public final void setTitle(String title) {
        this.title = requireText(title, "Title");
    }

    public final void setAuthor(String author) {
        this.author = requireText(author, "Author");
    }

    public final void setIsbn(String isbn) {
        // Basic validation only; ISBN checksum validation is outside this task.
        this.isbn = requireText(isbn, "ISBN");
    }

    public final void setPublicationYear(int publicationYear) {
        if (publicationYear < 1 || publicationYear > Year.now().getValue()) {
            throw new IllegalArgumentException("Publication year is invalid.");
        }
        this.publicationYear = publicationYear;
    }

    protected static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    public void displayInfo() {
        System.out.println("Inventory ID: " + inventoryId);
        System.out.println("Title: " + title);
        System.out.println("Author: " + author);
        System.out.println("ISBN: " + isbn);
        System.out.println("Publication year: " + publicationYear);
    }

    // Zero means that taking the book home is not permitted.
    public abstract int getLoanPeriodDays();

    public abstract String getUsageRules();

    @Override
    public final int compareTo(LibraryBook other) {
        return inventoryId.compareTo(other.inventoryId);
    }

    @Override
    public final boolean equals(Object other) {
        return this == other || other instanceof LibraryBook
                && inventoryId.equals(((LibraryBook) other).inventoryId);
    }

    @Override
    public final int hashCode() { return inventoryId.hashCode(); }
}
