package lk.hms.modules.patients.service;

import lk.hms.common.exception.BusinessException;
import lk.hms.config.HmsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores uploaded files outside the web root under random names. The file type is decided
 * from the content's magic bytes, never from the client-supplied name or Content-Type, so a
 * script renamed to .pdf is rejected.
 */
@Component
public class DocumentStorage {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public DocumentStorage(HmsProperties properties) {
        this.root = Path.of(properties.uploadDir() == null ? "./data/uploads" : properties.uploadDir())
                .toAbsolutePath().normalize();
    }

    public record Stored(String storedName, String contentType, long size, String sha256) {
    }

    public Stored store(byte[] bytes) {
        if (bytes.length == 0) {
            throw BusinessException.badRequest("FILE_EMPTY", "The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Files must be 10 MB or smaller.");
        }
        String contentType = detectType(bytes);
        if (contentType == null) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE_NOT_ALLOWED",
                    "Only PDF, JPEG and PNG files can be uploaded.");
        }
        String name = UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            Files.write(resolve(name), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store uploaded file", e);
        }
        return new Stored(name, contentType, bytes.length, sha256(bytes));
    }

    public InputStream open(String storedName) throws IOException {
        return Files.newInputStream(resolve(storedName));
    }

    /** Best-effort clean-up when the database part of an upload fails. */
    public void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ignored) {
            // orphaned file is harmless and can be swept later; the DB row was never committed
        }
    }

    /** Only UUID names are ever resolved, and the result must stay inside the root (no traversal). */
    private Path resolve(String storedName) {
        UUID.fromString(storedName);
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return p;
    }

    static String detectType(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
