package com.ale.nicecameraexample;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

/**
 * This is the graphical object used to display a real-time preview of the Camera.
 * It MUST be an extension of the {@link SurfaceView} class.<br /> 
 * It also needs to implement some other interfaces like {@link SurfaceHolder.Callback} 
 * (to react to SurfaceView events) and {@link PictureCallback} (to implement
 * the {@link PictureCallback#onPictureTaken(byte[], Camera)} method).
 * @author alessandrofrancesconi
 *
 */
public class CameraPreview 
	extends SurfaceView 
	implements SurfaceHolder.Callback, PictureCallback 
{

	/**
	 * ASPECT_RATIO_W and ASPECT_RATIO_H define the aspect ratio 
	 * of the Surface. They are used when {@link #onMeasure(int, int)}
	 * is called.
	 */
	private final float ASPECT_RATIO_W = 4.0f;
	private final float ASPECT_RATIO_H = 3.0f;
	
	/**
	 * The maximum dimension (in pixels) of the preview frames that are produced 
	 * by the Camera object. Note that this should not be intended as 
	 * the final, exact, dimension because the device could not support 
	 * it and a lower value is required (but the aspect ratio should remain the same).<br />
	 * See {@link CameraPreview#getBestSize(List, int)} for more information.
	 */
	private final int PREVIEW_MAX_WIDTH = 640;
	
	/**
	 * The maximum dimension (in pixels) of the images produced when a 
	 * {@link PictureCallback#onPictureTaken(byte[], Camera)} event is
	 * fired. Again, this is a maximum value and could not be the 
	 * real one implemented by the device.
	 */
	private final int PICTURE_MAX_WIDTH = 1280;
	
	// following: a simple reference of the camera object and cameraID value
	// that were previously defined in MainActivity
	private Camera camera;
	private int cameraID;
	
	/**
	 * In this example we look at camera preview buffer functionality too.<br />
	 * This is the array that will be filled everytime a single preview frame is 
	 * ready to be processed (for example when we want to show to the user 
	 * a transformed preview instead of the original one, or when we want to 
	 * make some image analysis in real-time without taking full-sized pictures).
	 */
	private byte[] previewBuffer;
	
	/**
	 * The "holder" is the underlying surface.
	 */
	private SurfaceHolder surfaceHolder;
	
	@SuppressWarnings("deprecation")
	public CameraPreview(Context context, Camera cam, int camID) {
		super(context);
		
		this.camera = cam;
		this.cameraID = camID;
		
		surfaceHolder = this.getHolder();
		surfaceHolder.addCallback(this);
		
		// deprecated setting, but required on Android versions prior to API 11
		if (Build.VERSION.SDK_INT < 11) {
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
	}
	
	/**
	 * Called when the surface is created for the first time. It sets all the 
	 * required {@link #camera}'s parameters and starts the preview stream.
	 * @param holder
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		setupCamera();
		startCameraPreview(holder);
	}

	/**
	 * [IMPORTANT!] A SurfaceChanged event means that the parent graphic has changed its layout 
	 * (for example when the orientation changes). It's necessary to update the {@link CameraPreview}
	 * orientation, so the preview is stopped, then updated, then re-activated.
	 * @param holder The SurfaceHolder whose surface has changed
	 * @param format The new PixelFormat of the surface
	 * @param w The new width of the surface
	 * @param h The new height of the surface
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (this.surfaceHolder.getSurface() == null) {
        	Log.e(MainActivity.LOG_TAG, "surfaceChanged(): surfaceHolder is null, nothing to do.");
    		return;
		}
		
		// stop preview before making changes!
		stopCameraPreview();

		// set preview size and make any resize, rotate or
		// reformatting changes here
		updateCameraDisplayOrientation();

		// restart preview with new settings
		startCameraPreview(holder);
	}

	/**
	 * surfaceDestroyed does nothing, because Camera release is 
	 * performed in the parent Activity  
	 * @param holder
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) { }
	
	/**
	 * [IMPORTANT!] Probably the most important method here. Lots of users experience bad 
	 * camera behaviors because they don't override this guy.
	 * In fact, some Android devices are very strict about the size of the surface 
	 * where the preview is printed: if its ratio is different from the 
	 * original one, it results in errors like "startPreview failed".<br />
	 * This methods takes care on this and applies the right size to the
	 * {@link CameraPreview}.
	 * @param widthMeasureSpec horizontal space requirements as imposed by the parent.
	 * @param heightMeasureSpec vertical space requirements as imposed by the parent.  
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int height = MeasureSpec.getSize(heightMeasureSpec);
		int width = MeasureSpec.getSize(widthMeasureSpec);

		// do some ultra high precision math...
		float ratio = ASPECT_RATIO_H / ASPECT_RATIO_W;
		if (width > height * ratio) {
			width = (int) (height / ratio + .5);
		} else {
			height = (int) (width / ratio + .5);
		}

		setMeasuredDimension(width, height);
		Log.i(MainActivity.LOG_TAG, "onMeasure(): set surface dimension to " + width + "x" + height);
	}
	
	/**
	 * It sets all the required parameters for the Camera object, like preview
	 * and picture size, format, flash modes and so on.
	 * In this particular example it initializes the {@link #previewBuffer} too.
	 */
	private void setupCamera() {
		if (this.camera == null) {
			Log.e(MainActivity.LOG_TAG, "setupCamera(): warning, camera is null");
    		return;
		}
		
		Camera.Parameters parameters = this.camera.getParameters();

		Size bestPreviewSize = getBestPreviewSize(parameters);
		Size bestPictureSize = getBestPictureSize(parameters);

		parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
		parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);

		parameters.setPreviewFormat(ImageFormat.NV21); // NV21 is the most supported format for preview frames
		parameters.setPictureFormat(ImageFormat.JPEG); // JPEG for full resolution images
        
		// example of settings
		try {
			parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
		}
		catch (NoSuchMethodError e) {
			// remember that not all the devices support a given feature
			Log.e(MainActivity.LOG_TAG, "setupCamera(): this camera ignored some unsupported settings.", e);
		}
		
		this.camera.setParameters(parameters); // save everything
		
		// print saved parameters
		int prevWidth = this.camera.getParameters().getPreviewSize().width;
		int prevHeight = this.camera.getParameters().getPreviewSize().height;
		int picWidth = this.camera.getParameters().getPictureSize().width;
		int picHeight = this.camera.getParameters().getPictureSize().height;

		Log.d(MainActivity.LOG_TAG, "setupCamera(): settings applied:\n\t"
			+ "preview size: " + prevWidth + "x" + prevHeight + "\n\t"
			+ "picture size: " + picWidth + "x" + picHeight
		);
		
		// here: previewBuffer initialization. It will host every frame that comes out
		// from the preview, so it must be big enough.
		// After that, it's linked to the camera with the setCameraCallback() method.
		try {
			this.previewBuffer = new byte[prevWidth * prevHeight * ImageFormat.getBitsPerPixel(this.camera.getParameters().getPreviewFormat()) / 8];
			setCameraCallback();
		} catch (IOException e) {
			Log.e(MainActivity.LOG_TAG, "setupCamera(): error setting camera callback.", e);
		}
	}

	/**
	 * [IMPORTANT!] Sets the {@link #previewBuffer} to be the default buffer where the 
	 * preview frames will be copied. Also sets the callback function 
	 * when a frame is ready.
	 * @throws IOException
	 */
	private void setCameraCallback() throws IOException {
		this.camera.addCallbackBuffer(this.previewBuffer);
		this.camera.setPreviewCallbackWithBuffer(new PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				processFrame(previewBuffer, cam);
				
				// [IMPORTANT!] remember to reset the CallbackBuffer at the end of every onPreviewFrame event.
				// Seems weird, but it works
				cam.addCallbackBuffer(previewBuffer);
			}
		});
	}
	
	/**
	 * Just a call to {@link CameraPreview#getBestSize(List, int)} for 
	 * the preview size
	 * @param parameters parameters of the camera
	 * @return an optimal size
	 */
	private Size getBestPreviewSize(Camera.Parameters parameters) {
		List<Size> sizes = parameters.getSupportedPreviewSizes();
		return getBestSize(sizes, PREVIEW_MAX_WIDTH);
	}

	/**
	 * Just a call to {@link CameraPreview#getBestSize(List, int)} for 
	 * the full-resolution picture size
	 * @param parameters parameters of the camera
	 * @return an optimal size
	 */
	private Size getBestPictureSize(Camera.Parameters parameters) {
		List<Size> sizes = parameters.getSupportedPictureSizes();
		return getBestSize(sizes, PICTURE_MAX_WIDTH);
	}

    /**
     * [IMPORTANT!] This is a convenient function to determine what's the proper
     * preview/picture size to be assigned to the camera, by looking at 
     * the list of supported sizes and the maximum value given
     * @param sizes sizes that are currently supported by the camera hardware,
     * retrived with {@link Camera.Parameters#getSupportedPictureSizes()} or {@link Camera.Parameters#getSupportedPreviewSizes()}
     * @param widthThreshold the maximum value we want to apply
     * @return an optimal size <= widthThreshold
     */
    private Size getBestSize(List<Size> sizes, int widthThreshold) {
    	Size bestSize = null;

    	for (Size currentSize : sizes) {
    		boolean isDesiredRatio = ((currentSize.width / ASPECT_RATIO_W) == (currentSize.height / ASPECT_RATIO_H));
    		boolean isBetterSize = (bestSize == null || currentSize.width > bestSize.width);
    		boolean isInBounds = currentSize.width <= widthThreshold;

    		if (isDesiredRatio && isInBounds && isBetterSize) {
    			bestSize = currentSize;
    		}
    	}

    	if (bestSize == null) {
    		bestSize = sizes.get(0);
    		Log.e(MainActivity.LOG_TAG, "determineBestSize(): can't find a good size. Setting to the very first...");
    	}
        
    	Log.i(MainActivity.LOG_TAG, "determineBestSize(): bestSize is " + bestSize.width + "x" + bestSize.height);
    	return bestSize;
	}
	
	/**
	 * In addition to calling {@link Camera#startPreview()}, it also 
	 * updates the preview display that could be changed in some situations
	 * @param holder the current {@link SurfaceHolder}
	 */
	private synchronized void startCameraPreview(SurfaceHolder holder) {
		try {
			this.camera.setPreviewDisplay(holder);
			this.camera.startPreview();
		} catch (Exception e){
			Log.e(MainActivity.LOG_TAG, "startCameraPreview(): error starting camera preview", e);
		}
	}
	
	/**
	 * It "simply" calls {@link Camera#stopPreview()} and checks
	 * for errors
	 */
	private synchronized void stopCameraPreview() {
		try {
			this.camera.stopPreview();
		} catch (Exception e){
			// ignored: tried to stop a non-existent preview
			Log.i(MainActivity.LOG_TAG, "stopCameraPreview(): tried to stop a non-running preview, this is not an error");
		}
	}
	
	/**
	 * Gets the current screen rotation in order to understand how much 
	 * the surface needs to be rotated
	 */
	private void updateCameraDisplayOrientation() {
		if (this.camera == null) {
			Log.e(MainActivity.LOG_TAG, "updateCameraDisplayOrientation(): warning, camera is null");
    		return;
		}
    	
		int result = 0;
		Activity parentActivity = (Activity)this.getContext();
    	
		int rotation = parentActivity.getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0: degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
		}
    	
		if (Build.VERSION.SDK_INT >= 9) {
			// on >= API 9 we can proceed with the CameraInfo method
			// and also we have to keep in mind that the camera could be the front one 
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(this.cameraID, info);
		
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				result = (info.orientation + degrees) % 360;
				result = (360 - result) % 360;  // compensate the mirror
			} 
			else {  
				// back-facing
				result = (info.orientation - degrees + 360) % 360;
			}
		}
		else {
			// TODO: on the majority of API 8 devices, this trick works good 
			// and doesn't produce an upside-down preview.
			// ... but there is a small amount of devices that don't like it!
			result = Math.abs(degrees - 90);
		}
		
		this.camera.setDisplayOrientation(result); // save settings
	}
    
	@Override
	public void onPictureTaken(byte[] raw, Camera cam) {
		Log.i(MainActivity.LOG_TAG, "onPictureTaken(): raw image is " + raw.length + " bytes long");
		
		stopCameraPreview(); // better do that because we don't need a preview right now
		
		// create a Bitmap from the raw data
		Bitmap picture = BitmapFactory.decodeByteArray(raw, 0, raw.length);
		
		// [IMPORTANT!] the image contained in the raw array is ALWAYS landscape-oriented.
		// We detect if the user took the picture in portrait mode and rotate it accordingly.
		Activity parentActivity = (Activity)this.getContext();
		int rotation = parentActivity.getWindowManager().getDefaultDisplay().getRotation();
		if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
			Matrix matrix = new Matrix();
			matrix.postRotate(90);
			// create a rotated version and replace the original bitmap
			picture = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, true);
		}
		
		// save to media library
		MediaStore.Images.Media.insertImage(parentActivity.getContentResolver(), picture, "NiceCameraExample", "NiceCameraExample test");
		
		// show a message
		Toast toast = Toast.makeText(parentActivity, "Picture saved to the media library", Toast.LENGTH_LONG);
		toast.show();
		
		// [IMPORTANT!] after the onPictureTaken event, the preview stream automatically stops.
		// You could navigate to another Activity, but in this example we just reset the
		// camera preview and continue
		startCameraPreview(this.surfaceHolder);
	}
	
	/**
	 * [IMPORTANT!] It's the callback that's fired when a preview frame is ready. Here
	 * we can do some real-time analysis of the preview's contents.
	 * Just remember that the buffer array is a list of pixels represented in 
	 * Y'UV420sp (NV21) format, so you could have to convert it to RGB before.
	 * Also, as in {@link #onPictureTaken(byte[], Camera)}, the raw image is always 
	 * landscape-oriented, even if the phone was in portrait mode.
	 * 
	 * @param raw the preview buffer
	 * @param cam the camera that filled the buffer
	 * @see <a href="http://en.wikipedia.org/wiki/YUV#Y.27UV420sp_.28NV21.29_to_ARGB8888_conversion">YUV Conversion - Wikipedia</a>
	 */
	private void processFrame(byte[] raw, Camera cam) {
		// TODO: insert a good YUV->RGB conversion algorithm?
	}

}
