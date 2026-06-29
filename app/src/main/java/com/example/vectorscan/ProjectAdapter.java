package com.example.vectorscan;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    private List<Project> projects;
    private List<Project> allProjects; // full list for search filtering
    private OnProjectClickListener listener;

    public interface OnProjectClickListener {
        void onProjectClick(Project project);
    }

    public ProjectAdapter(List<Project> projects, OnProjectClickListener listener) {
        this.projects = projects;
        this.allProjects = new ArrayList<>(projects);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = projects.get(position);
        holder.tvProjectName.setText(project.getName());

        if (project.isFolder()) {
            holder.ivProjectPreview.setImageResource(R.drawable.ic_folder);
            holder.ivProjectPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.ivProjectPreview.setBackgroundColor(0xFFFFF3E0);
        } else if (project.hasImageFile()) {
            File imgFile = new File(project.getImagePath());
            if (imgFile.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                holder.ivProjectPreview.setImageBitmap(
                        BitmapFactory.decodeFile(imgFile.getAbsolutePath(), options));
            } else {
                holder.ivProjectPreview.setImageResource(R.drawable.ic_project_placeholder);
            }
            holder.ivProjectPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.ivProjectPreview.setBackgroundColor(0xFFE3F2FD);
        } else {
            holder.ivProjectPreview.setImageResource(project.getThumbnailResId());
            holder.ivProjectPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.ivProjectPreview.setBackgroundColor(0xFFE3F2FD);
        }

        // Short click
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick(project);
            }
        });

        // Long press → start drag
        holder.itemView.setOnLongClickListener(v -> {
            // Don't allow dragging the test project (no file path)
            if (!project.isFolder() && !project.hasImageFile()) {
                return false;
            }

            ClipData clipData = new ClipData(
                    new ClipDescription("project", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}),
                    new ClipData.Item(String.valueOf(holder.getAdapterPosition()))
            );

            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(clipData, shadowBuilder, project, 0);
            } else {
                v.startDrag(clipData, shadowBuilder, project, 0);
            }

            return true;
        });
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    /**
     * Returns the project at the given adapter position
     */
    public Project getProjectAt(int position) {
        if (position >= 0 && position < projects.size()) {
            return projects.get(position);
        }
        return null;
    }

    /**
     * Updates the full data set and refreshes the view
     */
    public void updateData(List<Project> newProjects) {
        this.projects.clear();
        this.projects.addAll(newProjects);
        this.allProjects = new ArrayList<>(newProjects);
        notifyDataSetChanged();
    }

    /**
     * Filters the list based on a search query
     */
    public void filter(String query) {
        projects.clear();
        if (query == null || query.isEmpty()) {
            projects.addAll(allProjects);
        } else {
            String lowerQuery = query.toLowerCase();
            for (Project p : allProjects) {
                if (p.getName().toLowerCase().contains(lowerQuery)) {
                    projects.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProjectPreview;
        TextView tvProjectName;

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProjectPreview = itemView.findViewById(R.id.ivProjectPreview);
            tvProjectName = itemView.findViewById(R.id.tvProjectName);
        }
    }
}
