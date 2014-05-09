package com.li_tianyang.android3d;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
	private Thread mProcThread;

	ToggleButton mControlButton;

	private SensorManager mSensorManager;
	private Sensor accel;
	private Sensor gyro;

	private int mPreviewW;
	private int mPreviewH;

	public class GLRenderer implements GLSurfaceView.Renderer {

		public void onSurfaceCreated(GL10 unused, EGLConfig config) {
			// Set the background frame color
			GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		}

		public void onDrawFrame(GL10 unused) {
			// Redraw background color
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		}

		public void onSurfaceChanged(GL10 unused, int width, int height) {
			GLES20.glViewport(0, 0, width, height);
		}
	}

	public class GLView extends GLSurfaceView {
		public GLView(Context context) {
			super(context);
			setRenderer(new GLRenderer());
			getHolder().setFormat(PixelFormat.TRANSLUCENT);
			setEGLContextClientVersion(2);
		}

		@Override
		public void onLayout(boolean changed, int left, int top, int right,
				int bottom) {
			if (changed) {

				View par = (View) getParent();
				final int w = par.getWidth();
				final int h = par.getHeight();

				int l = (w - mPreviewH) / 2;
				int t = (h - mPreviewW) / 2;

				(this).layout(l, t, l + mPreviewH, t + mPreviewW);
			}
		}
	}

	private GLView mGLView;

	public class UIHandlerType {
		// clear what has been drawn
		public static final int CLEAR_VIEW = 0;

		// update view with new drawing
		public static final int UPDATE_VIEW = 1;

		// finished receiving sensor data
		public static final int FIN_REC = 2;

		// finished processing sensor data
		public static final int FIN_PROC = 3;
	}

	public static class UIHandler extends Handler {

		private WeakReference<MainActivity> mMain;

		public UIHandler(MainActivity mainActivity) {
			super(Looper.getMainLooper());
			mMain = new WeakReference<MainActivity>(mainActivity);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity main = mMain.get();
			if (main == null) {
				Log.d(TAG + ":UIHandler", "MainActivity null in handler");
				return;
			}

			switch (msg.what) {
			case UIHandlerType.CLEAR_VIEW:
				Log.d(TAG + ":UIHandler", "need to clear drawing in capture");
				break;

			case UIHandlerType.UPDATE_VIEW:

				break;

			case UIHandlerType.FIN_REC:
				main.mControlButton.setEnabled(false);
				Log.d(TAG + ":UIHandler",
						"all data received, processing starts");
				break;

			case UIHandlerType.FIN_PROC:
				main.mControlButton.setEnabled(true);
				Log.d(TAG + ":UIHandler", "processing finished");
				break;

			default:
				Log.d(TAG + ":UIHandler", "got weird message");
			}
		}
	}

	private UIHandler mUIHandler = null;

	public static enum RawDatumType {
		GYRO, /* gyroscope */
		ACCEL, /* accelerator */
		CAM, /* camera */
		FIN, /* data collection finished, continue processing */
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

		mUIHandler = new UIHandler(this);

		mCamera = getCameraInstance();
		mPreview = new CameraPreview(this, mCamera);

		FrameLayout fL = (FrameLayout) findViewById(R.id.camera_preview);
		fL.addView(mPreview);

		/*
		 * mGLView = new GLView(this); fL.addView(mGLView);
		 * mGLView.bringToFront();
		 */

		mControlButton = (ToggleButton) findViewById(R.id.control_button);
		mControlButton.bringToFront();

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

			mProcThread = new Thread(new Runnable() {

				@Override
				public void run() {

					Log.d(TAG + "-procThread", "processing thread started");

					Message startMsg = mUIHandler
							.obtainMessage(UIHandlerType.CLEAR_VIEW);
					startMsg.sendToTarget();

					boolean loop = true;
					RawDatum rawDatum = null;

					try {
						while (!Thread.currentThread().isInterrupted() && loop) {
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

							}
						}

					} catch (InterruptedException e) {
						Log.d(TAG + "-procThread",
								"processing thread may have been interuptted while waiting for data");
					}

					if (loop) {
						Message stopMsg = mUIHandler
								.obtainMessage(UIHandlerType.CLEAR_VIEW);
						stopMsg.sendToTarget();
					} else {
						Message finRecMsg = mUIHandler
								.obtainMessage(UIHandlerType.FIN_REC);
						finRecMsg.sendToTarget();

						Message finProcMsg = mUIHandler
								.obtainMessage(UIHandlerType.FIN_PROC);
						finProcMsg.sendToTarget();
					}

					Log.d(TAG + "-procThread", "processing thread ended");

				}
			});
			mProcThread.start();

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
				Log.d(TAG + ":CameraPreview", "Error setting camera preview: "
						+ e.getMessage());
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
				Log.d(TAG + ":CameraPreview", "Error starting camera preview: "
						+ e.getMessage());
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

			mPreviewH = goodSize.height;
			mPreviewW = goodSize.width;

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

		if (recording) {
			recording = false;
			mProcThread.interrupt();
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

		mControlButton.setEnabled(true);
		mControlButton.setChecked(false);
		recording = false;
	}

}
