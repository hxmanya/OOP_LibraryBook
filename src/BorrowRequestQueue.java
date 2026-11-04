import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BorrowRequestQueue {
    private final Queue<BorrowRequest> requests = new LinkedList<>();
    private final Set<BorrowRequest> pending = new HashSet<>();

    public void offer(BorrowRequest request) {
        Objects.requireNonNull(request, "Request cannot be null.");
        if (!pending.add(request)) { throw new IllegalArgumentException("Request ID is already pending."); }
        requests.offer(request);
    }

    public BorrowRequest peek() { return requests.peek(); }
    public boolean isEmpty() { return requests.isEmpty(); }
    public int size() { return requests.size(); }
    public List<BorrowRequest> getPendingRequests() { return List.copyOf(requests); }
    public int cancelForBook(String id) { return cancel(id, false); }
    public int cancelForReader(String id) { return cancel(id, true); }
    public boolean cancelOwn(String requestId, String readerId) {
        java.util.Iterator<BorrowRequest> iterator = requests.iterator();
        while (iterator.hasNext()) {
            BorrowRequest request = iterator.next();
            if (request.getRequestId().equals(requestId) && request.getReader().getReaderId().equals(readerId)) {
                iterator.remove(); pending.remove(request); return true;
            }
        }
        return false;
    }
    private int cancel(String id, boolean byReader) {
        int count = 0;
        java.util.Iterator<BorrowRequest> iterator = requests.iterator();
        while (iterator.hasNext()) {
            BorrowRequest request = iterator.next();
            if (id.equals(byReader ? request.getReader().getReaderId() : request.getInventoryId())) {
                iterator.remove(); pending.remove(request); count++;
            }
        }
        return count;
    }

    // Each request is attempted once. A rejection does not stop later requests.
    // Returns null when empty; the returned text describes the processed request.
    public String processNext(Library library, LibraryService service) {
        Objects.requireNonNull(library, "Library cannot be null.");
        Objects.requireNonNull(service, "Service cannot be null.");
        BorrowRequest request = requests.poll();
        if (request == null) { return null; }
        pending.remove(request);
        try {
            boolean success = service.borrowBook(library,
                    request.getInventoryId(), request.getReader());
            return request + (success ? " | ISSUED" : " | UNAVAILABLE");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return request + " | REJECTED: " + e.getMessage();
        }
    }
}
