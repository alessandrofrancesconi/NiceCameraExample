package com.ale.nicecameraexample;

import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.content.res.Configuration;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.RelativeLayout;


/**
 * This is the main activity and it's shown when the program start.
 * For this example, it contains a {@link #camPreview "Camera surface"}
 * that renders the preview to the GUI, and a Button to take a picture.
 * @author alessandrofrancesconi
 *
 */
public class MainActivity extends Activity {

	public static final String LOG_TAG = "NiceCameraExample";
	
	/**
	 * 'camera' is the object that references the hardware device 
	 * installed on your Android phone.
	 */
	private Camera camera;
	
	/**
	 * Phone can have multiple cameras, so 'cameraID' is a 
	 * useful variable to store which one of the camera is active.
	 * It starts with value -1 
	 */
	private int cameraID;
	
	/**
	 * 'camPreview' is the object that prints the data
	 * coming from the active camera on the GUI, that is... frames!
	 * It's an instance of the 'CameraPreview' class, more information
	 * in {@link CameraPreview}
	 */
	private CameraPreview camPreview;
	
	
	/**
	 * In the onCreate() we could initialize the camera preview, by calling 
	 * {@link #setCameraInstance()}. That's not strongly necessary to call it right here, 
	 * the camera preview may start in a secondary moment.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// first, we check is it's possible to get an "instance" of the hardware camera
		if (setCameraInstance() == true) {
			// everything's OK, we can go further and create the preview object
			this.camPreview = new CameraPreview(this, this.camera, this.cameraID);
		}
		else {
			// error here! we can print something or just cry.
			this.finish();
		}
		
		// if the preview is set, we add it to the contents of our activity.
		RelativeLayout preview = (RelativeLayout) findViewById(R.id.preview_layout);
		preview.addView(this.camPreview);
        
		// also we set some layout properties
		RelativeLayout.LayoutParams previewLayout = (RelativeLayout.LayoutParams) camPreview.getLayoutParams();
		previewLayout.width = LayoutParams.MATCH_PARENT;
		previewLayout.height = LayoutParams.MATCH_PARENT;
		this.camPreview.setLayoutParams(previewLayout);
        
		// on the main activity there's also a "capture" button, we set its behavior
		// when it gets clicked here
		Button captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					camera.takePicture(null, null, camPreview); // request a picture
				}
			}
		);
		
		// at last, a call to set the right layout of the elements (like the button)
		// depending on the screen orientation (if it's changeable).
		fixElementsPosition(getResources().getConfiguration().orientation);
	}
	
	// following: a set of overwritten methods in order to start and stop the
	// camera when the app gets closed or something
	
	@Override
	protected void onResume() {
		super.onResume();
		if (setCameraInstance() == true) {
			// TODO: camPreview.refresh...
		}
		else {
			Log.e(MainActivity.LOG_TAG, "onResume(): can't reconnect the camera");
			this.finish();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		releaseCameraInstance();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		releaseCameraInstance();
	}
	
	/**
	 * This method is added in order to detect changes in orientation.
	 * If we want we can react on this and change the position of
	 * some GUI elements (see {@link #fixElementsPosition(int)}
	 * method).
	 */
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		fixElementsPosition(newConfig.orientation);
	}
	
	/**
	 * [IMPORTANT!] The most important method of this Activity: it asks for an instance 
	 * of the hardware camera(s) and save it to the private field {@link #camera}.
	 * @return TRUE if camera is set, FALSE if something bad happens
	 */
	private boolean setCameraInstance() {
		if (this.camera != null) {
			// do the job only if the camera is not already set
			Log.i(MainActivity.LOG_TAG, "setCameraInstance(): camera is already set, nothing to do");
			return true;
		}
		
		// warning here! starting from API 9, we can retrieve one from the multiple 
		// hardware cameras (ex. front/back)
		if (Build.VERSION.SDK_INT >= 9) {
			
			if (this.cameraID < 0) {
				// at this point, it's the first time we request for a camera
				Camera.CameraInfo camInfo = new Camera.CameraInfo();
				for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
					Camera.getCameraInfo(i, camInfo);
				
					if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
						// in this example we'll request specifically the back camera 
						try {
							this.camera = Camera.open(i);
							this.cameraID = i; // assign to cameraID this camera's ID (O RLY?)
							return true;
						}
						catch (RuntimeException e){
							// something bad happened! this camera could be locked by other apps 
							Log.e(MainActivity.LOG_TAG, "setCameraInstance(): trying to open camera #" + i + " but it's locked", e);
						}
					}
				}
			}
			else {
				// at this point, a previous camera was set, we try to re-instantiate it
				try {
					this.camera = Camera.open(this.cameraID);
				}
				catch (RuntimeException e){
					Log.e(MainActivity.LOG_TAG, "setCameraInstance(): trying to re-open camera #" + this.cameraID + " but it's locked", e);
				}
			}
		}
		
		// we could reach this point in two cases:
		// - the API is lower than 9
		// - previous code block failed
		// hence, we try the classic method, that doesn't ask for a particular camera
		if (this.camera == null) {
			try {
				this.camera = Camera.open();
				this.cameraID = 0;
			}
			catch (RuntimeException e) {
				// this is REALLY bad, the camera is definitely locked by the system.
				Log.e(MainActivity.LOG_TAG, 
					"setCameraInstance(): trying to open default camera but it's locked. "
					+ "The camera is not available for this app at the moment.", e
				);
				return false;
			}
		}

		// here, the open() went good and the camera is available
		Log.i(MainActivity.LOG_TAG, "setCameraInstance(): successfully set camera #" + this.cameraID);
		return true;
	}

	/**
	 * [IMPORTANT!] Another very important method: it releases all the resources and the lock
	 * we created while using the camera. It MUST be called everytime the app exits, crashes, 
	 * is paused or whatever. The order of the called methods are the following: <br />
	 * 
	 * 1) stop any preview coming to the GUI, if running <br />
	 * 2) call {@link Camera#release()} <br />
	 * 3) set our camera object to null and invalidate its ID
	 */
	private void releaseCameraInstance() {
		if (this.camera != null) {
			try {
				this.camera.stopPreview();
			}
			catch (Exception e) {
				Log.i(MainActivity.LOG_TAG, "unsetCameraInstance(): tried to stop a non-existent preview, this is not an error");
			}
			
			this.camera.setPreviewCallback(null);
			this.camera.release();
			this.camera = null;
			this.cameraID = -1;
			Log.i(MainActivity.LOG_TAG, "unsetCameraInstance(): camera has been released.");
		}
	}
	
	/**
	 * Everytime the screen changes its orientation, the layout of the 
	 * snap button changes to a more convenient position.
	 * @param orientation
	 */
	private void fixElementsPosition(int orientation) {		
		Button captureButton = (Button)findViewById(R.id.button_capture);
		FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) captureButton.getLayoutParams();
	    
		switch(orientation) {
			case Configuration.ORIENTATION_LANDSCAPE: 
				layout.gravity = (Gravity.RIGHT | Gravity.CENTER); break;
			case Configuration.ORIENTATION_PORTRAIT: 
				layout.gravity = (Gravity.BOTTOM | Gravity.CENTER); break;
		}
	}
}
