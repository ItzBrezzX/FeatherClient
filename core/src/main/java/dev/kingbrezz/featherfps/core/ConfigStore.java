package dev.kingbrezz.featherfps.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persistence: generates defaults, recovers from corruption, saves atomically, keeps rotating backups.
 * It never overwrites a file it could not read without first moving that file aside.
 */
public final class ConfigStore {
    public static final String FILE_NAME = "feather-fps.properties";
    public static final String BACKUP_DIR_NAME = "feather-fps-backups";

    private final Path file;
    private final Path backupDir;
    private final Migrations migrations;

    public ConfigStore(Path configDir) { this(configDir, new Migrations()); }

    public ConfigStore(Path configDir, Migrations migrations) {
        this.file = configDir.resolve(FILE_NAME);
        this.backupDir = configDir.resolve(BACKUP_DIR_NAME);
        this.migrations = migrations;
    }

    public Path file() { return file; }

    /** Loads the config. Never throws: on any problem it falls back to defaults and says why in the warnings. */
    public LoadResult load() {
        if (!Files.exists(file)) {
            FeatherConfig defaults = new FeatherConfig();
            List<String> warnings = new ArrayList<>();
            try {
                save(defaults);
            } catch (IOException e) {
                warnings.add("Could not write the default config: " + e.getMessage());
            }
            return new LoadResult(defaults, List.copyOf(warnings), false, true);
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return ConfigFormat.parse(r, migrations);
        } catch (IOException | RuntimeException e) {
            List<String> warnings = new ArrayList<>();
            Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            try {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
                warnings.add("Config could not be read (" + e.getMessage() + "). It was moved to " + aside.getFileName() + " and defaults were restored.");
            } catch (IOException moveFailure) {
                warnings.add("Config could not be read (" + e.getMessage() + ") and could not be moved aside: " + moveFailure.getMessage());
            }
            FeatherConfig defaults = new FeatherConfig();
            try {
                save(defaults);
            } catch (IOException saveFailure) {
                warnings.add("Could not write the default config: " + saveFailure.getMessage());
            }
            return new LoadResult(defaults, List.copyOf(warnings), true, true);
        }
    }

    /** Atomically writes the config. A changed existing file is backed up first; an unchanged one is left alone. */
    public void save(FeatherConfig config) throws IOException {
        config.prepareForSave();
        String text = ConfigFormat.render(config);
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.exists(file)) {
            String old = null;
            try {
                old = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // unreadable (e.g. invalid UTF-8): treat as changed so it is backed up below
            }
            if (text.equals(old)) return;
            backup(config.getInt("advanced.configBackups"));
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public List<Path> listBackups() throws IOException {
        if (!Files.isDirectory(backupDir)) return List.of();
        try (Stream<Path> s = Files.list(backupDir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("feather-fps-")).sorted().toList();
        }
    }

    /** Writes a copy of the current settings to {@code target} (for sharing or manual backup). */
    public void exportTo(FeatherConfig config, Path target) throws IOException {
        config.prepareForSave();
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(target, ConfigFormat.render(config), StandardCharsets.UTF_8);
    }

    /** Reads a config from {@code source} with the same validation as loading. Throws if the file is unreadable. */
    public LoadResult importFrom(Path source) throws IOException {
        try (Reader r = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            return ConfigFormat.parse(r, migrations);
        }
    }

    private void backup(int keep) throws IOException {
        if (keep <= 0) return;
        Files.createDirectories(backupDir);
        long stamp = System.currentTimeMillis();
        int n = 0;
        Path target;
        do {
            target = backupDir.resolve(String.format("feather-fps-%013d-%03d.properties", stamp, n++));
        } while (Files.exists(target));
        Files.copy(file, target);
        List<Path> all = listBackups();
        for (int i = 0; i < all.size() - keep; i++) Files.deleteIfExists(all.get(i));
    }
}
