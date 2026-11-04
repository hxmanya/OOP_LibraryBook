public class ReferenceBook extends LibraryBook implements InLibraryReadable {
    private String subject;
    private String readingRoom;
    private String currentReaderId;

    public ReferenceBook(String inventoryId, String title, String author,
                         String isbn, int publicationYear, String subject,
                         String readingRoom) {
        super(inventoryId, title, author, isbn, publicationYear);
        this.subject = requireText(subject, "Subject");
        this.readingRoom = requireText(readingRoom, "Reading room");
    }

    public String getSubject() { return subject; }
    public String getReadingRoom() { return readingRoom; }
    public String getCurrentReaderId() { return currentReaderId; }
    public boolean isInUse() { return currentReaderId != null; }

    public void setSubject(String subject) {
        this.subject = requireText(subject, "Subject");
    }

    public void setReadingRoom(String readingRoom) {
        String validRoom = requireText(readingRoom, "Reading room");
        if (isInUse()) {
            throw new IllegalStateException("Cannot relocate a book in use.");
        }
        this.readingRoom = validRoom;
    }

    @Override
    public int getLoanPeriodDays() {
        return 0;
    }

    @Override
    public String getUsageRules() {
        return "Read only in " + readingRoom + "; taking home is not allowed.";
    }

    @Override
    public boolean startReading(String readerId) {
        String validReaderId = requireText(readerId, "Reader ID");
        if (isInUse()) {
            return false;
        }
        currentReaderId = validReaderId;
        return true;
    }

    @Override
    public boolean finishReading() {
        if (!isInUse()) {
            return false;
        }
        currentReaderId = null;
        return true;
    }

    // Unique behavior: match a catalog subject, ignoring letter case.
    public boolean matchesSubject(String requestedSubject) {
        return subject.equalsIgnoreCase(requireText(requestedSubject, "Subject"));
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("Type: Reference book");
        System.out.println("Subject: " + subject);
        System.out.println("Reading room: " + readingRoom);
        System.out.println("In use: " + isInUse());
        if (isInUse()) {
            System.out.println("Current reader: " + currentReaderId);
        }
    }
}
