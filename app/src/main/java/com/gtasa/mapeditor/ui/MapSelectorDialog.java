package com.gtasa.mapeditor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.gtasa.mapeditor.R;
import com.gtasa.mapeditor.core.DATParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to select an active IPL map from gta.dat or open a custom IPL file.
 */
public class MapSelectorDialog {

    public interface OnMapSelectedListener {
        void onMapSelected(DATParser.Entry entry, File customFile);
    }

    public static void show(Context context, List<DATParser.Entry> entries, File dataMapsDir, OnMapSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_map_selector, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        EditText etSearch = view.findViewById(R.id.et_search_map);
        ListView lvMaps = view.findViewById(R.id.lv_maps);
        Button btnCustom = view.findViewById(R.id.btn_open_custom_ipl);

        List<DATParser.Entry> filteredList = new ArrayList<>(entries);
        ArrayAdapter<DATParser.Entry> adapter = new ArrayAdapter<DATParser.Entry>(context, android.R.layout.simple_list_item_2, android.R.id.text1, filteredList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView text1 = row.findViewById(android.R.id.text1);
                TextView text2 = row.findViewById(android.R.id.text2);

                DATParser.Entry item = getItem(position);
                if (item != null) {
                    text1.setText(item.fileName);
                    text1.setTextColor(context.getResources().getColor(R.color.text_primary));
                    text2.setText(item.relativePath);
                    text2.setTextColor(context.getResources().getColor(R.color.text_secondary));
                }
                return row;
            }
        };
        lvMaps.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filteredList.clear();
                String q = s.toString().toLowerCase();
                for (DATParser.Entry e : entries) {
                    if (e.fileName.toLowerCase().contains(q) || e.relativePath.toLowerCase().contains(q)) {
                        filteredList.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        lvMaps.setOnItemClickListener((parent, v, position, id) -> {
            DATParser.Entry selected = filteredList.get(position);
            dialog.dismiss();
            if (listener != null) {
                listener.onMapSelected(selected, null);
            }
        });

        btnCustom.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onMapSelected(null, null); // triggers SAF custom file picker
            }
        });

        dialog.show();
    }
}
