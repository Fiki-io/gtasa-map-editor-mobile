package com.gtasa.mapeditor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.gtasa.mapeditor.R;
import com.gtasa.mapeditor.core.IPLParser;

import java.util.Locale;

/**
 * Dialog to inspect and manually adjust numeric object coordinates and rotations.
 */
public class ObjectInspectorDialog {

    public interface OnApplyListener {
        void onApply(IPLParser.IPLItem item);
    }

    public static void show(Context context, IPLParser.IPLItem item, OnApplyListener listener) {
        if (item == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_object_inspector, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView tvModel = view.findViewById(R.id.tv_inspector_model);
        EditText etX = view.findViewById(R.id.et_pos_x);
        EditText etY = view.findViewById(R.id.et_pos_y);
        EditText etZ = view.findViewById(R.id.et_pos_z);
        EditText etRotZ = view.findViewById(R.id.et_rot_z);
        Button btnApply = view.findViewById(R.id.btn_inspector_apply);

        tvModel.setText(String.format(Locale.US, "Model: %s (ID: %d)", item.modelName, item.id));
        etX.setText(String.format(Locale.US, "%.3f", item.posX));
        etY.setText(String.format(Locale.US, "%.3f", item.posY));
        etZ.setText(String.format(Locale.US, "%.3f", item.posZ));
        etRotZ.setText(String.format(Locale.US, "%.4f", item.rotZ));

        btnApply.setOnClickListener(v -> {
            try {
                item.posX = Float.parseFloat(etX.getText().toString().trim());
                item.posY = Float.parseFloat(etY.getText().toString().trim());
                item.posZ = Float.parseFloat(etZ.getText().toString().trim());
                item.rotZ = Float.parseFloat(etRotZ.getText().toString().trim());
                item.isModified = true;

                dialog.dismiss();
                if (listener != null) {
                    listener.onApply(item);
                }
                Toast.makeText(context, "Coordinates updated!", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid numbers entered", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
