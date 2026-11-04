import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public final class PriorityTaskDispatcher {
    // Priority 1 is highest. Equal priorities follow submission order.
    private final Queue<ScheduledTask> tasks = new PriorityQueue<>(
            Comparator.comparingInt((ScheduledTask item) -> item.task.getPriority())
                    .thenComparingLong(item -> item.sequence));
    private long nextSequence;
    private final Set<LibraryTask> pending = new HashSet<>();

    public void offer(LibraryTask task) {
        Objects.requireNonNull(task, "Task cannot be null.");
        if (!pending.add(task)) { throw new IllegalArgumentException("Task ID is already pending."); }
        tasks.offer(new ScheduledTask(task, nextSequence++));
    }

    public LibraryTask peek() {
        ScheduledTask next = tasks.peek();
        return next == null ? null : next.task;
    }

    public boolean isEmpty() { return tasks.isEmpty(); }
    public int size() { return tasks.size(); }
    public List<LibraryTask> getPendingTasks() {
        Queue<ScheduledTask> copy = new PriorityQueue<>(tasks);
        List<LibraryTask> result = new ArrayList<>();
        while (!copy.isEmpty()) { result.add(copy.poll().task); }
        return List.copyOf(result);
    }
    public int cancelForBook(String id) {
        int count = 0;
        java.util.Iterator<ScheduledTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            ScheduledTask item = iterator.next();
            if (item.task.getInventoryId().equals(id)) {
                iterator.remove(); pending.remove(item.task); count++;
            }
        }
        return count;
    }

    // Poll in priority order: iteration over a PriorityQueue is not sorted.
    // Each task is attempted once; failed tasks are reported and removed.
    public String dispatchNext(Library library, LibraryService service) {
        Objects.requireNonNull(library, "Library cannot be null.");
        Objects.requireNonNull(service, "Service cannot be null.");
        ScheduledTask next = tasks.poll();
        if (next == null) { return null; }
        pending.remove(next.task);
        try {
            boolean success = next.task.execute(library, service);
            return next.task + (success ? " | COMPLETED" : " | NOT COMPLETED");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return next.task + " | REJECTED: " + e.getMessage();
        }
    }

    // The sequence belongs to this queue entry, not to the task itself.
    private static final class ScheduledTask {
        private final LibraryTask task;
        private final long sequence;

        private ScheduledTask(LibraryTask task, long sequence) {
            this.task = task;
            this.sequence = sequence;
        }
    }
}
