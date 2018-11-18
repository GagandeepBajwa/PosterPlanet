package com.example.bradleycrump.arcoretutorial3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {



    private static final String TAG ="MyActivity";
    private ArFragment fragment;


    // For testing the plane detection
    private PointerDrawable pointer = new PointerDrawable();

    // More testing variables
    private boolean isTracking;
    private boolean isHitting;


    // Flag to ensure the ViewRenderable has been loaded


    // For accessing the user's photos
    private Intent galleryIntent;
    private static final int PICK_IMAGE = 1;


    private ArSceneView arSceneView;
    private ViewRenderable renderable;
    private GestureDetector gestureDetector;
    private boolean installRequested;
    private static final int RC_PERMISSIONS = 0*123;
    private Snackbar loadingMessageSnackbar = null;

    private boolean hasFinishedLoading = false;
    private boolean hasPlacedRenderable = false;



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == PICK_IMAGE){
            Log.v(TAG, "An image has been clicked");
            //create a viewrenderable
            // read the image from path after it
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!AppUtils.checkIsSupportedDeviceOrFinish(this)){
            return;
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // For accessing the user's photos
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            // Send the user to their photo gallery
            // To be used after loading of a static image is successful
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            galleryIntent = new Intent();
            galleryIntent.setType("image/*");
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(galleryIntent, "Select a picture"), PICK_IMAGE);

        });

        arSceneView = findViewById(R.id.sceneform_ar_scene_view);

        /* Initialize the ar fragment using sceneform
        fragment = (ArFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.sceneform_fragment);
*/

        // Allows for asynchronous programming, will run in another thread
        CompletableFuture<ViewRenderable> renderableCompletableFuture =
                ViewRenderable.builder().setView(this, R.layout.view_renderable).build();

        CompletableFuture.allOf(renderableCompletableFuture)
        .handle(
                (notUsed, throwable) -> {
                    if(throwable != null) {
                        AppUtils.displayError(this, "Unable to load renderable", throwable);
                        return null;
                    }

                    try {
                        renderable = renderableCompletableFuture.get();
                        // Everything loaded succesfully
                        hasFinishedLoading = true;
                    }

                    catch (InterruptedException | ExecutionException ex){
                        AppUtils.displayError(this, "Unable to load renderable", ex);
                    }
                    return null;
                });

        // Detect the user's tap
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
/*
        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
        });
*/
        /* Will need to replace the fragment with a scene that we can detect
        the clicks in.
         */

        // Set a touch listener on the Scene to listen for taps.
        arSceneView
                .getScene()
                .setOnTouchListener(
                        (HitTestResult hitTestResult, MotionEvent event) -> {
                            // If the solar system hasn't been placed yet, detect a tap and then check to see if
                            // the tap occurred on an ARCore plane to place the solar system.
                            if (!hasPlacedRenderable) {
                                return gestureDetector.onTouchEvent(event);
                            }

                            // Otherwise return false so that the touch event can propagate to the scene.
                            return false;
                        });

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (loadingMessageSnackbar == null) {
                                return;
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                if (plane.getTrackingState() == TrackingState.TRACKING) {
                                    hideLoadingMessage();
                                }
                            }
                        });
        AppUtils.requestCameraPermission(this, RC_PERMISSIONS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(arSceneView == null) {
            return;
        }

        if(arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted.
            try {
                Session session = AppUtils.createArSession(this, installRequested);
                if(session == null) {
                    installRequested = AppUtils.hasCameraPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e){
                AppUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            AppUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if(arSceneView.getSession() != null) {
            showLoadingMessage();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(arSceneView != null) {
            arSceneView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(arSceneView != null) {
            arSceneView.destroy();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if(!AppUtils.hasCameraPermission(this)) {
            if(!AppUtils.shouldShowRequestPermissionRationale(this)) {
                AppUtils.launchPermissionSettings(this);
            } else {
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) {
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onSingleTap(MotionEvent tap) {
        if(!hasFinishedLoading){
            // Can't load in anything if it isn't loading yet
            return;
        }

        Frame frame = arSceneView.getArFrame();
        if(frame != null) {
            if(!hasPlacedRenderable && tryPlaceRenderable(tap, frame)) {
                hasPlacedRenderable = true;
            }
        }
    }

    private boolean tryPlaceRenderable(MotionEvent tap, Frame frame) {
        if(tap != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
            for(HitResult hit : frame.hitTest(tap)) {
                Trackable trackable = hit.getTrackable();
                if(trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    // Create the Anchor
                    Anchor anchor = hit.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arSceneView.getScene());
                    Node node = createNode();
                    anchorNode.addChild(node);
                    return true;
                }
            }
        }

        return false;
    }

    private Node createNode() {
        Node base = new Node();

        Node viewRenderable = new Node();
        viewRenderable.setParent(base);
        viewRenderable.setRenderable(renderable);

        return base;
    }

    private void showLoadingMessage() {
        if(loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        MainActivity.this.findViewById(android.R.id.content),
                        "Searching for surfaces...",
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }


    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged)
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
        contentView.invalidate();

        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }



    // To display error to the user


    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth()/2, vw.getHeight()/2);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
