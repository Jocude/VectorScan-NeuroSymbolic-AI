package com.example.vectorscan;

import android.Manifest;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
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

public class HomeActivity extends AppCompatActivity {

    private Uri photoUri;
    private List<Project> projectList;
    private ProjectAdapter projectAdapter;
    private RecyclerView rvProjects;

    // Search bar views
    private EditText etSearch;
    private ImageButton btnSearch;
    private ImageButton btnCloseSearch;
    private boolean isSearchOpen = false;

    // FAB menu state
    private boolean isFabMenuOpen = false;
    private FloatingActionButton fabAdd;
    private View fabOverlay;
    private LinearLayout fabMenuCreate;
    private LinearLayout fabMenuFolder;
    private LinearLayout fabMenuImport;

    // Drag & drop
    private FrameLayout trashZone;

    // Root folder for projects/folders
    private File rootProjectsDir;

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
        setContentView(R.layout.activity_home);

        // Initialize root projects directory
        rootProjectsDir = new File(getExternalFilesDir(null), "Projects");
        if (!rootProjectsDir.exists()) rootProjectsDir.mkdirs();

        // Store username for later use
        String username = getIntent().getStringExtra("username");

        // --- Toolbar setup ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // --- Search bar views ---
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnCloseSearch = findViewById(R.id.btnCloseSearch);

        btnSearch.setOnClickListener(v -> openSearchBar());
        btnCloseSearch.setOnClickListener(v -> closeSearchBar());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                projectAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Add user icon to toolbar
        ImageButton btnUser = new ImageButton(this);
        btnUser.setImageResource(R.drawable.ic_person);
        btnUser.setBackground(null);
        btnUser.setContentDescription("Menú de usuario");
        Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        params.setMarginEnd(16);
        toolbar.addView(btnUser, params);

        btnUser.setOnClickListener(v -> showUserMenu(v));

        // --- RecyclerView with projects ---
        rvProjects = findViewById(R.id.rvProjects);
        rvProjects.setLayoutManager(new GridLayoutManager(this, 2));

        projectList = new ArrayList<>();

        projectAdapter = new ProjectAdapter(projectList, project -> {
            if (project.isFolder()) {
                Intent intent = new Intent(this, FolderActivity.class);
                intent.putExtra("folderPath", project.getFolderPath());
                intent.putExtra("folderName", project.getName());
                startActivity(intent);
            } else if (project.hasImageFile()) {
                Intent intent = new Intent(this, ProjectEditorActivity.class);
                intent.putExtra("imageUri", android.net.Uri.fromFile(new File(project.getImagePath())).toString());
                intent.putExtra("projectName", project.getName());
                startActivity(intent);
            } else {
                Toast.makeText(this, "Proyecto: " + project.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        rvProjects.setAdapter(projectAdapter);

        // --- Trash zone ---
        trashZone = findViewById(R.id.trashZone);

        // --- Drag & drop listener on root ---
        setupDragAndDrop();

        // --- Expandable FAB setup ---
        fabAdd = findViewById(R.id.fabAdd);
        fabOverlay = findViewById(R.id.fabOverlay);
        fabMenuCreate = findViewById(R.id.fabMenuCreate);
        fabMenuFolder = findViewById(R.id.fabMenuFolder);
        fabMenuImport = findViewById(R.id.fabMenuImport);

        fabMenuCreate.setAlpha(0f);
        fabMenuFolder.setAlpha(0f);
        fabMenuImport.setAlpha(0f);

        fabAdd.setOnClickListener(v -> toggleFabMenu());
        fabOverlay.setOnClickListener(v -> closeFabMenu());

        findViewById(R.id.miniFabCreate).setOnClickListener(v -> {
            closeFabMenu();
            showNamingDialog();
        });
        fabMenuCreate.setOnClickListener(v -> {
            closeFabMenu();
            showNamingDialog();
        });

        findViewById(R.id.miniFabFolder).setOnClickListener(v -> {
            closeFabMenu();
            showCreateFolderDialog();
        });
        fabMenuFolder.setOnClickListener(v -> {
            closeFabMenu();
            showCreateFolderDialog();
        });

        findViewById(R.id.miniFabImport).setOnClickListener(v -> {
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
        loadProjectsAndFolders();
    }

    // ===== Drag & Drop =====

    private void setupDragAndDrop() {
        View rootView = findViewById(android.R.id.content);

        rootView.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // Show trash zone when drag starts
                    trashZone.setVisibility(View.VISIBLE);
                    trashZone.setAlpha(0f);
                    trashZone.animate().alpha(1f).setDuration(200).start();
                    fabAdd.setVisibility(View.GONE);
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    // Check if hovering over trash zone
                    float y = event.getY();
                    int[] trashLoc = new int[2];
                    trashZone.getLocationOnScreen(trashLoc);
                    float trashTop = trashLoc[1];

                    int[] rootLoc = new int[2];
                    rootView.getLocationOnScreen(rootLoc);
                    float adjustedY = y + rootLoc[1];

                    if (adjustedY >= trashTop) {
                        trashZone.setBackgroundResource(R.drawable.bg_trash_zone_active);
                        trashZone.setScaleX(1.1f);
                        trashZone.setScaleY(1.1f);
                    } else {
                        trashZone.setBackgroundResource(R.drawable.bg_trash_zone);
                        trashZone.setScaleX(1f);
                        trashZone.setScaleY(1f);

                        // Highlight folder items when hovering
                        highlightFolderUnderDrag(event.getX(), event.getY());
                    }
                    return true;

                case DragEvent.ACTION_DROP:
                    Project draggedProject = (Project) event.getLocalState();
                    float dropY = event.getY();

                    int[] trashLocDrop = new int[2];
                    trashZone.getLocationOnScreen(trashLocDrop);
                    float trashTopDrop = trashLocDrop[1];

                    int[] rootLocDrop = new int[2];
                    rootView.getLocationOnScreen(rootLocDrop);
                    float adjustedDropY = dropY + rootLocDrop[1];

                    if (adjustedDropY >= trashTopDrop) {
                        // Dropped on trash → delete
                        confirmDelete(draggedProject);
                    } else {
                        // Check if dropped on a folder
                        handleDropOnFolder(event.getX(), event.getY(), draggedProject);
                    }
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    // Hide trash zone
                    trashZone.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> {
                                trashZone.setVisibility(View.GONE);
                                trashZone.setScaleX(1f);
                                trashZone.setScaleY(1f);
                                trashZone.setBackgroundResource(R.drawable.bg_trash_zone);
                            }).start();
                    fabAdd.setVisibility(View.VISIBLE);
                    clearFolderHighlights();
                    return true;
            }
            return false;
        });
    }

    private void highlightFolderUnderDrag(float x, float y) {
        clearFolderHighlights();
        View child = rvProjects.findChildViewUnder(x, y - getToolbarHeight());
        if (child != null) {
            int pos = rvProjects.getChildAdapterPosition(child);
            Project target = projectAdapter.getProjectAt(pos);
            if (target != null && target.isFolder()) {
                child.setAlpha(0.6f);
                child.setScaleX(1.05f);
                child.setScaleY(1.05f);
            }
        }
    }

    private void clearFolderHighlights() {
        for (int i = 0; i < rvProjects.getChildCount(); i++) {
            View child = rvProjects.getChildAt(i);
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
        }
    }

    private int getToolbarHeight() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        return toolbar != null ? toolbar.getHeight() : 0;
    }

    private void handleDropOnFolder(float x, float y, Project draggedProject) {
        View child = rvProjects.findChildViewUnder(x, y - getToolbarHeight());
        if (child != null) {
            int pos = rvProjects.getChildAdapterPosition(child);
            Project targetFolder = projectAdapter.getProjectAt(pos);
            if (targetFolder != null && targetFolder.isFolder() && targetFolder != draggedProject) {
                moveItemIntoFolder(draggedProject, targetFolder);
                return;
            }
        }
        // Dropped elsewhere, do nothing
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
                        // Delete both image and glb if they exist
                        File imgFile = new File(project.getImagePath());
                        String basePath = project.getImagePath().substring(0, project.getImagePath().lastIndexOf('.'));
                        File glbFile = new File(basePath + ".glb");
                        deleted = imgFile.delete();
                        if (glbFile.exists()) glbFile.delete();
                    }
                    if (deleted) {
                        Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                        loadProjectsAndFolders();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moveItemIntoFolder(Project item, Project folder) {
        File source;
        if (item.isFolder()) {
            source = new File(item.getFolderPath());
        } else {
            source = new File(item.getImagePath());
        }

        File destDir = new File(folder.getFolderPath());
        File dest = new File(destDir, source.getName());

        boolean success = source.renameTo(dest);
        
        // Also move GLB if it's a project
        if (!item.isFolder() && item.hasImageFile()) {
             String basePath = item.getImagePath().substring(0, item.getImagePath().lastIndexOf('.'));
             File glbSource = new File(basePath + ".glb");
             if (glbSource.exists()) {
                 File glbDest = new File(destDir, glbSource.getName());
                 glbSource.renameTo(glbDest);
             }
        }

        if (success) {
            Toast.makeText(this, R.string.item_moved, Toast.LENGTH_SHORT).show();
            loadProjectsAndFolders();
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

    // ===== Load Projects =====

    private void loadProjectsAndFolders() {
        List<Project> items = new ArrayList<>();
        Map<String, Project> projectMap = new HashMap<>();

        if (rootProjectsDir.exists()) {
            File[] files = rootProjectsDir.listFiles();
            if (files != null) {
                // Folders first
                for (File f : files) {
                    if (f.isDirectory()) {
                        items.add(new Project(
                                f.getName(),
                                R.drawable.ic_folder,
                                true,
                                f.getAbsolutePath()
                        ));
                    }
                }
                // Group image and glb files by name
                for (File f : files) {
                    if (f.isFile() && isProjectFile(f.getName())) {
                        String fullName = f.getName();
                        int dotIndex = fullName.lastIndexOf('.');
                        String name = (dotIndex > 0) ? fullName.substring(0, dotIndex) : fullName;
                        String ext = (dotIndex > 0) ? fullName.substring(dotIndex).toLowerCase() : "";

                        Project existing = projectMap.get(name);
                        if (existing == null) {
                            Project p = new Project(name, f.getAbsolutePath());
                            projectMap.put(name, p);
                        } else {
                            // If we find an image and we already have a glb (or vice versa), 
                            // prefer setting the imagePath to the image file for the thumbnail.
                            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp")) {
                                // Update to image path if current is not an image
                                String currentPath = existing.getImagePath();
                                if (currentPath.endsWith(".glb")) {
                                    // Use reflection or just create a new one since Project is simple
                                    projectMap.put(name, new Project(name, f.getAbsolutePath()));
                                }
                            }
                        }
                    }
                }
                items.addAll(projectMap.values());
            }
        }

        if (items.isEmpty()) {
            items.add(new Project(getString(R.string.test_project_name), R.drawable.ic_project_placeholder));
        }

        projectList.clear();
        projectList.addAll(items);
        projectAdapter.updateData(items);
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

        // Try to get real filename
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                fileName = cursor.getString(nameIndex);
            }
            cursor.close();
        }

        // Verify it's a .glb file
        if (!fileName.toLowerCase().endsWith(".glb")) {
            Toast.makeText(this, "Solo se permiten archivos GLB", Toast.LENGTH_SHORT).show();
            return;
        }

        File destFile = new File(rootProjectsDir, fileName);

        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            OutputStream os = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            is.close();

            Toast.makeText(this, "Importado: " + fileName, Toast.LENGTH_SHORT).show();
            loadProjectsAndFolders();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al importar", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== Search Bar =====

    private void openSearchBar() {
        isSearchOpen = true;
        etSearch.setVisibility(View.VISIBLE);
        btnCloseSearch.setVisibility(View.VISIBLE);
        btnSearch.setVisibility(View.GONE);

        etSearch.setAlpha(0f);
        etSearch.animate().alpha(1f).setDuration(250).start();
        etSearch.requestFocus();

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearchBar() {
        isSearchOpen = false;
        etSearch.setText("");
        etSearch.setVisibility(View.GONE);
        btnCloseSearch.setVisibility(View.GONE);
        btnSearch.setVisibility(View.VISIBLE);

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);

        projectAdapter.filter("");
    }

    @Override
    public void onBackPressed() {
        if (isSearchOpen) {
            closeSearchBar();
        } else {
            super.onBackPressed();
        }
    }

    // ===== FAB Menu =====

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
                .setStartDelay(delay)
                .withEndAction(() -> item.setVisibility(View.GONE)).start();
    }

    // ===== Menus & Dialogs =====

    private void showUserMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.menu_user, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            } else if (id == R.id.action_help) {
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            } else if (id == R.id.action_logout) {
                getSharedPreferences("vectorscan_prefs", MODE_PRIVATE)
                        .edit().remove("saved_username").apply();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showNamingDialog() {
        EditText input = new EditText(this);
        input.setHint("Nombre del proyecto");
        input.setPadding(64, 32, 64, 32);

        new AlertDialog.Builder(this)
                .setTitle("Nuevo Proyecto")
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
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        File folder = new File(rootProjectsDir, folderName);
                        if (folder.exists()) {
                            Toast.makeText(this, "La carpeta ya existe", Toast.LENGTH_SHORT).show();
                        } else if (folder.mkdirs()) {
                            Toast.makeText(this, "Carpeta \"" + folderName + "\" creada", Toast.LENGTH_SHORT).show();
                            loadProjectsAndFolders();
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
        // Open file manager filtered for GLB files
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
        String imageFileName = "SCAN_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void openProjectEditor(Uri imageUri, String projectName) {
        Intent intent = new Intent(this, ProjectEditorActivity.class);
        intent.putExtra("imageUri", imageUri.toString());
        intent.putExtra("projectName", projectName);
        // Marcamos como nuevo para que ProjectEditorActivity sepa que debe subirlo
        intent.putExtra("isNewProject", true);
        startActivity(intent);
    }
}
