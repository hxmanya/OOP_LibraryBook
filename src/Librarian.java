public final class Librarian {
    private final String staffId;
    private String fullName;

    public Librarian(String staffId, String fullName) {
        this.staffId = LibraryBook.requireText(staffId, "Staff ID");
        setFullName(fullName);
    }

    public String getStaffId() { return staffId; }
    public String getFullName() { return fullName; }
    public void setFullName(String name) { fullName = LibraryBook.requireText(name, "Staff name"); }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Librarian
                && staffId.equals(((Librarian) other).staffId);
    }

    @Override
    public int hashCode() { return staffId.hashCode(); }

    @Override
    public String toString() { return staffId + " - " + fullName; }
}
