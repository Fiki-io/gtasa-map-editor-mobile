package com.gtasa.mapeditor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gtasa.mapeditor.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Automates checking and downloading essential map editor assets (textures.tpk, mapa.png, data).
 */
public class AssetSetupDialog {

    private final Context context;
    private final File targetDir;
    private AlertDialog dialog;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnAction;

    public interface OnSetupCompleteListener {
        void onSetupComplete();
    }

    private final OnSetupCompleteListener listener;

    public AssetSetupDialog(Context context, File targetDir, OnSetupCompleteListener listener) {
        this.context = context;
        this.targetDir = targetDir;
        this.listener = listener;
    }

    public boolean isSetupRequired() {
        File tpk = new File(targetDir, "textures.tpk");
        File mapa = new File(targetDir, "mapa.png");
        File dat = new File(targetDir, "data/gta.dat");
        return !tpk.exists() || !mapa.exists() || !dat.exists();
    }

    public void show() {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_asset_setup, null);
        builder.setView(view);
        builder.setCancelable(false);

        progressBar = view.findViewById(R.id.pb_setup);
        tvStatus = view.findViewById(R.id.tv_setup_status);
        btnAction = view.findViewById(R.id.btn_setup_start);

        dialog = builder.create();
        dialog.show();

        btnAction.setOnClickListener(v -> {
            btnAction.setEnabled(false);
            startDownloadThread();
        });
    }

    private void startDownloadThread() {
        new Thread(() -> {
            try {
                // 1. Download mapa.png if missing
                File mapa = new File(targetDir, "mapa.png");
                if (!mapa.exists()) {
                    updateStatus("Downloading mapa.png...", 10);
                    downloadFile("https://drive.google.com/uc?id=1HuPR6e6fYWf2YtyzBJkAb-buofTuVNHs&export=download", mapa);
                }

                // 2. Download and extract data.mpk if missing
                File dat = new File(targetDir, "data/gta.dat");
                if (!dat.exists()) {
                    updateStatus("Downloading data.mpk...", 30);
                    File dataZip = new File(targetDir, "data.temp.zip");
                    downloadFile("https://drive.google.com/uc?id=1eUxyYt5c3M1WmBgwEXMr7GEAhioMBa9M&export=download", dataZip);

                    updateStatus("Extracting data/ folder...", 50);
                    extractZip(dataZip, targetDir);
                    dataZip.delete();
                }

                // 3. Download and extract textures.mpk if missing
                File tpk = new File(targetDir, "textures.tpk");
                if (!tpk.exists()) {
                    updateStatus("Downloading textures.mpk (80MB)...", 70);
                    File texZip = new File(targetDir, "textures.temp.zip");
                    downloadFile("https://media.githubusercontent.com/media/FSSRepo/zmstore/main/textures.mpk", texZip);

                    updateStatus("Extracting textures.tpk...", 90);
                    extractZip(texZip, targetDir);
                    texZip.delete();
                }

                updateStatus("Assets setup completed!", 100);
                tvStatus.postDelayed(() -> {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onSetupComplete();
                    }
                }, 1000);

            } catch (Exception e) {
                updateStatus("Download Error: " + e.getMessage(), 0);
                tvStatus.post(() -> {
                    btnAction.setEnabled(true);
                    btnAction.setText("Retry");
                    Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void updateStatus(String text, int progress) {
        tvStatus.post(() -> {
            tvStatus.setText(text);
            progressBar.setProgress(progress);
        });
    }

    private void downloadFile(String urlStr, File destination) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String newUrl = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void extractZip(File zipFile, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
