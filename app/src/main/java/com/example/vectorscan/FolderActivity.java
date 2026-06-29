package com.example.vectorscan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Activity to display the contents of a folder.
 * Supports creating projects, sub-folders, importing GLB files,
 * drag-to-delete, drag-into-subfolder, and drag-to-move-out.
 */
public class FolderActivity extends AppCompatActivity {

    private String folderPath;
    private Uri photoUri;
    private List<Project> contentList;
    private ProjectAdapter contentAdapter;
    private RecyclerView rvContents;
    private TextView tvEmpty;

    // FAB menu state
    private boolean isFabMenuOpen = false;
    private FloatingActionButton fabAdd;
    private View fabOverlay;
    private LinearLayout fabMenuCreate;
    private LinearLayout fabMenuFolder;
    private LinearLayout fabMenuImport;

    // Drag & drop
    private FrameLayout trashZone;
    private FrameLayout moveOutZone;

    // Temporary storage for the project name being created
    private String pendingProjectName;

    // Camera launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && photoUri != null) {
                    openProjectEditor(photoUri, pendingProjectName);
                } else {
                    Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show();
                }
            });

    // Gallery picker
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    openProjectEditor(uri, pendingProjectName);
                } else {
                    Toast.makeText(this, "No se seleccionó ninguna imagen", Toast.LENGTH_SHORT).show();
                }
            });

    // File import picker — GLB files
    private final ActivityResultLauncher<String[]> fileImportLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importGlbFile(uri);
                } else {
                    Toast.makeText(this, "No se seleccionó ningún archivo", Toast.LENGTH_SHORT).show();
                }
            });

    // Camera permission
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);

        folderPath = getIntent().getStringExtra("folderPath");
        String folderName = getIntent().getStringExtra("folderName");

        if (folderPath == null) {
            finish();
            return;
        }

        // --- Toolbar setup ---
        Toolbar toolbar = findViewById(R.id.toolbarFolder);
        toolbar.setTitle(folderName != null ? folderName : "Carpeta");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // --- Empty state ---
        tvEmpty = findViewById(R.id.tvEmptyFolder);

        // --- RecyclerView ---
        rvContents = findViewById(R.id.rvFolderContents);
        rvContents.setLayoutManager(new GridLayoutManager(this, 2));

        contentList = new ArrayList<>();
        contentAdapter = new ProjectAdapter(contentList, item -> {
            if (item.isFolder()) {
                Intent intent = new Intent(this, FolderActivity.class);
                intent.putExtra("folderPath", item.getFolderPath());
                intent.putExtra("folderName", item.getName());
                startActivity(intent);
            } else if (item.hasImageFile()) {
                Intent intent = new Intent(this, ProjectEditorActivity.class);
                intent.putExtra("imageUri", Uri.fromFile(new File(item.getImagePath())).toString());
                intent.putExtra("projectName", item.getName());
                intent.putExtra("saveFolder", folderPath); // Pasamos la carpeta actual para guardar ahí
                startActivity(intent);
            } else {
                Toast.makeText(this, "Proyecto: " + item.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        rvContents.setAdapter(contentAdapter);

        // --- Drag & drop zones ---
        trashZone = findViewById(R.id.trashZoneFolder);
        moveOutZone = findViewById(R.id.moveOutZone);
        setupDragAndDrop();

        // --- FAB setup ---
        fabAdd = findViewById(R.id.fabAddFolder);
        fabOverlay = findViewById(R.id.fabOverlayFolder);
        fabMenuCreate = findViewById(R.id.fabMenuCreateFolder);
        fabMenuFolder = findViewById(R.id.fabMenuFolderFolder);
        fabMenuImport = findViewById(R.id.fabMenuImportFolder);

        fabMenuCreate.setAlpha(0f);
        fabMenuFolder.setAlpha(0f);
        fabMenuImport.setAlpha(0f);

        fabAdd.setOnClickListener(v -> toggleFabMenu());
        fabOverlay.setOnClickListener(v -> closeFabMenu());

        // Create Project
        findViewById(R.id.miniFabCreateFolder).setOnClickListener(v -> {
            closeFabMenu();
            showNamingDialog();
        });
        fabMenuCreate.setOnClickListener(v -> {
            closeFabMenu();
            showNamingDialog();
        });

        // Create Folder
        findViewById(R.id.miniFabFolderFolder).setOnClickListener(v -> {
            closeFabMenu();
            showCreateFolderDialog();
        });
        fabMenuFolder.setOnClickListener(v -> {
            closeFabMenu();
            showCreateFolderDialog();
        });

        // Import
        findViewById(R.id.miniFabImportFolder).setOnClickListener(v -> {
            closeFabMenu();
            openFileManager();
        });
        fabMenuImport.setOnClickListener(v -> {
            closeFabMenu();
            openFileManager();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFolderContents();
    }

    // ===== Drag & Drop =====

    private void setupDragAndDrop() {
        View rootView = findViewById(android.R.id.content);

        rootView.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    trashZone.setVisibility(View.VISIBLE);
                    trashZone.setAlpha(0f);
                    trashZone.animate().alpha(1f).setDuration(200).start();
                    moveOutZone.setVisibility(View.VISIBLE);
                    moveOutZone.setAlpha(0f);
                    moveOutZone.animate().alpha(1f).setDuration(200).start();
                    fabAdd.setVisibility(View.GONE);
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    float x = event.getX();
                    float y = event.getY();

                    // Check trash zone
                    int[] trashLoc = new int[2];
                    trashZone.getLocationOnScreen(trashLoc);
                    int[] rootLoc = new int[2];
                    rootView.getLocationOnScreen(rootLoc);
                    float adjustedY = y + rootLoc[1];

                    if (adjustedY >= trashLoc[1]) {
                        trashZone.setBackgroundResource(R.drawable.bg_trash_zone_active);
                        trashZone.setScaleX(1.1f);
                        trashZone.setScaleY(1.1f);
                        moveOutZone.setBackgroundColor(0x301565C0);
                    } else if (x < moveOutZone.getWidth() + 20) {
                        // Hovering over move-out zone
                        moveOutZone.setBackgroundColor(0x601565C0);
                        trashZone.setBackgroundResource(R.drawable.bg_trash_zone);
                        trashZone.setScaleX(1f);
                        trashZone.setScaleY(1f);
                    } else {
                        trashZone.setBackgroundResource(R.drawable.bg_trash_zone);
                        trashZone.setScaleX(1f);
                        trashZone.setScaleY(1f);
                        moveOutZone.setBackgroundColor(0x301565C0);

                        // Highlight folder items
                        highlightFolderUnderDrag(x, y);
                    }
                    return true;

                case DragEvent.ACTION_DROP:
                    Project draggedProject = (Project) event.getLocalState();
                    float dropX = event.getX();
                    float dropY = event.getY();

                    // Check trash
                    int[] trashLocDrop = new int[2];
                    trashZone.getLocationOnScreen(trashLocDrop);
                    int[] rootLocDrop = new int[2];
                    rootView.getLocationOnScreen(rootLocDrop);
                    float adjustedDropY = dropY + rootLocDrop[1];

                    if (adjustedDropY >= trashLocDrop[1]) {
                        confirmDelete(draggedProject);
                    } else if (dropX < moveOutZone.getWidth() + 20) {
                        // Move out of folder to parent
                        moveItemToParent(draggedProject);
                    } else {
                        // Check drop on sub-folder
                        handleDropOnFolder(dropX, dropY, draggedProject);
                    }
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    trashZone.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> {
                                trashZone.setVisibility(View.GONE);
                                trashZone.setScaleX(1f);
                                trashZone.setScaleY(1f);
                                trashZone.setBackgroundResource(R.drawable.bg_trash_zone);
                            }).start();
                    moveOutZone.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> moveOutZone.setVisibility(View.GONE)).start();
                    fabAdd.setVisibility(View.VISIBLE);
                    clearFolderHighlights();
                    return true;
            }
            return false;
        });
    }

    private void highlightFolderUnderDrag(float x, float y) {
        clearFolderHighlights();
        View child = rvContents.findChildViewUnder(x, y - getToolbarHeight());
        if (child != null) {
            int pos = rvContents.getChildAdapterPosition(child);
            Project target = contentAdapter.getProjectAt(pos);
            if (target != null && target.isFolder()) {
                child.setAlpha(0.6f);
                child.setScaleX(1.05f);
                child.setScaleY(1.05f);
            }
        }
    }

    private void clearFolderHighlights() {
        for (int i = 0; i < rvContents.getChildCount(); i++) {
            View child = rvContents.getChildAt(i);
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
        }
    }

    private int getToolbarHeight() {
        Toolbar toolbar = findViewById(R.id.toolbarFolder);
        return toolbar != null ? toolbar.getHeight() : 0;
    }

    private void handleDropOnFolder(float x, float y, Project draggedProject) {
        View child = rvContents.findChildViewUnder(x, y - getToolbarHeight());
        if (child != null) {
            int pos = rvContents.getChildAdapterPosition(child);
            Project targetFolder = contentAdapter.getProjectAt(pos);
            if (targetFolder != null && targetFolder.isFolder() && targetFolder != draggedProject) {
                moveItemIntoFolder(draggedProject, targetFolder);
            }
        }
    }

    private void confirmDelete(Project project) {
        String message;
        if (project.isFolder()) {
            message = getString(R.string.delete_confirm_folder, project.getName());
        } else {
            message = getString(R.string.delete_confirm_file, project.getName());
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete_label, (d, w) -> {
                    boolean deleted = false;
                    if (project.isFolder()) {
                        deleted = deleteRecursive(new File(project.getFolderPath()));
                    } else if (project.hasImageFile()) {
                        File imgFile = new File(project.getImagePath());
                        String basePath = project.getImagePath().substring(0, project.getImagePath().lastIndexOf('.'));
                        File glbFile = new File(basePath + ".glb");
                        deleted = imgFile.delete();
                        if (glbFile.exists()) glbFile.delete();
                    }
                    if (deleted) {
                        Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                        loadFolderContents();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moveItemIntoFolder(Project item, Project folder) {
        File source = item.isFolder() ? new File(item.getFolderPath()) : new File(item.getImagePath());
        File dest = new File(folder.getFolderPath(), source.getName());

        boolean success = source.renameTo(dest);
        
        // Also move GLB if it's a project
        if (!item.isFolder() && item.hasImageFile()) {
             String basePath = item.getImagePath().substring(0, item.getImagePath().lastIndexOf('.'));
             File glbSource = new File(basePath + ".glb");
             if (glbSource.exists()) {
                 File glbDest = new File(folder.getFolderPath(), glbSource.getName());
                 glbSource.renameTo(glbDest);
             }
        }

        if (success) {
            Toast.makeText(this, R.string.item_moved, Toast.LENGTH_SHORT).show();
            loadFolderContents();
        } else {
            Toast.makeText(this, "Error al mover", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Moves an item from this folder up to the parent folder
     */
    private void moveItemToParent(Project item) {
        File currentFolder = new File(folderPath);
        File parentFolder = currentFolder.getParentFile();
        if (parentFolder == null) return;

        File source = item.isFolder() ? new File(item.getFolderPath()) : new File(item.getImagePath());
        File dest = new File(parentFolder, source.getName());

        boolean success = source.renameTo(dest);
        
        // Also move GLB
        if (!item.isFolder() && item.hasImageFile()) {
             String basePath = item.getImagePath().substring(0, item.getImagePath().lastIndexOf('.'));
             File glbSource = new File(basePath + ".glb");
             if (glbSource.exists()) {
                 File glbDest = new File(parentFolder, glbSource.getName());
                 glbSource.renameTo(glbDest);
             }
        }

        if (success) {
            Toast.makeText(this, R.string.item_moved, Toast.LENGTH_SHORT).show();
            loadFolderContents();
        } else {
            Toast.makeText(this, "Error al mover", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    // ===== Load Contents =====

    private void loadFolderContents() {
        contentList.clear();
        Map<String, Project> projectMap = new HashMap<>();
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                // Folders first
                for (File f : files) {
                    if (f.isDirectory()) {
                        contentList.add(new Project(
                                f.getName(), R.drawable.ic_folder, true, f.getAbsolutePath()));
                    }
                }
                // Group items by name
                for (File f : files) {
                    if (f.isFile() && isProjectFile(f.getName())) {
                        String fullName = f.getName();
                        int dotIndex = fullName.lastIndexOf('.');
                        String name = (dotIndex > 0) ? fullName.substring(0, dotIndex) : fullName;
                        String ext = (dotIndex > 0) ? fullName.substring(dotIndex).toLowerCase() : "";

                        Project existing = projectMap.get(name);
                        if (existing == null) {
                            projectMap.put(name, new Project(name, f.getAbsolutePath()));
                        } else if (ext.matches(".(jpg|jpeg|png|webp)")) {
                             // Prefer image as main path for thumbnail
                             projectMap.put(name, new Project(name, f.getAbsolutePath()));
                        }
                    }
                }
                contentList.addAll(projectMap.values());
            }
        }

        contentAdapter.updateData(contentList);
        tvEmpty.setVisibility(contentList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isProjectFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.endsWith(".glb");
    }

    // ===== Import GLB =====

    private void importGlbFile(Uri uri) {
        String fileName = "import_" + System.currentTimeMillis() + ".glb";
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
            cursor.close();
        }

        if (!fileName.toLowerCase().endsWith(".glb")) {
            Toast.makeText(this, "Solo se permiten archivos GLB", Toast.LENGTH_SHORT).show();
            return;
        }

        File destFile = new File(folderPath, fileName);
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            OutputStream os = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) os.write(buffer, 0, len);
            os.flush();
            os.close();
            is.close();
            Toast.makeText(this, "Importado: " + fileName, Toast.LENGTH_SHORT).show();
            loadFolderContents();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al importar", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== FAB Menu Animations =====

    private void toggleFabMenu() {
        if (isFabMenuOpen) closeFabMenu();
        else openFabMenu();
    }

    private void openFabMenu() {
        isFabMenuOpen = true;
        fabAdd.animate().rotation(45f).setDuration(300)
                .setInterpolator(new OvershootInterpolator()).start();
        fabOverlay.setVisibility(View.VISIBLE);
        fabOverlay.setAlpha(0f);
        fabOverlay.animate().alpha(1f).setDuration(250).start();
        showMenuItem(fabMenuCreate, 0);
        showMenuItem(fabMenuFolder, 50);
        showMenuItem(fabMenuImport, 100);
    }

    private void closeFabMenu() {
        isFabMenuOpen = false;
        fabAdd.animate().rotation(0f).setDuration(300)
                .setInterpolator(new OvershootInterpolator()).start();
        fabOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> fabOverlay.setVisibility(View.GONE)).start();
        hideMenuItem(fabMenuImport, 0);
        hideMenuItem(fabMenuFolder, 50);
        hideMenuItem(fabMenuCreate, 100);
    }

    private void showMenuItem(View item, long delay) {
        item.setVisibility(View.VISIBLE);
        item.setTranslationY(60f);
        item.setAlpha(0f);
        item.animate().translationY(0f).alpha(1f).setDuration(250)
                .setStartDelay(delay).setInterpolator(new OvershootInterpolator()).start();
    }

    private void hideMenuItem(View item, long delay) {
        item.animate().translationY(60f).alpha(0f).setDuration(200)
                .setStartDelay(delay).withEndAction(() -> item.setVisibility(View.GONE)).start();
    }

    // ===== Dialogs =====

    private void showNamingDialog() {
        EditText input = new EditText(this);
        input.setHint("Nombre del proyecto");
        input.setPadding(64, 32, 64, 32);

        new AlertDialog.Builder(this)
                .setTitle("Nuevo Proyecto en Carpeta")
                .setMessage("Introduce el nombre que tendrá tu proyecto:")
                .setView(input)
                .setPositiveButton("Siguiente", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pendingProjectName = name;
                    showCreateProjectDialog();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showCreateProjectDialog() {
        String[] options = {
                getString(R.string.take_photo),
                getString(R.string.gallery),
                getString(R.string.cancel)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.create_project)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            openCamera();
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                        }
                    } else if (which == 1) {
                        galleryLauncher.launch("image/*");
                    }
                }).show();
    }

    private void showCreateFolderDialog() {
        EditText input = new EditText(this);
        input.setHint("Nombre de la carpeta");
        input.setPadding(48, 32, 48, 16);
        new AlertDialog.Builder(this)
                .setTitle(R.string.create_folder)
                .setView(input)
                .setPositiveButton("Crear", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File newFolder = new File(folderPath, name);
                        if (newFolder.exists()) {
                            Toast.makeText(this, "La carpeta ya existe", Toast.LENGTH_SHORT).show();
                        } else if (newFolder.mkdirs()) {
                            Toast.makeText(this, "Carpeta \"" + name + "\" creada", Toast.LENGTH_SHORT).show();
                            loadFolderContents();
                        } else {
                            Toast.makeText(this, "Error al crear la carpeta", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openFileManager() {
        fileImportLauncher.launch(new String[]{"*/*"});
    }

    // ===== Camera & Editor =====

    private void openCamera() {
        try {
            File photoFile = createImageFile();
            photoUri = FileProvider.getUriForFile(this,
                    "com.example.vectorscan.fileprovider", photoFile);
            cameraLauncher.launch(photoUri);
        } catch (IOException ex) {
            Toast.makeText(this, "Error al crear archivo de imagen", Toast.LENGTH_SHORT).show();
            ex.printStackTrace();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        return File.createTempFile("SCAN_" + timeStamp, ".jpg", storageDir);
    }

    private void openProjectEditor(Uri imageUri, String projectName) {
        Intent intent = new Intent(this, ProjectEditorActivity.class);
        intent.putExtra("imageUri", imageUri.toString());
        intent.putExtra("projectName", projectName);
        intent.putExtra("isNewProject", true);
        intent.putExtra("saveFolder", folderPath); // Muy importante: indicamos dónde guardar
        startActivity(intent);
    }
}
