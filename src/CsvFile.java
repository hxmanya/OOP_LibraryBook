import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

// UTF-8 CSV with commas, escaped quotes and quoted multiline fields; no external libraries.
public final class CsvFile {
    private CsvFile() { }
    public static List<List<String>> read(Path path) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (PushbackReader in = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false, closed = false, first = true;
            int value;
            while ((value = in.read()) != -1) {
                char ch = (char) value;
                if (first && ch == '\uFEFF') { first = false; continue; }
                first = false;
                if (quoted) {
                    if (ch == '"') {
                        int next = in.read();
                        if (next == '"') { field.append('"'); }
                        else { quoted = false; closed = true; if (next != -1) { in.unread(next); } }
                    } else { field.append(ch); }
                } else if (ch == ',') {
                    row.add(field.toString()); field.setLength(0); closed = false;
                } else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r') { int next = in.read(); if (next != '\n' && next != -1) { in.unread(next); } }
                    row.add(field.toString()); rows.add(row); row = new ArrayList<>();
                    field.setLength(0); closed = false;
                } else if (closed) {
                    throw new IOException("Unexpected character after closing CSV quote.");
                } else if (ch == '"') {
                    if (field.length() != 0) { throw new IOException("Quote inside an unquoted CSV field."); }
                    quoted = true;
                } else { field.append(ch); }
            }
            if (quoted) { throw new IOException("Unclosed quoted CSV field."); }
            if (!row.isEmpty() || field.length() != 0 || closed) { row.add(field.toString()); rows.add(row); }
        }
        return rows;
    }
    public static void writeAtomic(Path path, List<List<String>> rows) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (List<String> row : rows) {
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) { writer.write(','); }
                        String value = row.get(i);
                        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                            writer.write('"'); writer.write(value.replace("\"", "\"\"")); writer.write('"');
                        } else { writer.write(value); }
                    }
                    writer.newLine();
                }
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
