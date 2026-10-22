package dev.legacybridge.converter.mapping.source;

import dev.legacybridge.converter.operation.OperationControl;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class BoundedInputs {
    static final long MAPPING_BYTES = 32L * 1024 * 1024;
    static final long JAR_BYTES = 256L * 1024 * 1024;
    static final long CLASS_BYTES = 16L * 1024 * 1024;
    static final long INFLATED_BYTES = 1024L * 1024 * 1024;
    static final int ENTRY_COUNT = 100_000;

    private BoundedInputs() { }

    static InputStream open(Path path, long limit, String label) throws IOException {
        OperationControl.checkpoint();
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException(label + " must be a regular file, not a symbolic link");
        }
        if (attributes.size() > limit) {
            throw new IOException(label + " exceeds byte limit " + limit);
        }
        return new FilterInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            private long readBytes;

            private void account(long amount) throws IOException {
                OperationControl.checkpoint();
                readBytes += amount;
                if (readBytes > limit) {
                    throw new IOException(label + " exceeds byte limit " + limit);
                }
            }

            @Override
            public int read() throws IOException {
                OperationControl.checkpoint();
                int value = in.read();
                if (value >= 0) account(1);
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                OperationControl.checkpoint();
                int count = in.read(bytes, offset, length);
                if (count > 0) account(count);
                return count;
            }
        };
    }

    static byte[] read(InputStream input, long limit, String label) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transfer(input, limit, label, output);
        return output.toByteArray();
    }

    static long transfer(InputStream input, long limit, String label, ByteArrayOutputStream output)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int stalls = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            OperationControl.checkpoint();
            if (count == 0) {
                if (++stalls > 16) throw new IOException(label + " stream made no progress");
                continue;
            }
            stalls = 0;
            total += count;
            if (total > limit) throw new IOException(label + " exceeds byte limit " + limit);
            if (output != null) output.write(buffer, 0, count);
        }
        return total;
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String hash(byte[] bytes) {
        var digest = sha256();
        for (int offset = 0; offset < bytes.length; offset += 65536) {
            OperationControl.checkpoint();
            digest.update(bytes, offset, Math.min(65536, bytes.length - offset));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
