package com.jocude.vectorscan;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.Trackable;

import java.io.File;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.ar.ARSceneView;
import io.github.sceneview.ar.node.AnchorNode;
import io.github.sceneview.node.ModelNode;
import kotlin.Unit;

/**
 * Coloca el modelo 3D sobre una superficie real con ARCore. Se puede ver como maqueta (1:50)
 * o a tamaño real, y girar o escalar con gestos.
 */
public class ArViewerActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL_PATH = "modelPath";
    private static final float MAQUETA = 1f / 50f;

    private ARSceneView arSceneView;
    private TextView tvHint;
    private MaterialButton btnScale;

    private File modelFile;
    private AnchorNode anchorNode;
    private ModelNode modelNode;
    private boolean loading = false;
    private boolean realScale = false;
    private boolean planeFound = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_viewer);

        String path = getIntent().getStringExtra(EXTRA_MODEL_PATH);
        modelFile = path != null ? new File(path) : null;
        if (modelFile == null || !modelFile.exists()) {
            Toast.makeText(this, R.string.model_not_available, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        arSceneView = findViewById(R.id.arSceneView);
        tvHint = findViewById(R.id.tvArHint);
        btnScale = findViewById(R.id.btnArScale);
        findViewById(R.id.btnArClose).setOnClickListener(v -> finish());
        btnScale.setOnClickListener(v -> {
            realScale = !realScale;
            applyScale();
        });

        // ARSceneView pide el permiso de cámara e instala ARCore si hace falta.
        arSceneView.setLifecycle(getLifecycle());
        arSceneView.setOnSessionUpdated((session, frame) -> {
            if (!planeFound && hasTrackedPlane(frame)) {
                planeFound = true;
                if (anchorNode == null) tvHint.setText(R.string.ar_hint_tap);
            }
            return Unit.INSTANCE;
        });
        arSceneView.setOnTrackingFailureChanged(reason -> {
            if (reason != null && reason != TrackingFailureReason.NONE) {
                tvHint.setVisibility(View.VISIBLE);
                tvHint.setText(R.string.ar_hint_tracking_lost);
            } else if (anchorNode != null) {
                tvHint.setVisibility(View.GONE);
            } else {
                tvHint.setText(planeFound ? R.string.ar_hint_tap : R.string.ar_hint_scan);
            }
            return Unit.INSTANCE;
        });
        arSceneView.setOnSessionFailed(exception -> {
            Toast.makeText(this, getString(R.string.ar_session_error, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
            finish();
            return Unit.INSTANCE;
        });
        arSceneView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) placeModel(event);
            return false; // deja pasar el gesto para girar y escalar el modelo
        });
    }

    private static boolean hasTrackedPlane(Frame frame) {
        for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) return true;
        }
        return false;
    }

    /** Busca un suelo o una mesa bajo el dedo y ancla allí el modelo. */
    private void placeModel(MotionEvent event) {
        Frame frame = arSceneView.getFrame();
        if (frame == null || loading) return;
        if (anchorNode != null && event.getEventTime() - event.getDownTime() > 300) {
            return; // ha sido un gesto sobre el modelo, no un toque para recolocarlo
        }
        for (HitResult hit : frame.hitTest(event)) {
            Trackable trackable = hit.getTrackable();
            if (trackable instanceof Plane
                    && ((Plane) trackable).getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                    && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                anchorAt(hit.createAnchor());
                return;
            }
        }
    }

    private void anchorAt(Anchor anchor) {
        if (anchorNode != null) {
            if (modelNode != null) anchorNode.removeChildNode(modelNode);
            arSceneView.removeChildNode(anchorNode);
            anchorNode.destroy();
        }
        anchorNode = new AnchorNode(arSceneView.getEngine(), anchor, null, null, null, null);
        arSceneView.addChildNode(anchorNode);
        tvHint.setVisibility(View.GONE);
        arSceneView.getPlaneRenderer().setVisible(false);

        if (modelNode != null) {
            anchorNode.addChildNode(modelNode);
            return;
        }
        loading = true;
        arSceneView.getModelLoader().loadModelInstanceAsync(
                Uri.fromFile(modelFile).toString(),
                resource -> resource,
                modelInstance -> {
                    loading = false;
                    if (modelInstance == null) {
                        Toast.makeText(this, R.string.model_load_error, Toast.LENGTH_SHORT).show();
                        return Unit.INSTANCE;
                    }
                    // Origen en el centro de la base: el modelo se apoya sobre la superficie.
                    modelNode = new ModelNode(modelInstance, null, true, null, new Float3(0f, -1f, 0f));
                    modelNode.setEditable(true);
                    modelNode.setPositionEditable(false);
                    applyScale();
                    anchorNode.addChildNode(modelNode);
                    btnScale.setVisibility(View.VISIBLE);
                    return Unit.INSTANCE;
                });
    }

    /** El GLB viene en metros: a escala 1 es tamaño real. */
    private void applyScale() {
        if (modelNode == null) return;
        modelNode.setScale(new Float3(realScale ? 1f : MAQUETA));
        btnScale.setText(realScale ? R.string.ar_scale_model : R.string.ar_scale_real);
    }
}
