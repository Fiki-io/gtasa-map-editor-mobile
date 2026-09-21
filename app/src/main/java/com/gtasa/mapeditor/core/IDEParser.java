package com.gtasa.mapeditor.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clean-room implementation of GTA SA Item Definition (.ide) parser and indexer.
 */
public class IDEParser {

    public static class IDEItem {
        public int id;
        public String modelName;
        public String textureDictName;
        public float drawDistance;
        public int flags;

        public IDEItem(int id, String modelName, String textureDictName, float drawDistance, int flags) {
            this.id = id;
            this.modelName = modelName;
            this.textureDictName = textureDictName;
            this.drawDistance = drawDistance;
            this.flags = flags;
        }
    }

    private final Map<Integer, IDEItem> idMap = new HashMap<>();
    private final Map<String, IDEItem> nameMap = new HashMap<>();
    private final List<IDEItem> allItems = new ArrayList<>();

    public IDEParser() {}

    public void load(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            load(is);
        }
    }

    public void load(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        boolean inObjsSection = false;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();

            if (trimmed.equalsIgnoreCase("objs")) {
                inObjsSection = true;
                continue;
            }

            if (trimmed.equalsIgnoreCase("end")) {
                inObjsSection = false;
                continue;
            }

            if (inObjsSection && !trimmed.startsWith("#") && !trimmed.isEmpty()) {
                String[] tokens = trimmed.split(",");
                if (tokens.length >= 3) {
                    try {
                        int id = Integer.parseInt(tokens[0].trim());
                        String modelName = tokens[1].trim();
                        String txdName = tokens[2].trim();
                        float drawDist = tokens.length > 3 ? Float.parseFloat(tokens[3].trim()) : 100.0f;
                        int flags = tokens.length > 4 ? Integer.parseInt(tokens[4].trim()) : 0;

                        IDEItem item = new IDEItem(id, modelName, txdName, drawDist, flags);
                        idMap.put(id, item);
                        nameMap.put(modelName.toLowerCase(), item);
                        allItems.add(item);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    public IDEItem getById(int id) {
        return idMap.get(id);
    }

    public IDEItem getByModelName(String modelName) {
        return nameMap.get(modelName.toLowerCase());
    }

    public List<IDEItem> search(String query) {
        List<IDEItem> result = new ArrayList<>();
        String q = query.toLowerCase();
        for (IDEItem item : allItems) {
            if (item.modelName.toLowerCase().contains(q) || String.valueOf(item.id).contains(q)) {
                result.add(item);
            }
        }
        return result;
    }

    public List<IDEItem> getAllItems() {
        return allItems;
    }

    public void clear() {
        idMap.clear();
        nameMap.clear();
        allItems.clear();
    }
}
