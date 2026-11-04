import java.util.Objects;

public final class BorrowRequest {
    private final String requestId;
    private final String inventoryId;
    private final Reader reader;

    public BorrowRequest(String requestId, String inventoryId, Reader reader) {
        this.requestId = LibraryBook.requireText(requestId, "Request ID");
        this.inventoryId = LibraryBook.requireText(inventoryId, "Inventory ID");
        this.reader = Objects.requireNonNull(reader, "Reader cannot be null.");
    }

    public String getRequestId() { return requestId; }
    public String getInventoryId() { return inventoryId; }
    public Reader getReader() { return reader; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BorrowRequest
                && requestId.equals(((BorrowRequest) other).requestId);
    }

    @Override
    public int hashCode() { return requestId.hashCode(); }

    @Override
    public String toString() {
        return requestId + " | " + reader.getReaderId() + " | " + inventoryId;
    }
}
