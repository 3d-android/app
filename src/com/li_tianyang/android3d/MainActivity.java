package com.li_tianyang.android3d;

import java.io.IOException;
import java.util.List;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainActivity";

	private Camera mCamera;
	private static int mCameraID;
	private CameraPreview mPreview;

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance() {
		Camera c = null;

		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		int cameraCount = 0;
		cameraCount = Camera.getNumberOfCameras();
		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
			Camera.getCameraInfo(camIdx, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				try {
					c = Camera.open(camIdx);
					mCameraID = camIdx;
				} catch (RuntimeException e) {
					Log.d(TAG,
							"Camera is not available (in use or does not exist)"
									+ e.getMessage());
				}
			}
		}
		c.setDisplayOrientation(90);

		return c; // returns null if camera is unavailable
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/*
		 * this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		 * WindowManager.LayoutParams.FLAG_FULLSCREEN);
		 */

		setContentView(R.layout.activity_main);

		mCamera = getCameraInstance();
		mPreview = new CameraPreview(this, mCamera);

		FrameLayout fL = (FrameLayout) findViewById(R.id.camera_preview);
		fL.addView(mPreview);

		TextView sB = (TextView) findViewById(R.id.start_button);
		sB.bringToFront();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void openAbout() {
		Intent intent = new Intent(this, AboutActivity.class);
		startActivity(intent);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {
		case R.id.action_about:
			openAbout();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void start(View view) {
		// start capturing camera data
	}

	public class CameraPreview extends SurfaceView implements
			SurfaceHolder.Callback {
		private SurfaceHolder mHolder;
		private Camera c;

		public CameraPreview(Context context, Camera camera) {
			super(context);
			c = camera;

			// Install a SurfaceHolder.Callback so we get notified when the
			// underlying surface is created and destroyed.
			mHolder = getHolder();
			mHolder.addCallback(this);
			// deprecated setting, but required on Android versions prior to 3.0
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			// The Surface has been created, now tell the camera where to draw
			// the preview.
			try {
				c.setPreviewDisplay(holder);

				previewSize();

				c.startPreview();
			} catch (IOException e) {
				Log.d(TAG, "Error setting camera preview: " + e.getMessage());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// empty. Take care of releasing the Camera preview in your
			// activity.
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w,
				int h) {
			// If your preview can change or rotate, take care of those events
			// here.
			// Make sure to stop the preview before resizing or reformatting it.

			if (mHolder.getSurface() == null) {
				// preview surface does not exist
				return;
			}

			// stop preview before making changes
			try {
				c.stopPreview();
			} catch (Exception e) {
				// ignore: tried to stop a non-existent preview
			}

			// set preview size and make any resize, rotate or
			// reformatting changes here

			// start preview with new settings
			try {
				c.setPreviewDisplay(mHolder);

				previewSize();

				c.startPreview();

			} catch (Exception e) {
				Log.d(TAG, "Error starting camera preview: " + e.getMessage());
			}
		}

		public void setCamera(Camera cam) {
			c = cam;
		}

		private void previewSize() {
			Camera.Parameters p = mCamera.getParameters();
			List<Size> sizes = p.getSupportedPreviewSizes();
			// TODO
			p.setPreviewSize(480, 480);
			c.setParameters(p);
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		releaseCamera(); // release the camera immediately on pause event
	}

	private void releaseCamera() {
		if (mCamera != null) {

			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mCamera == null) {
			mCamera = Camera.open(mCameraID);
			mCamera.setDisplayOrientation(90);

			mPreview.setCamera(mCamera);
		}
	}

}
