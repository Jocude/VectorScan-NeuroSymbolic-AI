package com.jocude.vectorscan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.ar.core.ArCoreApk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.SceneView;
import io.github.sceneview.node.ModelNode;
import kotlin.Unit;

/**
 * Editor de un proyecto: muestra el plano (2D) y su modelo 3D. Si el proyecto es nuevo, envía
 * la imagen al servidor de VectorScan para generar el modelo.
 */
public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "imageUri";
    public static final String EXTRA_PROJECT_NAME = "projectName";
    public static final String EXTRA_IS_NEW = "isNewProject";
    public static final String EXTRA_SAVE_FOLDER = "saveFolder";
    /** Ancho real del edificio en metros (double). Opcional. */
    public static final String EXTRA_WIDTH_M = "anchoM";

    private String imageUriString;
    private String projectName;
    private boolean isNewProject;
    private boolean modelOnly; // proyecto importado: solo hay GLB, sin imagen
    private String saveFolderPath;
    private Double widthM;

    private File modelFile;
    private String loadedModelPath;
    private boolean generationFailed = false;

    private Toolbar toolbar;
    private SceneView sceneView;
    private ImageView ivProjectImage;
    private ModelNode modelNode;
    private LinearLayout llLoading;
    private TextView tvLoadingStatus;
    private MaterialButton btnSave;
    private MaterialButton btnRetry;
    private ExtendedFloatingActionButton fabAR;
    private MaterialButtonToggleGroup toggleGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_editor);

        toolbar = findViewById(R.id.toolbarEditor);
        sceneView = findViewById(R.id.sceneView);
        ivProjectImage = findViewById(R.id.ivProjectImage);
        llLoading = findViewById(R.id.llLoading);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);
        fabAR = findViewById(R.id.fabAR);
        toggleGroup = findViewById(R.id.toggleGroup);
        btnSave = findViewById(R.id.btnSave);
        btnRetry = findViewById(R.id.btnRetry);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);

        Intent intent = getIntent();
        imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI);
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME);
        isNewProject = intent.getBooleanExtra(EXTRA_IS_NEW, false);
        saveFolderPath = intent.getStringExtra(EXTRA_SAVE_FOLDER);
        widthM = intent.hasExtra(EXTRA_WIDTH_M) ? intent.getDoubleExtra(EXTRA_WIDTH_M, 0) : null;
        modelOnly = imageUriString != null && imageUriString.toLowerCase().endsWith(".glb");

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(projectName);
        }
        toolbar.setNavigationOnClickListener(v -> handleCancel());

        // Imprescindible para que SceneView siga el ciclo de vida de la actividad.
        sceneView.setLifecycle(getLifecycle());

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn2D) show2D();
            else if (checkedId == R.id.btn3D) show3D();
        });
        fabAR.setOnClickListener(v -> openAR());
        btnCancel.setOnClickListener(v -> handleCancel());
        btnSave.setOnClickListener(v -> saveProject());
        btnRetry.setOnClickListener(v -> generateModel());

        if (imageUriString == null) {
            Toast.makeText(this, "No se ha recibido ninguna imagen", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (modelOnly) {
            modelFile = new File(Uri.parse(imageUriString).getPath());
            btnSave.setEnabled(true);
        } else {
            ivProjectImage.setImageURI(Uri.parse(imageUriString));
            if (isNewProject) {
                btnSave.setEnabled(false);
                generateModel();
            } else {
                btnSave.setEnabled(true);
                modelFile = findSavedModel();
                if (modelFile == null) {
                    // Proyecto guardado sin modelo (el servidor falló): se puede generar ahora.
                    btnRetry.setText(R.string.generate_model);
                    btnRetry.setVisibility(View.VISIBLE);
                }
            }
        }
        updateModeControls();
    }

    // ===== Generación del modelo =====

    private void generateModel() {
        generationFailed = false;
        btnRetry.setVisibility(View.GONE);
        llLoading.setVisibility(View.VISIBLE);
        tvLoadingStatus.setText(R.string.generating_model);

        File upload = new File(getCacheDir(), "plano_subida.jpg");
        try {
            copy(getContentResolver().openInputStream(Uri.parse(imageUriString)), upload);
        } catch (IOException e) {
            onGenerationError("No se puede leer la imagen");
            return;
        }

        File destino = new File(getCacheDir(), "modelo_" + System.currentTimeMillis() + ".glb");
        VectorScanClient client = new VectorScanClient(ServerConfig.getUrl(this));
        client.generateModelAsync(upload, widthM, destino, new VectorScanClient.Callback<ModelSummary>() {
            @Override
            public void onSuccess(ModelSummary summary) {
                if (isFinishing() || isDestroyed()) {
                    destino.delete();
                    return;
                }
                deleteTemporaryModel();
                modelFile = destino;
                llLoading.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                if (summary != null && getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(summary.describe());
                }
                updateModeControls();
                toggleGroup.check(R.id.btn3D);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                onGenerationError(message);
            }
        });
    }

    private void onGenerationError(String message) {
        generationFailed = true;
        llLoading.setVisibility(View.GONE);
        btnRetry.setVisibility(View.VISIBLE);
        btnSave.setEnabled(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.generation_failed)
                .setMessage(message + "\n\n" + getString(R.string.generation_failed_hint))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.settings, (d, w) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    // ===== Vistas 2D / 3D =====

    private boolean hasModel() {
        return modelFile != null && modelFile.exists();
    }

    /** Muestra el selector 2D/3D solo si hay algo que elegir. */
    private void updateModeControls() {
        boolean both = hasModel() && !modelOnly;
        toggleGroup.setVisibility(both ? View.VISIBLE : View.GONE);
        if (modelOnly && hasModel()) {
            show3D();
        } else if (toggleGroup.getCheckedButtonId() == View.NO_ID) {
            toggleGroup.check(R.id.btn2D);
        }
        invalidateOptionsMenu();
    }

    private void show2D() {
        ivProjectImage.setVisibility(View.VISIBLE);
        sceneView.setVisibility(View.GONE);
        fabAR.setVisibility(View.GONE);
    }

    private void show3D() {
        if (!hasModel()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            toggleGroup.check(R.id.btn2D);
            return;
        }
        ivProjectImage.setVisibility(View.GONE);
        sceneView.setVisibility(View.VISIBLE);
        fabAR.setVisibility(View.VISIBLE);
        loadModel();
    }

    private void loadModel() {
        if (modelFile.getAbsolutePath().equals(loadedModelPath)) return;
        if (modelNode != null) {
            sceneView.removeChildNode(modelNode);
            modelNode.destroy();
            modelNode = null;
        }
        loadedModelPath = modelFile.getAbsolutePath();
        sceneView.getModelLoader().loadModelInstanceAsync(
                Uri.fromFile(modelFile).toString(),
                resource -> resource,
                modelInstance -> {
                    if (modelInstance == null) {
                        loadedModelPath = null;
                        Toast.makeText(this, R.string.model_load_error, Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    // Normaliza el tamaño (eje mayor = 2,5 unidades), centra el modelo y lo pone
                    // en el punto alrededor del que orbita la cámara de SceneView (0, 0, -4).
                    // Inclinado para que se vea la planta desde arriba al abrirlo.
                    modelNode = new ModelNode(modelInstance, null, true, 2.5f, new Float3(0f, 0f, 0f));
                    modelNode.setPosition(new Float3(0f, 0f, -4f));
                    modelNode.setRotation(new Float3(35f, -30f, 0f));
                    sceneView.addChildNode(modelNode);
                    return Unit.INSTANCE;
                });
    }

    // ===== Realidad aumentada y compartir =====

    private void openAR() {
        if (!hasModel()) return;
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isUnsupported()) {
            Toast.makeText(this, R.string.ar_not_supported, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, ArViewerActivity.class);
        intent.putExtra(ArViewerActivity.EXTRA_MODEL_PATH, modelFile.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_share).setVisible(hasModel());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareModel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareModel() {
        if (!hasModel()) return;
        try {
            // Copia con el nombre del proyecto para que el archivo compartido sea reconocible.
            File shared = new File(new File(getCacheDir(), "compartir"), safeName(projectName) + ".glb");
            shared.getParentFile().mkdirs();
            copy(new FileInputStream(modelFile), shared);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", shared);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("model/gltf-binary")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.share_model)));
        } catch (IOException e) {
            Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== Guardar / cancelar =====

    private File projectsDir() {
        return saveFolderPath != null ? new File(saveFolderPath) : new File(getExternalFilesDir(null), "Projects");
    }

    private File findSavedModel() {
        File[] candidates = {
                new File(projectsDir(), projectName + ".glb"),
                new File(new File(Uri.parse(imageUriString).getPath()).getParentFile(), projectName + ".glb"),
        };
        for (File f : candidates) {
            if (f.exists()) return f;
        }
        return null;
    }

    private boolean isTemporary(File f) {
        return f != null && f.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath());
    }

    private void deleteTemporaryModel() {
        if (isTemporary(modelFile)) modelFile.delete();
    }

    @Override
    protected void onDestroy() {
        // Si se sale sin guardar (botón atrás del sistema), no dejar el modelo en la caché.
        if (isFinishing()) deleteTemporaryModel();
        super.onDestroy();
    }

    private void handleCancel() {
        deleteTemporaryModel();
        finish();
    }

    private void saveProject() {
        if (modelOnly) {
            finish(); // ya está guardado en disco
            return;
        }
        if (!isNewProject) {
            // Solo falta guardar el modelo si se acaba de generar, junto a la imagen.
            if (isTemporary(modelFile)) {
                File imageFile = new File(Uri.parse(imageUriString).getPath());
                try {
                    copy(new FileInputStream(modelFile), new File(imageFile.getParentFile(), projectName + ".glb"));
                    deleteTemporaryModel();
                    Toast.makeText(this, R.string.project_saved, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            finish();
            return;
        }
        File targetDir = projectsDir();
        if (!targetDir.exists()) targetDir.mkdirs();
        String baseName = safeName(projectName);
        File imageFile = new File(targetDir, baseName + ".jpg");
        File glbFile = new File(targetDir, baseName + ".glb");

        try (InputStream is = getContentResolver().openInputStream(Uri.parse(imageUriString))) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) throw new IOException("imagen no válida");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            if (hasModel()) {
                copy(new FileInputStream(modelFile), glbFile);
                deleteTemporaryModel();
            }
            Toast.makeText(this, hasModel() && !generationFailed
                    ? R.string.project_saved : R.string.project_saved_2d_only, Toast.LENGTH_SHORT).show();
            finish();
        } catch (IOException e) {
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show();
        }
    }

    /** Nombre de archivo seguro a partir del nombre del proyecto. */
    static String safeName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return clean.isEmpty() ? "proyecto" : clean;
    }

    private static void copy(InputStream in, File dest) throws IOException {
        if (in == null) throw new IOException("sin datos");
        try (InputStream src = in; OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = src.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }
}
