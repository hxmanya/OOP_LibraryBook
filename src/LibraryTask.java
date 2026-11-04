import java.util.Objects;

public final class LibraryTask {
    public enum Type { RETURN_BOOK, RENEW_LOAN, FINISH_READING }

    private final String taskId;
    private final Type type;
    private final String inventoryId;
    private final int priority;

    public LibraryTask(String taskId, Type type, String inventoryId, int priority) {
        this.taskId = LibraryBook.requireText(taskId, "Task ID");
        this.type = Objects.requireNonNull(type, "Task type cannot be null.");
        this.inventoryId = LibraryBook.requireText(inventoryId, "Inventory ID");
        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException("Priority must be 1, 2 or 3.");
        }
        this.priority = priority;
    }

    public String getTaskId() { return taskId; }
    public Type getType() { return type; }
    public String getInventoryId() { return inventoryId; }
    public int getPriority() { return priority; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LibraryTask
                && taskId.equals(((LibraryTask) other).taskId);
    }

    @Override
    public int hashCode() { return taskId.hashCode(); }

    public boolean execute(Library library, LibraryService service) {
        Objects.requireNonNull(library, "Library cannot be null.");
        Objects.requireNonNull(service, "Service cannot be null.");
        switch (type) {
            case RETURN_BOOK:
                return service.returnBook(library, inventoryId);
            case RENEW_LOAN:
                return service.renewLoan(library, inventoryId);
            case FINISH_READING:
                return service.finishReading(library, inventoryId);
            default:
                throw new IllegalStateException("Unsupported task type.");
        }
    }

    @Override
    public String toString() {
        return taskId + " | priority=" + priority + " | " + type + " | " + inventoryId;
    }
}
