package com.example.vectorscan;

/**
 * Model representing a project or folder with a name, thumbnail, and optional image path.
 */
public class Project {
    private String name;
    private int thumbnailResId;
    private boolean isFolder;
    private String folderPath;   // absolute path for folders on disk
    private String imagePath;    // absolute path to the project image file on disk

    // Constructor for projects with resource thumbnail (test project)
    public Project(String name, int thumbnailResId) {
        this.name = name;
        this.thumbnailResId = thumbnailResId;
        this.isFolder = false;
        this.folderPath = null;
        this.imagePath = null;
    }

    // Constructor for folders
    public Project(String name, int thumbnailResId, boolean isFolder, String folderPath) {
        this.name = name;
        this.thumbnailResId = thumbnailResId;
        this.isFolder = isFolder;
        this.folderPath = folderPath;
        this.imagePath = null;
    }

    // Constructor for saved projects with image file
    public Project(String name, String imagePath) {
        this.name = name;
        this.thumbnailResId = 0;
        this.isFolder = false;
        this.folderPath = null;
        this.imagePath = imagePath;
    }

    public String getName() {
        return name;
    }

    public int getThumbnailResId() {
        return thumbnailResId;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getImagePath() {
        return imagePath;
    }

    /**
     * Returns true if this project has a real image file (not just a resource thumbnail)
     */
    public boolean hasImageFile() {
        return imagePath != null && !imagePath.isEmpty();
    }
}
