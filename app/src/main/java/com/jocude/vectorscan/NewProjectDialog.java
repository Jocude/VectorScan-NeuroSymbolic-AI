package com.jocude.vectorscan;

import android.app.Activity;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;

/** Pide el nombre del proyecto y, opcionalmente, el ancho real del edificio. */
public final class NewProjectDialog {

    public interface Listener {
        /** @param widthM ancho real en metros, o null si no se ha indicado */
        void onAccepted(String name, Double widthM);
    }

    private NewProjectDialog() {
    }

    public static void show(Activity activity, File targetDir, Listener listener) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_new_project, null);
        TextInputLayout tilName = view.findViewById(R.id.tilProjectName);
        TextInputLayout tilWidth = view.findViewById(R.id.tilWidth);
        TextInputEditText etName = view.findViewById(R.id.etProjectName);
        TextInputEditText etWidth = view.findViewById(R.id.etWidth);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.create_project)
                .setView(view)
                .setPositiveButton(R.string.next, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        // Se valida sin cerrar el diálogo si hay errores.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String widthText = etWidth.getText() != null ? etWidth.getText().toString() : "";
            String nameError = validateName(name, targetDir);
            tilName.setError(nameError != null ? activity.getString(R.string.error_prefix, nameError) : null);
            Double width = parseWidth(widthText);
            boolean widthOk = widthText.trim().isEmpty() || width != null;
            tilWidth.setError(widthOk ? null : activity.getString(R.string.project_width_error));
            if (nameError == null && widthOk) {
                dialog.dismiss();
                listener.onAccepted(name, width);
            }
        }));
        dialog.show();
    }

    /** @return el motivo por el que el nombre no vale, o null si es válido. */
    static String validateName(String name, File targetDir) {
        if (name.isEmpty()) return "el nombre es obligatorio";
        if (!name.equals(ProjectEditorActivity.safeName(name))) return "no puede contener / \\ : * ? \" < > |";
        if (targetDir != null && (new File(targetDir, name + ".jpg").exists()
                || new File(targetDir, name + ".glb").exists())) {
            return "ya existe un proyecto con ese nombre";
        }
        return null;
    }

    /** Acepta coma o punto decimal. Devuelve null si está vacío o fuera de rango (1–500 m). */
    static Double parseWidth(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return value >= 1 && value <= 500 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
