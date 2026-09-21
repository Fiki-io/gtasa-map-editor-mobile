package com.gtasa.mapeditor.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clean-room implementation of GTA SA Item Placement (.ipl) file parser and serializer.
 */
public class IPLParser {

    public static class IPLItem {
        public int id;
        public String modelName = "";
        public int interior = 0;
        public float posX, posY, posZ;
        public float rotX, rotY, rotZ, rotW = 1.0f;
        public int lod = -1;

        public boolean isModified = false;
        public boolean isDeleted = false;
        public int originalLineIndex = -1;

        public IPLItem() {}

        public IPLItem(int id, String modelName, int interior,
                       float posX, float posY, float posZ,
                       float rotX, float rotY, float rotZ, float rotW,
                       int lod) {
            this.id = id;
            this.modelName = modelName;
            this.interior = interior;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.rotW = rotW;
            this.lod = lod;
        }

        public IPLItem copy() {
            IPLItem item = new IPLItem(id, modelName, interior, posX, posY, posZ, rotX, rotY, rotZ, rotW, lod);
            item.isModified = true;
            return item;
        }

        public String toIPLString() {
            return String.format(Locale.US, "%d, %s, %d, %.6f, %.6f, %.6f, %.8f, %.8f, %.8f, %.8f, %d",
                    id, modelName, interior, posX, posY, posZ, rotX, rotY, rotZ, rotW, lod);
        }
    }

    private final List<IPLItem> items = new ArrayList<>();
    private final List<String> rawLines = new ArrayList<>();
    private File sourceFile;

    public IPLParser() {}

    public List<IPLItem> getItems() {
        return items;
    }

    public void load(File file) throws IOException {
        this.sourceFile = file;
        try (InputStream is = new FileInputStream(file)) {
            load(is);
        }
    }

    public void load(InputStream inputStream) throws IOException {
        items.clear();
        rawLines.clear();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        boolean inInstSection = false;
        int lineIndex = 0;

        while ((line = reader.readLine()) != null) {
            rawLines.add(line);
            String trimmed = line.trim();

            if (trimmed.equalsIgnoreCase("inst")) {
                inInstSection = true;
                lineIndex++;
                continue;
            }

            if (trimmed.equalsIgnoreCase("end")) {
                inInstSection = false;
                lineIndex++;
                continue;
            }

            if (inInstSection && !trimmed.startsWith("#") && !trimmed.isEmpty()) {
                String[] tokens = trimmed.split(",");
                if (tokens.length >= 11) {
                    try {
                        int id = Integer.parseInt(tokens[0].trim());
                        String modelName = tokens[1].trim();
                        int interior = Integer.parseInt(tokens[2].trim());
                        float px = Float.parseFloat(tokens[3].trim());
                        float py = Float.parseFloat(tokens[4].trim());
                        float pz = Float.parseFloat(tokens[5].trim());
                        float rx = Float.parseFloat(tokens[6].trim());
                        float ry = Float.parseFloat(tokens[7].trim());
                        float rz = Float.parseFloat(tokens[8].trim());
                        float rw = Float.parseFloat(tokens[9].trim());
                        int lod = Integer.parseInt(tokens[10].trim());

                        IPLItem item = new IPLItem(id, modelName, interior, px, py, pz, rx, ry, rz, rw, lod);
                        item.originalLineIndex = lineIndex;
                        items.add(item);
                    } catch (NumberFormatException ignored) {}
                }
            }
            lineIndex++;
        }
    }

    public void addItem(IPLItem item) {
        items.add(item);
    }

    public void removeItem(IPLItem item) {
        item.isDeleted = true;
    }

    public void save(File targetFile) throws IOException {
        try (OutputStream os = new FileOutputStream(targetFile)) {
            save(os);
        }
    }

    public void save(OutputStream outputStream) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

        // Write standard GTA SA header if new file or preserve existing
        if (rawLines.isEmpty()) {
            writer.write("# IPL generated by GTA SA Modern Map Editor\n");
            writer.write("inst\n");
            for (IPLItem item : items) {
                if (!item.isDeleted) {
                    writer.write(item.toIPLString());
                    writer.write("\n");
                }
            }
            writer.write("end\n");
        } else {
            // Merge changes into existing lines
            int currentItemIdx = 0;
            for (int i = 0; i < rawLines.size(); i++) {
                IPLItem currentItem = (currentItemIdx < items.size()) ? items.get(currentItemIdx) : null;
                if (currentItem != null && currentItem.originalLineIndex == i) {
                    if (currentItem.isDeleted) {
                        writer.write("# deleted\n");
                    } else if (currentItem.isModified) {
                        writer.write(currentItem.toIPLString());
                        writer.write("\n");
                    } else {
                        writer.write(rawLines.get(i));
                        writer.write("\n");
                    }
                    currentItemIdx++;
                } else {
                    writer.write(rawLines.get(i));
                    writer.write("\n");
                }
            }
            // Append any newly added items that were not in original file
            for (IPLItem item : items) {
                if (item.originalLineIndex == -1 && !item.isDeleted) {
                    writer.write(item.toIPLString());
                    writer.write("\n");
                }
            }
        }
        writer.flush();
    }
}
