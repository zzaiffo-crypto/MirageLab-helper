package miragelab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable small state-file writes: backup original, stage in same folder, atomic replace. */
final class SafeStateStore {
    private static final Set<Path> snapshotted = new HashSet<>();
    private SafeStateStore() {}

    static synchronized void write(Path target, String text) throws IOException {
        if (text == null || text.length() > 1024 * 1024) throw new IOException("State content invalid/too large");
        Path file = target.toAbsolutePath().normalize();
        Path parent = file.getParent();
        if (parent == null) throw new IOException("No state parent folder");
        Files.createDirectories(parent);
        Path backup = file.resolveSibling(file.getFileName().toString() + ".pre_v9.bak");
        if (!snapshotted.contains(file) && Files.isRegularFile(file)) {
            // Stable pre-v9 recovery point. Never overwrite an existing preserved backup.
            if (!Files.exists(backup)) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            snapshotted.add(file);
        } else if (!snapshotted.contains(file)) {
            snapshotted.add(file);
        }
        Path temp = Files.createTempFile(parent,".miragelab_", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    /** Keep unknown external keys rather than discarding them on settings updates. */
    static String preserveUnknown(String previous, String fresh) {
        Set<String> known = new HashSet<>();
        for(String line : fresh.split("\\R")) {
            int eq=line.indexOf('=');
            if(eq>0) known.add(line.substring(0,eq).trim().toLowerCase(Locale.ROOT));
        }
        StringBuilder extra=new StringBuilder();
        for(String line : previous.split("\\R")) {
            String t=line.trim();
            if(t.isEmpty()||t.startsWith("#")) continue;
            int eq=t.indexOf('=');
            if(eq>0 && !known.contains(t.substring(0,eq).trim().toLowerCase(Locale.ROOT))) extra.append(line).append('\n');
        }
        return extra.length()==0 ? fresh : fresh+"# Campos adicionais preservados da configuracao anterior\n"+extra;
    }
}
