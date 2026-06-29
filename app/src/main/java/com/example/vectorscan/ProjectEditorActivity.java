package com.example.vectorscan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.SceneView;
import io.github.sceneview.node.ModelNode;
import kotlin.Unit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProjectEditorActivity extends AppCompatActivity {

    private String imageUriString;
    private String projectName;
    private boolean isNewProject;
    private String saveFolderPath;
    
    private SceneView sceneView;
    private ImageView ivProjectImage;
    private ModelNode modelNode;
    private File glbTemporalFile;
    private boolean isModelGenerated = false;

    // UI Views
    private LinearLayout llLoading;
    private TextView tvLoadingStatus;
    private MaterialButton btnSave;
    private MaterialButton btnRetry;
    private ExtendedFloatingActionButton fabAR;
    private MaterialButtonToggleGroup toggleGroup;
    private boolean serverFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_editor);

        // --- View Initialization ---
        Toolbar toolbar = findViewById(R.id.toolbarEditor);
        sceneView = findViewById(R.id.sceneView);
        ivProjectImage = findViewById(R.id.ivProjectImage);
        llLoading = findViewById(R.id.llLoading);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);
        fabAR = findViewById(R.id.fabAR);
        toggleGroup = findViewById(R.id.toggleGroup);
        btnSave = findViewById(R.id.btnSave);
        btnRetry = findViewById(R.id.btnRetry);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);

        // --- Toolbar Setup ---
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> handleCancel());

        // --- Intent Data ---
        imageUriString = getIntent().getStringExtra("imageUri");
        projectName = getIntent().getStringExtra("projectName");
        isNewProject = getIntent().getBooleanExtra("isNewProject", false);
        saveFolderPath = getIntent().getStringExtra("saveFolder");

        // --- SceneView Setup ---
        // Crucial for SceneView to work correctly
        sceneView.setLifecycle(getLifecycle());

        // --- UI Setup ---
        toggleGroup.setVisibility(isNewProject ? View.GONE : View.VISIBLE);
        fabAR.setVisibility(View.GONE);

        // Retry button listener
        btnRetry.setOnClickListener(v -> {
            if (imageUriString != null) {
                serverFailed = false;
                btnRetry.setVisibility(View.GONE);
                enviarImagenAlServidor(Uri.parse(imageUriString), projectName);
            }
        });

        if (imageUriString != null) {
            try {
                Uri imageUri = Uri.parse(imageUriString);
                ivProjectImage.setImageURI(imageUri);
                
                if (isNewProject) {
                    enviarImagenAlServidor(imageUri, projectName);
                    btnSave.setEnabled(false); 
                } else {
                    btnSave.setEnabled(true);
                    buscarArchivoGLB();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
            }
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn2D) {
                show2D();
            } else if (checkedId == R.id.btn3D) {
                show3D();
            }
        });

        // --- Button Listeners ---
        fabAR.setOnClickListener(v -> openAR());
        btnCancel.setOnClickListener(v -> handleCancel());
        btnSave.setOnClickListener(v -> saveProject(projectName));
        
        show2D();
    }

    private void openAR() {
        // Implementation for openAR
    }

    private void buscarArchivoGLB() {
        try {
            // 1. Check in saveFolderPath if provided
            if (saveFolderPath != null) {
                File glb = new File(saveFolderPath, projectName + ".glb");
                if (glb.exists()) {
                    glbTemporalFile = glb;
                    isModelGenerated = true;
                    return;
                }
            }
            
            // 2. Check in standard Projects folder
            File projectsDir = new File(getExternalFilesDir(null), "Projects");
            File glbDefault = new File(projectsDir, projectName + ".glb");
            if (glbDefault.exists()) {
                glbTemporalFile = glbDefault;
                isModelGenerated = true;
                return;
            }

            // 3. Try to deduce from image path
            Uri uri = Uri.parse(imageUriString);
            String path = uri.getPath();
            if (path != null) {
                File imageFile = new File(path);
                File folder = imageFile.getParentFile();
                if (folder != null) {
                    File glb = new File(folder, projectName + ".glb");
                    if (glb.exists()) {
                        glbTemporalFile = glb;
                        isModelGenerated = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show2D() {
        ivProjectImage.setVisibility(View.VISIBLE);
        sceneView.setVisibility(View.GONE);
        fabAR.setVisibility(View.GONE);
    }

    private void show3D() {
        if (!isModelGenerated || glbTemporalFile == null || !glbTemporalFile.exists()) {
            Toast.makeText(this, "Modelo 3D no disponible", Toast.LENGTH_SHORT).show();
            toggleGroup.check(R.id.btn2D);
            return;
        }
        ivProjectImage.setVisibility(View.GONE);
        sceneView.setVisibility(View.VISIBLE);
        fabAR.setVisibility(View.VISIBLE);
        cargarModelo3D();
    }

    private void cargarModelo3D() {
        if (!isModelGenerated || glbTemporalFile == null || !glbTemporalFile.exists()) return;

        if (modelNode != null) {
            sceneView.removeChildNode(modelNode);
            modelNode.destroy();
        }

        Uri modelUri = Uri.fromFile(glbTemporalFile);

        sceneView.getModelLoader().loadModelInstanceAsync(
                modelUri.toString(),
                uri -> uri,
                modelInstance -> {
                    if (modelInstance != null) {
                        // Constructor: (modelInstance, parent, autoAnimate, scaleToUnits, centerOrigin)
                        // centerOrigin = (0,0,0) → centers the model's bounding box at origin
                        // scaleToUnits = 2.0f → normalizes the model so its largest axis = 2 units
                        // This makes orbit controls rotate around the model center (like glTF Viewer)
                        modelNode = new ModelNode(
                                modelInstance,
                                null,                       // parent
                                true,                       // autoAnimate
                                2.0f,                       // scaleToUnits
                                new Float3(0f, 0f, 0f)      // centerOrigin
                        );
                        sceneView.addChildNode(modelNode);
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    private void handleCancel() {
        if (isNewProject && glbTemporalFile != null && glbTemporalFile.exists()) {
            if (glbTemporalFile.getAbsolutePath().contains(getCacheDir().getAbsolutePath())) {
                glbTemporalFile.delete();
            }
        }
        finish();
    }

    private void saveProject(String projectName) {
        if (imageUriString == null) return;

        File targetDir = (saveFolderPath != null) ? new File(saveFolderPath) : new File(getExternalFilesDir(null), "Projects");
        if (!targetDir.exists()) targetDir.mkdirs();

        File outputFile = new File(targetDir, projectName + ".jpg");
        File finalGlbFile = new File(targetDir, projectName + ".glb");

        try {
            // Save Image
            InputStream is = getContentResolver().openInputStream(Uri.parse(imageUriString));
            if (is != null) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null) {
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                }
            }

            // Save GLB
            if (glbTemporalFile != null && glbTemporalFile.exists() && !glbTemporalFile.equals(finalGlbFile)) {
                InputStream in = new java.io.FileInputStream(glbTemporalFile);
                FileOutputStream out = new FileOutputStream(finalGlbFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.close();
                
                if (glbTemporalFile.getAbsolutePath().contains(getCacheDir().getAbsolutePath())) {
                    glbTemporalFile.delete();
                }
            }

            if (serverFailed || !isModelGenerated) {
                Toast.makeText(this, "Proyecto guardado (solo imagen 2D)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Proyecto guardado", Toast.LENGTH_SHORT).show();
            }
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarImagenAlServidor(Uri imageUri, String name) {
        llLoading.setVisibility(View.VISIBLE);
        tvLoadingStatus.setText("Subiendo imagen...");

        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            File temp = new File(getCacheDir(), name + ".jpg");
            FileOutputStream os = new FileOutputStream(temp);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) os.write(buffer, 0, len);
            os.close();
            is.close();

            RequestBody req = RequestBody.create(MediaType.parse("image/*"), temp);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", temp.getName(), req);

            ApiService api = RetrofitClient.getApiService();
            api.uploadImage(body).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        tvLoadingStatus.setText("Recibiendo modelo 3D...");
                        new Thread(() -> {
                            try {
                                glbTemporalFile = new File(getCacheDir(), name + ".glb");
                                InputStream in = response.body().byteStream();
                                FileOutputStream out = new FileOutputStream(glbTemporalFile);
                                byte[] b = new byte[4096];
                                int l;
                                while ((l = in.read(b)) > 0) out.write(b, 0, l);
                                out.close();
                                in.close();
                                runOnUiThread(() -> {
                                    isModelGenerated = true;
                                    serverFailed = false;
                                    llLoading.setVisibility(View.GONE);
                                    btnRetry.setVisibility(View.GONE);
                                    btnSave.setEnabled(true);
                                    Toast.makeText(ProjectEditorActivity.this, "¡Modelo listo!", Toast.LENGTH_SHORT).show();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    serverFailed = true;
                                    llLoading.setVisibility(View.GONE);
                                    btnRetry.setVisibility(View.VISIBLE);
                                    btnSave.setEnabled(true);
                                    Toast.makeText(ProjectEditorActivity.this, "Error al procesar modelo. Puedes guardar solo la imagen o reintentar.", Toast.LENGTH_LONG).show();
                                });
                            }
                        }).start();
                    } else {
                        serverFailed = true;
                        llLoading.setVisibility(View.GONE);
                        btnRetry.setVisibility(View.VISIBLE);
                        btnSave.setEnabled(true);
                        Toast.makeText(ProjectEditorActivity.this, "Error en el servidor. Puedes guardar solo la imagen o reintentar.", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    serverFailed = true;
                    llLoading.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.VISIBLE);
                    btnSave.setEnabled(true);
                    String errorMsg;
                    if (t instanceof java.net.SocketTimeoutException) {
                        errorMsg = "Tiempo de espera agotado. Puedes guardar solo la imagen o reintentar.";
                    } else {
                        errorMsg = "Error de conexión. Puedes guardar solo la imagen o reintentar.";
                    }
                    Toast.makeText(ProjectEditorActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (IOException e) {
            llLoading.setVisibility(View.GONE);
            Toast.makeText(this, "Error al leer imagen", Toast.LENGTH_SHORT).show();
        }
    }
}
