package com.example.bradleycrump.arcoretutorial3;

import android.content.Intent;
import android.nfc.TagLostException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
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
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    // Debugging
    private static final String TAG ="MyActivity";

    // For accessing the user's photos
    private Intent galleryIntent;
    private static final int PICK_IMAGE = 1;

    // For accessing the scene session
    private ArSceneView arSceneView;
    private ViewRenderable renderable;

    // Detect user clicking the screen
    private GestureDetector gestureDetector;

    // Flags for system requirements
    private boolean installRequested;
    private static final int RC_PERMISSIONS = 0*123;

    // Flags for the status of the renderable
    private boolean hasFinishedLoading = false;
    private boolean hasPlacedRenderable = false;

    // Displaying messages to the user
    private Snackbar loadingMessageSnackbar = null;

    // Needs to process the user returning from the gallery with an image
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


        // For accessing the user's photos
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            galleryIntent = new Intent();
            galleryIntent.setType("image/*");
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(galleryIntent, "Select a picture"), PICK_IMAGE);

        });

        AppUtils.requestCameraPermission(this, RC_PERMISSIONS);
        // Connect the view
        arSceneView = findViewById(R.id.sceneform_ar_scene_view);

        // Allows for asynchronous programming, will run in another thread
        CompletableFuture<ViewRenderable> renderableCompletableFuture =
                ViewRenderable.builder().setView(this, R.layout.view_renderable).build();

        CompletableFuture.allOf(renderableCompletableFuture)
        .handle(
                (notUsed, throwable) -> {
                    if(throwable != null) {
                        AppUtils.displayError(this, "Unable to load renderable", throwable);// Debugging
                        return null;
                    }

                    try {
                        renderable = renderableCompletableFuture.get();
                        // Everything loaded succesfully
                        hasFinishedLoading = true;
                        Log.v(TAG, "The renderable has finished loading"); // Debug
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

        // Set a touch listener on the Scene to listen for user interaction
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
                            Log.v(TAG, "Plane has been clicked");
                            return false;
                        });

        // Set an update listener on the Scene that will hide
        // the loading message once a Plane is detected.
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (loadingMessageSnackbar == null) {
                                return;
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                Log.v(TAG, "Second condition in adding Update Listener");
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                Log.v(TAG, "Third condition in adding Update Listener");
                                return;
                            }

                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                if (plane.getTrackingState() == TrackingState.TRACKING) {
                                    Log.v(TAG, "Fourth condition in adding Update Listener");
                                    hideLoadingMessage();
                                }
                            }
                        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(arSceneView == null) {
            return;
        }

        if(arSceneView.getSession() == null) {
            // If the session isn't created yet, don't resume rendering.
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
            Log.v(TAG, "The renderable isn't done loading");
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
            Log.v(TAG, "First condition met in tryPlaceRenderable");
            for(HitResult hit : frame.hitTest(tap)) {
                Trackable trackable = hit.getTrackable();
                if(trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    Log.v(TAG, "Second condition met in tryPlaceRenderable");
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
        Log.v(TAG, "The renderable was not able to be placed");
        return false;
    }

    private Node createNode() {

        Node viewRenderable = new Node();
        viewRenderable.setRenderable(renderable);
       // viewRenderable.setWorldScale(1.0, 1.0,1.0);

        //View renderableView = renderable.getView();

        return viewRenderable;
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


    /* May add action bar later

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
    */
}
