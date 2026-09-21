package com.gtasa.mapeditor.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gtasa.mapeditor.R;
import com.gtasa.mapeditor.core.DATParser;
import com.gtasa.mapeditor.core.IDEParser;
import com.gtasa.mapeditor.core.IMGArchive;
import com.gtasa.mapeditor.core.IPLParser;
import com.gtasa.mapeditor.core.TexturePackReader;
import com.gtasa.mapeditor.render.MapGLSurfaceView;
import com.gtasa.mapeditor.render.MapRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Main Activity for GTA SA Map Editor Mobile.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IPL = 101;
    private static final int REQUEST_PICK_IMG_GTA3 = 102;
    private static final int REQUEST_PICK_IMG_INT = 103;

    private MapGLSurfaceView glSurfaceView;
    private MapRenderer renderer;
    private RadarOverlayView radarOverlay;
    private TextView tvSelectionInfo;

    private File appStorageDir;
    private File gameDataDir;

    private DATParser datParser = new DATParser();
    private IDEParser ideParser = new IDEParser();
    private IPLParser iplParser = new IPLParser();
    private IMGArchive imgArchive;
    private TexturePackReader texturePackReader;

    private File currentIPLFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive sticky fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.gl_surface_view);
        renderer = glSurfaceView.getMapRenderer();
        radarOverlay = findViewById(R.id.radar_overlay);
        tvSelectionInfo = findViewById(R.id.tv_selection_info);

        radarOverlay.setCamera(renderer.camera);
        radarOverlay.setOnTeleportListener((wx, wy) -> {
            renderer.camera.teleportTo(wx, wy, renderer.camera.targetZ);
            Toast.makeText(this, String.format(Locale.US, "Teleported to: (%.1f, %.1f)", wx, wy), Toast.LENGTH_SHORT).show();
        });

        renderer.setSelectionListener(item -> runOnUiThread(() -> {
            radarOverlay.setSelectedItem(item);
            if (item != null) {
                tvSelectionInfo.setText(String.format(Locale.US, "Selected: %s (ID: %d) | Pos: %.1f, %.1f, %.1f",
                        item.modelName, item.id, item.posX, item.posY, item.posZ));
            } else {
                tvSelectionInfo.setText(R.string.no_object_selected);
            }
        }));

        setupStoragePaths();
        setupToolbarButtons();

        checkAssetsAndInitialize();
    }

    private void setupStoragePaths() {
        // Preferred: /sdcard/mape/ or internal storage
        File sdcardMape = new File(Environment.getExternalStorageDirectory(), "mape");
        if (sdcardMape.exists() || sdcardMape.mkdirs()) {
            appStorageDir = sdcardMape;
        } else {
            appStorageDir = getExternalFilesDir(null);
        }

        // GTA SA default folder
        File gtasaFiles = new File(Environment.getExternalStorageDirectory(), "Android/data/com.rockstargames.gtasa/files");
        if (gtasaFiles.exists()) {
            gameDataDir = gtasaFiles;
        } else {
            gameDataDir = appStorageDir;
        }
    }

    private void checkAssetsAndInitialize() {
        AssetSetupDialog setupDialog = new AssetSetupDialog(this, appStorageDir, this::loadAssetsAndInitialize);
        if (setupDialog.isSetupRequired()) {
            setupDialog.show();
        } else {
            loadAssetsAndInitialize();
        }
    }

    private void loadAssetsAndInitialize() {
        new Thread(() -> {
            try {
                // 1. Setup radar map
                File mapaPng = new File(appStorageDir, "mapa.png");
                runOnUiThread(() -> radarOverlay.setMapImage(mapaPng));

                // 2. Load gta.dat
                File gtaDat = new File(appStorageDir, "data/gta.dat");
                if (!gtaDat.exists() && gameDataDir != null) {
                    gtaDat = new File(gameDataDir, "data/gta.dat");
                }
                if (gtaDat.exists()) {
                    datParser.load(gtaDat);

                    // Index all IDE files
                    File baseDataDir = gtaDat.getParentFile().getParentFile();
                    for (DATParser.Entry entry : datParser.getIdeEntries()) {
                        File ideFile = new File(baseDataDir, entry.relativePath);
                        if (ideFile.exists()) {
                            ideParser.load(ideFile);
                        }
                    }
                }

                // 3. Open TexturePackReader (textures.tpk)
                File tpkFile = new File(appStorageDir, "textures.tpk");
                texturePackReader = new TexturePackReader(tpkFile, new File(appStorageDir, "textures"));
                texturePackReader.open();

                // 4. Check for gta3.img and gta_int.img
                imgArchive = new IMGArchive();

                File gta3Img = new File(gameDataDir, "texdb/gta3.img");
                if (!gta3Img.exists()) gta3Img = new File(appStorageDir, "gta3.img");
                if (gta3Img.exists()) imgArchive.addArchive(gta3Img);

                File gtaIntImg = new File(gameDataDir, "texdb/gta_int.img");
                if (!gtaIntImg.exists()) gtaIntImg = new File(appStorageDir, "gta_int.img");
                if (gtaIntImg.exists()) imgArchive.addArchive(gtaIntImg);

                // 5. Load default map (LAe.ipl)
                File defaultIpl = new File(appStorageDir, "data/maps/LA/LAe.ipl");
                if (!defaultIpl.exists() && gameDataDir != null) {
                    defaultIpl = new File(gameDataDir, "data/maps/LA/LAe.ipl");
                }

                if (defaultIpl.exists()) {
                    loadIPL(defaultIpl);
                }

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Init Warning: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadIPL(File iplFile) {
        try {
            this.currentIPLFile = iplFile;
            iplParser.load(iplFile);
            renderer.setDataSources(iplParser, ideParser, imgArchive, texturePackReader);

            // Move camera to first object
            if (!iplParser.getItems().isEmpty()) {
                IPLParser.IPLItem first = iplParser.getItems().get(0);
                renderer.camera.teleportTo(first.posX, first.posY, first.posZ);
            }

            runOnUiThread(() -> Toast.makeText(this, "Loaded: " + iplFile.getName() + " (" + iplParser.getItems().size() + " objects)", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error loading IPL: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void setupToolbarButtons() {
        Button btnSelectMap = findViewById(R.id.btn_map_select);
        Button btnImportImg = findViewById(R.id.btn_import_img);
        Button btnAddObject = findViewById(R.id.btn_add_object);
        Button btnDuplicate = findViewById(R.id.btn_duplicate);
        Button btnDelete = findViewById(R.id.btn_delete);
        Button btnInspector = findViewById(R.id.btn_inspector);
        Button btnToggleRadar = findViewById(R.id.btn_toggle_radar);
        Button btnSave = findViewById(R.id.btn_save);

        btnImportImg.setOnClickListener(v -> showImportImgDialog());

        btnSelectMap.setOnClickListener(v -> {
            MapSelectorDialog.show(this, datParser.getIplEntries(), new File(appStorageDir, "data/maps"), (entry, customFile) -> {
                if (entry != null) {
                    File target = new File(appStorageDir, entry.relativePath);
                    if (target.exists()) {
                        loadIPL(target);
                    } else {
                        Toast.makeText(this, "File not found: " + entry.relativePath, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Open custom file picker
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQUEST_PICK_IPL);
                }
            });
        });

        btnAddObject.setOnClickListener(v -> showAddObjectDialog());

        btnDuplicate.setOnClickListener(v -> {
            IPLParser.IPLItem selected = renderer.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "Select an object first to duplicate", Toast.LENGTH_SHORT).show();
                return;
            }
            IPLParser.IPLItem copy = selected.copy();
            copy.posX += 2.0f; // offset slightly
            copy.posY += 2.0f;
            iplParser.addItem(copy);
            renderer.setSelectedItem(copy);
            Toast.makeText(this, "Object duplicated!", Toast.LENGTH_SHORT).show();
        });

        btnDelete.setOnClickListener(v -> {
            IPLParser.IPLItem selected = renderer.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "Select an object first to delete", Toast.LENGTH_SHORT).show();
                return;
            }
            iplParser.removeItem(selected);
            renderer.setSelectedItem(null);
            tvSelectionInfo.setText(R.string.no_object_selected);
            Toast.makeText(this, "Object removed", Toast.LENGTH_SHORT).show();
        });

        btnInspector.setOnClickListener(v -> {
            IPLParser.IPLItem selected = renderer.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "Select an object first", Toast.LENGTH_SHORT).show();
                return;
            }
            ObjectInspectorDialog.show(this, selected, item -> {
                renderer.camera.updateMatrices();
            });
        });

        btnToggleRadar.setOnClickListener(v -> {
            if (radarOverlay.getVisibility() == View.VISIBLE) {
                radarOverlay.setVisibility(View.GONE);
            } else {
                radarOverlay.setVisibility(View.VISIBLE);
            }
        });

        btnSave.setOnClickListener(v -> {
            if (currentIPLFile == null) {
                Toast.makeText(this, "No IPL file currently open to save", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                iplParser.save(currentIPLFile);
                Toast.makeText(this, "Saved: " + currentIPLFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showAddObjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Object to Map");

        final EditText input = new EditText(this);
        input.setHint("Model name (e.g. veg_tree3) or ID");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String query = input.getText().toString().trim();
            if (query.isEmpty()) return;

            int id = 1280;
            String model = query;

            try {
                id = Integer.parseInt(query);
                IDEParser.IDEItem ide = ideParser.getById(id);
                if (ide != null) model = ide.modelName;
            } catch (NumberFormatException e) {
                IDEParser.IDEItem ide = ideParser.getByModelName(query);
                if (ide != null) id = ide.id;
            }

            IPLParser.IPLItem newItem = new IPLParser.IPLItem(
                    id, model, 0,
                    renderer.camera.targetX, renderer.camera.targetY, renderer.camera.targetZ,
                    0, 0, 0, 1, -1
            );
            newItem.isModified = true;

            iplParser.addItem(newItem);
            renderer.setSelectedItem(newItem);
            Toast.makeText(this, "Object placed at camera focus point!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showImportImgDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Import GTA 3D Models (.img)");
        String[] options = {
                "Select gta3.img (Exterior models)",
                "Select gta_int.img (Interior models)",
                "Auto-detect from GTA SA folder"
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_PICK_IMG_GTA3);
            } else if (which == 1) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_PICK_IMG_INT);
            } else if (which == 2) {
                autoDetectAndLoadImg();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void autoDetectAndLoadImg() {
        int countBefore = (imgArchive != null) ? imgArchive.getEntryCount() : 0;
        if (imgArchive == null) imgArchive = new IMGArchive();

        File gta3 = new File(gameDataDir, "texdb/gta3.img");
        if (gta3.exists()) {
            try { imgArchive.addArchive(gta3); } catch (Exception ignored) {}
        }
        File gtaInt = new File(gameDataDir, "texdb/gta_int.img");
        if (gtaInt.exists()) {
            try { imgArchive.addArchive(gtaInt); } catch (Exception ignored) {}
        }

        renderer.setDataSources(iplParser, ideParser, imgArchive, texturePackReader);
        int added = imgArchive.getEntryCount() - countBefore;
        if (added > 0) {
            Toast.makeText(this, "Found " + added + " models from GTA SA folder!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No .img files found in game folder. Please use 'Select gta3.img' option.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_PICK_IPL) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    iplParser.load(is);
                    renderer.setDataSources(iplParser, ideParser, imgArchive, texturePackReader);
                    Toast.makeText(this, "Loaded custom IPL from storage!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to load custom IPL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == REQUEST_PICK_IMG_GTA3 || requestCode == REQUEST_PICK_IMG_INT) {
                boolean isInt = (requestCode == REQUEST_PICK_IMG_INT);
                String fileName = isInt ? "gta_int.img" : "gta3.img";
                File destFile = new File(appStorageDir, fileName);

                Toast.makeText(this, "Importing " + fileName + "... Please wait.", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        InputStream in = getContentResolver().openInputStream(uri);
                        FileOutputStream out = new FileOutputStream(destFile);
                        byte[] buf = new byte[16384];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        in.close();
                        out.close();

                        if (imgArchive == null) {
                            imgArchive = new IMGArchive();
                        }
                        imgArchive.addArchive(destFile);
                        renderer.setDataSources(iplParser, ideParser, imgArchive, texturePackReader);

                        runOnUiThread(() -> Toast.makeText(this, fileName + " imported successfully! Models ready: " + imgArchive.getEntryCount(), Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Failed to import " + fileName + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (texturePackReader != null) texturePackReader.close();
        if (imgArchive != null) imgArchive.close();
    }
}
