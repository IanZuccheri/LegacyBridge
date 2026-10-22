package dev.legacybridge.converter.archive;

import dev.legacybridge.converter.operation.OperationControl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reads only the bounded ZIP end records before {@link java.util.zip.ZipFile}
 * is allowed to allocate its central-directory index.
 */
final class ZipCentralDirectoryPreflight {
    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int ZIP64_EOCD_SIGNATURE = 0x06064b50;
    private static final int ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
    private static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50;
    private static final int EOCD_MIN_BYTES = 22;
    private static final int MAX_COMMENT_BYTES = 0xffff;
    private static final int ZIP64_LOCATOR_BYTES = 20;
    private static final int ZIP64_EOCD_MIN_BYTES = 56;

    private ZipCentralDirectoryPreflight() {
    }

    static Summary inspect(Path input) throws IOException, InputRejectedException {
        try (FileChannel channel = FileChannel.open(input, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < EOCD_MIN_BYTES) {
                throw reject("MALFORMED_ZIP", "Input has no complete ZIP end record");
            }

            int tailLength = (int) Math.min(fileSize, EOCD_MIN_BYTES + MAX_COMMENT_BYTES);
            long tailOffset = fileSize - tailLength;
            ByteBuffer tail = readExactly(channel, tailOffset, tailLength);
            int eocdIndex = findEocd(tail, fileSize, tailOffset);
            if (eocdIndex < 0) {
                throw reject("MALFORMED_ZIP", "Input has no valid ZIP end record");
            }

            int diskNumber = unsignedShort(tail, eocdIndex + 4);
            int centralDirectoryDisk = unsignedShort(tail, eocdIndex + 6);
            int entriesOnDisk = unsignedShort(tail, eocdIndex + 8);
            int totalEntries = unsignedShort(tail, eocdIndex + 10);
            long centralDirectorySize = unsignedInt(tail, eocdIndex + 12);
            long centralDirectoryOffset = unsignedInt(tail, eocdIndex + 16);

            boolean zip64 = entriesOnDisk == 0xffff || totalEntries == 0xffff
                    || centralDirectorySize == 0xffffffffL
                    || centralDirectoryOffset == 0xffffffffL;
            if (!zip64) {
                requireSingleDisk(diskNumber, centralDirectoryDisk, entriesOnDisk, totalEntries);
                Summary summary = validate(
                        totalEntries,
                        centralDirectorySize,
                        centralDirectoryOffset,
                        fileSize,
                        tailOffset + eocdIndex
                );
                validateCentralDirectoryEntries(channel, centralDirectoryOffset, summary);
                return summary;
            }

            long absoluteEocdOffset = tailOffset + eocdIndex;
            if (absoluteEocdOffset < ZIP64_LOCATOR_BYTES) {
                throw reject("MALFORMED_ZIP", "ZIP64 locator is missing");
            }
            ByteBuffer locator = readExactly(
                    channel,
                    absoluteEocdOffset - ZIP64_LOCATOR_BYTES,
                    ZIP64_LOCATOR_BYTES
            );
            if (locator.getInt(0) != ZIP64_LOCATOR_SIGNATURE) {
                throw reject("MALFORMED_ZIP", "ZIP64 locator is missing");
            }
            long zip64Disk = unsignedInt(locator, 4);
            long zip64EocdOffset = locator.getLong(8);
            long diskCount = unsignedInt(locator, 16);
            if (zip64Disk != 0 || diskCount != 1) {
                throw reject("MULTI_DISK_ZIP", "Multi-disk ZIP archives are not supported");
            }
            if (zip64EocdOffset < 0 || zip64EocdOffset > fileSize - ZIP64_EOCD_MIN_BYTES) {
                throw reject("MALFORMED_ZIP", "ZIP64 end-record offset is outside the input");
            }
            ByteBuffer zip64End = readExactly(channel, zip64EocdOffset, ZIP64_EOCD_MIN_BYTES);
            if (zip64End.getInt(0) != ZIP64_EOCD_SIGNATURE) {
                throw reject("MALFORMED_ZIP", "ZIP64 end record is missing");
            }
            long recordSize = zip64End.getLong(4);
            if (recordSize < 44 || recordSize > fileSize - zip64EocdOffset - 12) {
                throw reject("MALFORMED_ZIP", "ZIP64 end-record size is invalid");
            }
            long disk = unsignedInt(zip64End, 16);
            long centralDisk = unsignedInt(zip64End, 20);
            long zip64EntriesOnDisk = zip64End.getLong(24);
            long zip64TotalEntries = zip64End.getLong(32);
            long zip64CentralSize = zip64End.getLong(40);
            long zip64CentralOffset = zip64End.getLong(48);
            if (disk != 0 || centralDisk != 0 || zip64EntriesOnDisk != zip64TotalEntries) {
                throw reject("MULTI_DISK_ZIP", "Multi-disk ZIP archives are not supported");
            }
            if (diskNumber != 0 && diskNumber != 0xffff
                    || centralDirectoryDisk != 0 && centralDirectoryDisk != 0xffff) {
                throw reject("MULTI_DISK_ZIP", "Multi-disk ZIP archives are not supported");
            }
            requireClassicAgreement(entriesOnDisk, 0xffff, zip64EntriesOnDisk, "entry count on disk");
            requireClassicAgreement(totalEntries, 0xffff, zip64TotalEntries, "total entry count");
            requireClassicAgreement(
                    centralDirectorySize,
                    0xffffffffL,
                    zip64CentralSize,
                    "central-directory size"
            );
            requireClassicAgreement(
                    centralDirectoryOffset,
                    0xffffffffL,
                    zip64CentralOffset,
                    "central-directory offset"
            );
            long locatorOffset = absoluteEocdOffset - ZIP64_LOCATOR_BYTES;
            if (zip64EocdOffset + 12 + recordSize != locatorOffset) {
                throw reject("NON_CANONICAL_ZIP_LAYOUT",
                        "ZIP64 end record and locator are not contiguous");
            }
            Summary summary = validate(
                    zip64TotalEntries,
                    zip64CentralSize,
                    zip64CentralOffset,
                    fileSize,
                    zip64EocdOffset
            );
            validateCentralDirectoryEntries(channel, zip64CentralOffset, summary);
            return summary;
        }
    }

    private static void validateCentralDirectoryEntries(
            FileChannel channel,
            long directoryOffset,
            Summary summary
    ) throws IOException, InputRejectedException {
        long cursor = directoryOffset;
        long end = directoryOffset + summary.centralDirectoryBytes();
        long actualEntries = 0;
        ByteBuffer fixedHeader = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);

        while (cursor < end) {
            OperationControl.checkpoint();
            long remaining = end - cursor;
            if (remaining < 4) {
                throw reject("MALFORMED_ZIP", "ZIP central directory ends inside a record");
            }
            fixedHeader.clear();
            fixedHeader.limit((int) Math.min(fixedHeader.capacity(), remaining));
            readExactly(channel, cursor, fixedHeader);
            int signature = fixedHeader.getInt(0);
            if (signature == CENTRAL_FILE_HEADER_SIGNATURE) {
                if (remaining < fixedHeader.capacity()) {
                    throw reject("MALFORMED_ZIP", "ZIP central file header is incomplete");
                }
                long variableBytes = (long) unsignedShort(fixedHeader, 28)
                        + unsignedShort(fixedHeader, 30)
                        + unsignedShort(fixedHeader, 32);
                if (unsignedShort(fixedHeader, 34) != 0) {
                    throw reject("MULTI_DISK_ZIP",
                            "Central-directory entries from another disk are not supported");
                }
                long recordBytes = fixedHeader.capacity() + variableBytes;
                if (recordBytes > remaining) {
                    throw reject("MALFORMED_ZIP", "ZIP central file header exceeds its directory");
                }
                cursor += recordBytes;
                actualEntries = Math.incrementExact(actualEntries);
                if (actualEntries > ArchivePolicy.MAX_ENTRY_COUNT) {
                    throw reject("ENTRY_COUNT_LIMIT", "Archive contains too many entries");
                }
                continue;
            }
            throw reject("MALFORMED_ZIP", "ZIP central directory contains an unsupported record");
        }
        if (actualEntries != summary.entryCount()) {
            throw reject("CENTRAL_DIRECTORY_MISMATCH",
                    "ZIP end record does not match the central-directory entry count");
        }
    }

    private static Summary validate(
            long entries,
            long directorySize,
            long directoryOffset,
            long fileSize,
            long expectedDirectoryEnd
    )
            throws InputRejectedException {
        if (entries < 0 || directorySize < 0 || directoryOffset < 0) {
            throw reject("MALFORMED_ZIP", "ZIP central-directory values overflow signed limits");
        }
        if (entries > ArchivePolicy.MAX_ENTRY_COUNT) {
            throw reject("ENTRY_COUNT_LIMIT", "Archive contains too many entries");
        }
        if (directorySize > ArchivePolicy.MAX_CENTRAL_DIRECTORY_BYTES) {
            throw reject("CENTRAL_DIRECTORY_LIMIT", "ZIP central directory exceeds policy limit");
        }
        if (directoryOffset > fileSize || directorySize > fileSize - directoryOffset) {
            throw reject("MALFORMED_ZIP", "ZIP central directory lies outside the input");
        }
        if (directoryOffset + directorySize != expectedDirectoryEnd) {
            throw reject("NON_CANONICAL_ZIP_LAYOUT",
                    "Self-extracting or gapped ZIP layouts are outside the analyzer subset");
        }
        return new Summary(entries, directorySize);
    }

    private static void requireSingleDisk(
            int disk,
            int centralDisk,
            int entriesOnDisk,
            int totalEntries
    ) throws InputRejectedException {
        if (disk != 0 || centralDisk != 0 || entriesOnDisk != totalEntries) {
            throw reject("MULTI_DISK_ZIP", "Multi-disk ZIP archives are not supported");
        }
    }

    private static void requireClassicAgreement(
            long classicValue,
            long sentinel,
            long zip64Value,
            String label
    ) throws InputRejectedException {
        if (classicValue != sentinel && classicValue != zip64Value) {
            throw reject("CENTRAL_DIRECTORY_MISMATCH",
                    "Classic ZIP and ZIP64 disagree on " + label);
        }
    }

    private static int findEocd(ByteBuffer tail, long fileSize, long tailOffset) {
        for (int index = tail.limit() - EOCD_MIN_BYTES; index >= 0; index--) {
            if ((index & 1023) == 0) OperationControl.checkpoint();
            if (tail.getInt(index) != EOCD_SIGNATURE) {
                continue;
            }
            int commentLength = unsignedShort(tail, index + 20);
            long absoluteEnd = tailOffset + index + EOCD_MIN_BYTES + commentLength;
            if (absoluteEnd == fileSize) {
                return index;
            }
        }
        return -1;
    }

    private static ByteBuffer readExactly(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        readExactly(channel, offset, buffer);
        return buffer;
    }

    private static void readExactly(FileChannel channel, long offset, ByteBuffer buffer) throws IOException {
        int noProgressReads = 0;
        while (buffer.hasRemaining()) {
            OperationControl.checkpoint();
            int read = channel.read(buffer, offset + buffer.position());
            if (read < 0) {
                throw new IOException("Unexpected end of file while reading ZIP structures");
            }
            if (read == 0) {
                noProgressReads++;
                if (noProgressReads >= 16) {
                    throw new IOException("No progress while reading ZIP structures");
                }
            } else {
                noProgressReads = 0;
            }
        }
        buffer.flip();
    }

    private static int unsignedShort(ByteBuffer buffer, int index) {
        return Short.toUnsignedInt(buffer.getShort(index));
    }

    private static long unsignedInt(ByteBuffer buffer, int index) {
        return Integer.toUnsignedLong(buffer.getInt(index));
    }

    private static InputRejectedException reject(String code, String message) {
        return new InputRejectedException(code, message);
    }

    record Summary(long entryCount, long centralDirectoryBytes) {
    }
}
