public interface Borrowable {
    boolean borrowBook(String readerId);
    boolean returnBook();
}
