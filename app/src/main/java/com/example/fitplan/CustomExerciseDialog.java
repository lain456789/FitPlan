package com.example.fitplan;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class CustomExerciseDialog extends DialogFragment {
    private EditText name;
    private Spinner part;
    private Spinner muscle;
    private final ArrayList<String> muscleKeys = new ArrayList<>();
    private final CheckBox[] standard = new CheckBox[Catalog.EQUIPMENT.length];
    private CheckBox none, other;
    private EditText otherName;
    private LinearLayout choices;
    private ImageView preview;
    private Button remove;
    private Uri selected;
    private String exerciseId;
    private SaveState saveState;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(
                    new PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            selected = uri;
                            renderPreview();
                        }
                    });

    public static final class SaveState extends ViewModel {
        final MutableLiveData<Boolean> result = new MutableLiveData<>();
        boolean saving;

        void save(Context app, JSONObject row, Uri uri) {
            saving = true;
            new Thread(
                            () -> {
                                CustomMedia files = new CustomMedia(app, row.optString("id"));
                                try (Store store = new Store(app)) {
                                    if (uri != null) files.importFrom(app, uri);
                                    store.save("exercise", row);
                                    result.postValue(true);
                                } catch (Exception error) {
                                    files.discard();
                                    result.postValue(false);
                                }
                            },
                            "save-exercise")
                    .start();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        MainActivity a = (MainActivity) requireActivity();
        saveState = new ViewModelProvider(this).get(SaveState.class);
        exerciseId = state == null ? Store.id() : state.getString("id");
        selected = state == null ? null : state.getParcelable("uri");
        LinearLayout form = a.column();
        TrainingScreen screen = new TrainingScreen(a);
        name =
                screen.input(
                        form,
                        a.s("exercise_name"),
                        state == null ? "" : state.getString("name"),
                        false);
        part =
                screen.select(
                        form,
                        a.s("part"),
                        a.partLabels(),
                        state == null ? 0 : state.getInt("part"));
        muscle = screen.select(form, a.s("specific_muscle"), new String[] {a.s("part_only")}, 0);
        String initial = state == null ? "" : state.getString("muscle", "");
        updateMuscles(a, initial);
        part.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    int last = part.getSelectedItemPosition();

                    public void onNothingSelected(AdapterView<?> parent) {}

                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        if (pos != last) {
                            last = pos;
                            updateMuscles(a, "");
                        }
                    }
                });
        addEquipment(a, form, state);
        a.button(
                form,
                a.s("choose_media"),
                () ->
                        picker.launch(
                                new PickVisualMediaRequest.Builder()
                                        .setMediaType(PickVisualMedia.ImageOnly.INSTANCE)
                                        .build()));
        preview = new ImageView(a);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        form.addView(preview, new LinearLayout.LayoutParams(-1, a.dp(120)));
        remove =
                a.button(
                        form,
                        a.s("clear_media"),
                        () -> {
                            selected = null;
                            renderPreview();
                        });
        renderPreview();
        ScrollView scroll = new ScrollView(a);
        scroll.addView(form);
        return new AlertDialog.Builder(a)
                .setTitle(a.s("custom"))
                .setView(scroll)
                .setPositiveButton(a.s("save"), null)
                .setNegativeButton(a.s("cancel"), null)
                .create();
    }

    private void updateMuscles(MainActivity a, String selectedKey) {
        muscleKeys.clear();
        ArrayList<String> labels = new ArrayList<>();
        muscleKeys.add("");
        labels.add(a.s("part_only"));
        String region = Catalog.PARTS[part.getSelectedItemPosition()];
        for (MuscleCatalog group : MuscleCatalog.values()) {
            if (!region.equals(group.part)) continue;
            muscleKeys.add(group.key);
            labels.add(a.s("muscle_" + group.key));
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        muscle.setAdapter(adapter);
        muscle.setSelection(Math.max(0, muscleKeys.indexOf(selectedKey)));
    }

    private CheckBox check(MainActivity a, String label, boolean checked) {
        CheckBox box = new CheckBox(a);
        box.setText(label);
        box.setChecked(checked);
        choices.addView(box);
        return box;
    }

    private void addEquipment(MainActivity a, LinearLayout form, Bundle state) {
        Button toggle = a.button(form, a.s("choose_equipment"), () -> {});
        choices = new LinearLayout(a);
        choices.setOrientation(LinearLayout.VERTICAL);
        form.addView(choices);
        none = check(a, a.s("equipment_none"), state != null && state.getBoolean("none"));
        for (int i = 0; i < standard.length; i++) {
            String code = Catalog.EQUIPMENT[i];
            standard[i] = check(a, a.s("eq_" + code), state != null && state.getBoolean(code));
            standard[i].setOnCheckedChangeListener((box, on) -> {
                if (on) none.setChecked(false);
            });
        }
        other = check(a, a.s("other_equipment"), state != null && state.getBoolean("other"));
        otherName = new EditText(a);
        otherName.setSingleLine(true);
        otherName.setHint(a.s("other_equipment_name"));
        if (state != null) otherName.setText(state.getString("otherName"));
        choices.addView(otherName);
        otherName.setVisibility(other.isChecked() ? View.VISIBLE : View.GONE);
        other.setOnCheckedChangeListener((box, on) -> {
            if (on) none.setChecked(false);
            otherName.setVisibility(on ? View.VISIBLE : View.GONE);
        });
        none.setOnCheckedChangeListener((box, on) -> {
            if (on) {
                for (CheckBox item : standard) item.setChecked(false);
                other.setChecked(false);
            }
        });
        Runnable update =
                () -> {
                    JSONObject draft = Store.object();
                    writeEquipment(draft);
                    toggle.setText(
                            draft.has("equipment")
                                    ? a.s(
                                            "chosen_equipment",
                                            new TrainingScreen(a).customEquipmentText(draft))
                                    : a.s("choose_equipment"));
                };
        choices.setVisibility(
                state != null && state.getBoolean("expanded") ? View.VISIBLE : View.GONE);
        update.run();
        toggle.setOnClickListener(v -> {
            choices.setVisibility(
                    choices.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            update.run();
        });
    }

    private void writeEquipment(JSONObject row) {
        JSONArray codes = new JSONArray();
        for (int i = 0; i < standard.length; i++)
            if (standard[i].isChecked()) codes.put(Catalog.EQUIPMENT[i]);
        String label = other.isChecked() ? otherName.getText().toString().trim() : "";
        if (!label.isEmpty()) Store.put(row, "otherEquipment", label);
        if (none.isChecked() || codes.length() != 0 || !label.isEmpty())
            Store.put(row, "equipment", codes);
    }

    private void renderPreview() {
        if (preview == null) return;
        Glide.with(this).clear(preview);
        int visibility = selected == null ? View.GONE : View.VISIBLE;
        preview.setVisibility(visibility);
        remove.setVisibility(visibility);
        if (selected != null)
            Glide.with(this)
                    .asBitmap()
                    .load(selected)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(preview);
    }

    @Override
    public void onStart() {
        super.onStart();
        MainActivity a = (MainActivity) requireActivity();
        AlertDialog dialog = (AlertDialog) requireDialog();
        Button submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable lock =
                () -> {
                    submit.setEnabled(!saveState.saving);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!saveState.saving);
                    setCancelable(!saveState.saving);
                };
        lock.run();
        submit.setOnClickListener(v -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty()) {
                name.setError(a.s("name_required"));
                return;
            }
            if (other.isChecked() && otherName.getText().toString().trim().isEmpty()) {
                choices.setVisibility(View.VISIBLE);
                otherName.setError(a.s("other_equipment_name"));
                otherName.requestFocus();
                return;
            }
            JSONObject row =
                    Store.object(
                            "id",
                            exerciseId,
                            "name",
                            label,
                            "part",
                            Catalog.PARTS[part.getSelectedItemPosition()]);
            Store.put(row, "muscle", muscleKeys.get(muscle.getSelectedItemPosition()));
            writeEquipment(row);
            saveState.save(a.getApplicationContext(), row, selected);
            lock.run();
        });
        saveState.result.removeObservers(this);
        saveState.result.observe(
                this,
                ok -> {
                    if (ok == null) return;
                    saveState.saving = false;
                    if (ok) {
                        dismiss();
                        a.show("library");
                    } else {
                        lock.run();
                        Toast.makeText(a, a.s("media_failed"), Toast.LENGTH_LONG).show();
                        saveState.result.setValue(null);
                    }
                });
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("id", exerciseId);
        state.putString("name", name.getText().toString());
        state.putInt("part", part.getSelectedItemPosition());
        state.putString("muscle", muscleKeys.get(muscle.getSelectedItemPosition()));
        state.putBoolean("expanded", choices.getVisibility() == View.VISIBLE);
        state.putBoolean("none", none.isChecked());
        for (int i = 0; i < standard.length; i++)
            state.putBoolean(Catalog.EQUIPMENT[i], standard[i].isChecked());
        state.putBoolean("other", other.isChecked());
        state.putString("otherName", otherName.getText().toString());
        state.putParcelable("uri", selected);
    }
}
