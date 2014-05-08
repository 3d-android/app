package com.li_tianyang.android3d;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ToggleButton;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainActivity";

	private Camera mCamera;
	private static int mCameraID;
	private CameraPreview mPreview;
	private boolean recording;

	private SensorManager mSensorManager;
	private Sensor accel;
	private Sensor gyro;

	public static enum RawDatumType {
		GYRO, /* gyroscope */
		ACCEL, /* accelerator */
		CAM, /* camera */
		FIN, /* data collection finished, continue processing */
		STOP, /* data collection stopped, forget about processing */
	}

	public class RawDatum {
		RawDatumType mType;

		byte[] mCam;
		float[] mSensorVal;

		public RawDatum() {
			mCam = null;
			mSensorVal = null;
		}
	};

	LinkedBlockingQueue<RawDatum> mRawData;

	private SensorEventListener mSensorEventListener = new SensorEventListener() {

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (recording) {
				RawDatum rawData = new RawDatum();
				rawData.mSensorVal = Arrays.copyOf(event.values,
						event.values.length);
				switch (event.sensor.getType()) {
				case Sensor.TYPE_ACCELEROMETER:
					rawData.mType = RawDatumType.ACCEL;
					break;
				case Sensor.TYPE_GYROSCOPE:
					rawData.mType = RawDatumType.GYRO;
					break;
				}
			}
		}
	};

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

		setContentView(R.layout.activity_main);

		mCamera = getCameraInstance();
		mPreview = new CameraPreview(this, mCamera);

		FrameLayout fL = (FrameLayout) findViewById(R.id.camera_preview);
		fL.addView(mPreview);

		ToggleButton cB = (ToggleButton) findViewById(R.id.control_button);
		cB.bringToFront();

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		gyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

		mRawData = new LinkedBlockingQueue<RawDatum>();

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

	public void control(View view) {
		recording = ((ToggleButton) findViewById(R.id.control_button))
				.isChecked();

		if (recording) {

			new Thread(new Runnable() {

				@Override
				public void run() {

					Log.d(TAG, "processing thread started");

					boolean loop = true;
					RawDatum rawDatum = null;

					try {
						while (loop) {
							rawDatum = mRawData.take();
							switch (rawDatum.mType) {

							case GYRO:
								break;

							case ACCEL:
								break;

							case CAM:
								break;

							case FIN:
								loop = false;
								break;

							case STOP:
								loop = false;
								break;
							}

						}

						switch (rawDatum.mType) {

						case FIN:
							break;

						case STOP:
							break;

						default:
							break;
						}

					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					Log.d(TAG, "processing thread ended");

				}
			}).start();

		} else {
			RawDatum rawDatum = new RawDatum();
			rawDatum.mType = RawDatumType.FIN;
			try {
				mRawData.put(rawDatum);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	public class CameraPreview extends SurfaceView implements
			SurfaceHolder.Callback, Camera.PreviewCallback {
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

		@Override
		public void onLayout(boolean changed, int left, int top, int right,
				int bottom) {
			if (changed) {
				Size s = goodPreviewSize();

				View par = (View) getParent();
				final int w = par.getWidth();
				final int h = par.getHeight();

				int l = (w - s.height) / 2;
				int t = (h - s.width) / 2;

				(this).layout(l, t, l + s.height, t + s.width);
			}
		}

		private void fitPreview() {
			Size s = goodPreviewSize();
			Camera.Parameters p = c.getParameters();
			p.setPreviewSize(s.width, s.height);
			c.setParameters(p);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			// The Surface has been created, now tell the camera where to draw
			// the preview.
			try {
				c.setPreviewDisplay(holder);

				fitPreview();

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

				fitPreview();

				c.setPreviewCallback(this);
				c.startPreview();

			} catch (Exception e) {
				Log.d(TAG, "Error starting camera preview: " + e.getMessage());
			}
		}

		public void setCamera(Camera cam) {
			c = cam;
		}

		private Size goodPreviewSize() {
			Camera.Parameters p = mCamera.getParameters();
			List<Size> sizes = p.getSupportedPreviewSizes();

			Size goodSize = null;

			View par = (View) getParent();
			final int w = par.getWidth();
			final int h = par.getHeight();
			double area = (double) (w * h);
			double partial = 0;

			for (Size s : sizes) {
				if (s.width <= h && s.height <= w) {
					if (goodSize == null) {
						goodSize = s;
						partial = (double) (s.width * s.height) / area;
					} else {
						double tmpPartial = (double) (s.width * s.height)
								/ area;
						if (tmpPartial > partial) {
							partial = tmpPartial;
							goodSize = s;
						}
					}
				}
			}

			return goodSize;
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (recording && data != null) {
				RawDatum rawDatum = new RawDatum();
				rawDatum.mType = RawDatumType.CAM;
				rawDatum.mCam = Arrays.copyOf(data, data.length);
				try {
					mRawData.put(rawDatum);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		releaseCamera(); // release the camera immediately on pause event

		mSensorManager.unregisterListener(mSensorEventListener);

		RawDatum rawDatum = new RawDatum();
		rawDatum.mType = RawDatumType.STOP;
		try {
			mRawData.put(rawDatum);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

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

		if (accel != null) {
			mSensorManager.registerListener(mSensorEventListener, accel,
					SensorManager.SENSOR_DELAY_NORMAL);
			Log.d(TAG, "Good: ACCELEROMETER Sensor");

		} else {
			Log.d(TAG, "Bad: ACCELEROMETER Sensor");

		}

		if (gyro != null) {
			mSensorManager.registerListener(mSensorEventListener, gyro,
					SensorManager.SENSOR_DELAY_NORMAL);
			Log.d(TAG, "Good: GYROSCOPE Sensor");

		} else {
			Log.d(TAG, "Bad: GYROSCOPE Sensor");

		}

		ToggleButton cB = (ToggleButton) findViewById(R.id.control_button);
		cB.setChecked(false);
		recording = false;
	}

}
