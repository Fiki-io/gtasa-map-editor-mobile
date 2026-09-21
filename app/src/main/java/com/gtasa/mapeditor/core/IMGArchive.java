package com.gtasa.mapeditor.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reader for GTA SA IMG Version 2 archives (e.g. gta3.img, gta_int.img).
 */
public class IMGArchive implements AutoCloseable {

    public static class Entry {
        public final int offsetSectors;
        public final int sizeSectors;
        public final String name;

        public Entry(int offsetSectors, int sizeSectors, String name) {
            this.offsetSectors = offsetSectors;
            this.sizeSectors = sizeSectors;
            this.name = name;
        }

        public long getByteOffset() {
            return (long) offsetSectors * 2048L;
        }

        public int getByteSize() {
            return sizeSectors * 2048;
        }
    }

    private final File imgFile;
    private RandomAccessFile raf;
    private final Map<String, Entry> entryMap = new HashMap<>();

    public IMGArchive(File imgFile) {
        this.imgFile = imgFile;
    }

    public void open() throws IOException {
        close();
        if (!imgFile.exists() || !imgFile.canRead()) {
            throw new IOException("IMG file does not exist or cannot be read: " + imgFile.getAbsolutePath());
        }

        raf = new RandomAccessFile(imgFile, "r");
        byte[] headerBytes = new byte[8];
        raf.readFully(headerBytes);

        ByteBuffer headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        headerBuf.get(magic);
        String magicStr = new String(magic, StandardCharsets.US_ASCII);

        if (!"VER2".equals(magicStr)) {
            throw new IOException("Unsupported IMG version. Expected 'VER2', got: " + magicStr);
        }

        int numEntries = headerBuf.getInt();
        byte[] dirBytes = new byte[numEntries * 32];
        raf.readFully(dirBytes);

        ByteBuffer dirBuf = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] nameBytes = new byte[24];

        for (int i = 0; i < numEntries; i++) {
            int offset = dirBuf.getInt();
            int size = dirBuf.getShort() & 0xFFFF;
            dirBuf.getShort(); // streaming size / padding
            dirBuf.get(nameBytes);

            int nameLen = 0;
            while (nameLen < 24 && nameBytes[nameLen] != 0) {
                nameLen++;
            }
            String name = new String(nameBytes, 0, nameLen, StandardCharsets.US_ASCII).toLowerCase();
            entryMap.put(name, new Entry(offset, size, name));
        }
    }

    public boolean hasEntry(String entryName) {
        return entryMap.containsKey(entryName.toLowerCase());
    }

    public Entry getEntry(String entryName) {
        return entryMap.get(entryName.toLowerCase());
    }

    public byte[] readEntry(String entryName) throws IOException {
        Entry entry = getEntry(entryName);
        if (entry == null) {
            return null;
        }
        return readEntry(entry);
    }

    public synchronized byte[] readEntry(Entry entry) throws IOException {
        if (raf == null) {
            throw new IOException("IMG archive is not open");
        }
        raf.seek(entry.getByteOffset());
        byte[] buffer = new byte[entry.getByteSize()];
        raf.readFully(buffer);
        return buffer;
    }

    public int getEntryCount() {
        return entryMap.size();
    }

    @Override
    public void close() {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {}
            raf = null;
        }
        entryMap.clear();
    }
}
