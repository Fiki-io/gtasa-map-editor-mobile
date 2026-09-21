package com.gtasa.mapeditor.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GTA SA data/gta.dat manifest file.
 */
public class DATParser {

    public static class Entry {
        public static final int TYPE_IDE = 0;
        public static final int TYPE_IPL = 1;

        public final int type;
        public final String relativePath;
        public final String fileName;

        public Entry(int type, String relativePath) {
            this.type = type;
            this.relativePath = relativePath.replace('\\', '/');
            this.fileName = new File(this.relativePath).getName();
        }
    }

    private final List<Entry> ideEntries = new ArrayList<>();
    private final List<Entry> iplEntries = new ArrayList<>();

    public DATParser() {}

    public void load(File gtaDatFile) throws IOException {
        try (InputStream is = new FileInputStream(gtaDatFile)) {
            load(is);
        }
    }

    public void load(InputStream inputStream) throws IOException {
        ideEntries.clear();
        iplEntries.clear();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                String cmd = parts[0].toUpperCase();
                String path = parts[1];

                if (cmd.equals("IDE")) {
                    ideEntries.add(new Entry(Entry.TYPE_IDE, path));
                } else if (cmd.equals("IPL")) {
                    // Ignore zone and cull files
                    if (!path.toUpperCase().contains("ZON") && !path.toUpperCase().contains("CULL")) {
                        iplEntries.add(new Entry(Entry.TYPE_IPL, path));
                    }
                }
            }
        }
    }

    public List<Entry> getIdeEntries() {
        return ideEntries;
    }

    public List<Entry> getIplEntries() {
        return iplEntries;
    }
}
