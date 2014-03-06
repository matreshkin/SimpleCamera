package com.example.camera.simplecamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * User: Polikarpov V.
 * Date: 03.02.14
 */

public class SimpleCamera {


    public interface BitmapCreatedCallback {
        void done(Bitmap bmp);
    }

    private int mCameraIndex = 0;
    private Camera mCamera = null;
    private MediaRecorder mRecorder = null;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mReadySurfaceHolder = null;

    private Shooter mShooter = null;
    private File mOutputVideoFile = null;
    private File mOutputImageFile = null;

    private CamcorderProfile mCamcorderProfile = null;

    private int mRotationAngle = 0;
    private Camera.Size mVideoSize = null;
    private Camera.Size mPictureSize = null;
    private Camera.Size mPreviewSize = null;
    private boolean mCallVideoAutoFocus = false;
    private boolean mCallPictureAutoFocus = false;

    private short mMaxPictureWidth = Short.MAX_VALUE;
    private short mMaxPictureHeight = Short.MAX_VALUE;
    private short mMaxVideoWidth = Short.MAX_VALUE;
    private short mMaxVideoHeight = Short.MAX_VALUE;
    private short mMaxPreviewWidth = Short.MAX_VALUE;
    private short mMaxPreviewHeight = Short.MAX_VALUE;

    /**
     * If Preview has been started outside
     * if preview is really processing now, mCamera != null
     */
    private boolean mDoingPreview = false;

    /**
     * If Record has been started outside
     * if Record is really processing now, mRecorder != null
     */
    private boolean mDoingRecord = false;

    private Handler mHandler = new Handler();

    private class Shooter implements Camera.PictureCallback {
        BitmapCreatedCallback mCallback;
        int mCalledCounter = 0;
        boolean mDone = false;

        Shooter(BitmapCreatedCallback callback) {
            mCallback = callback;
        }

        void take() {
            if (mCallback == null) return;
            if (mCamera == null) return;
            Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (mCallback == null) return;
                    if (mCamera == null) return;
                    mCamera.takePicture(null, new RawCallback(), new PostViewCallback(), Shooter.this);
                }
            };
            if (mCallPictureAutoFocus) {
                try {
                    mCamera.autoFocus(focusCallback);
                } catch (Exception e) {
                    mCallPictureAutoFocus = false;
                    focusCallback.onAutoFocus(true, mCamera);
                }
            } else {
                focusCallback.onAutoFocus(true, mCamera);
            }
        }

        @Override
        public void onPictureTaken(byte[] imageData, Camera c) {
            _stopTakingPicture();
            if (imageData == null) {
                mCallback.done(null);
                mCallback = null;
            }
            if (mCallback == null) return;
            if (imageData == null) return;
            Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            mCallback.done(bmp);
            mCallback = null;
        }

        // Do nothing, our goal is JPEG !
        class RawCallback implements Camera.PictureCallback {
            @Override public void onPictureTaken(byte[] data, Camera camera) {
                return;
            }
        }
        class PostViewCallback implements Camera.PictureCallback {
            @Override public void onPictureTaken(byte[] data, Camera camera) {
                return;
            }
        }
    }

    private final SurfaceHolder.Callback mSurfaceHolderCallback =
            new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mReadySurfaceHolder = holder;
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder,
                                           int format, int width, int height) {
                    mReadySurfaceHolder = holder;
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    if (mReadySurfaceHolder != holder) return;
                    release();
                    mSurfaceView = null;
                    mReadySurfaceHolder = null;
                }
            };

    //////////////  INIT/RELEASE  ///////////////

    @SuppressWarnings("deprecation")
    public void init(SurfaceView surfaceView) {
        if (mSurfaceView != surfaceView) {
            mSurfaceView = surfaceView;
            if (Build.VERSION.SDK_INT < 11) {
                mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            }
            mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            mReadySurfaceHolder = null;
        }
    }

    public int setCameraIndex(int index) {
        if (Build.VERSION.SDK_INT >= 9) {
            if (index < Camera.getNumberOfCameras())
                mCameraIndex = index;
        } else {
            mCameraIndex = index;
        }
        return mCameraIndex;
    }

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }

    public void setMaxPictureSize(short width, short height) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width and height must be positive");
        mMaxPictureWidth = width;
        mMaxPictureHeight = height;
    }

    public void setMaxVideoSize(short width, short height) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width and height must be positive");
        mMaxVideoWidth = width;
        mMaxVideoHeight = height;
    }

    public void setMaxPreviewSize(short width, short height) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width and height must be positive");
        mMaxPreviewWidth = width;
        mMaxPreviewHeight = height;
    }

    public void setRotationAngle(int rotationAngle) {
        if (rotationAngle != 0 && rotationAngle != 90 &&
                rotationAngle != 180 && rotationAngle != 270)
            throw new IllegalArgumentException("rotationAngle must be 0, 90, 180 or 270");
        mRotationAngle = rotationAngle;
    }

    public void release() {
        stopRecording();
        stopPreview();
        // surface view has its own lifecycle
        //  - moved to surfaceDestroyed()
        // mSurfaceView = null;
        // mReadySurfaceHolder = null;
    }

    /////////////  PREVIEW  //////////////

    public void startPreview() {
        if (mDoingPreview) return;
        mDoingPreview = true;
        _startPreview();
    }

    public void stopPreview() {
        if (!mDoingPreview) return;
        mDoingPreview = false;
        _stopPreview();
    }

    private void _startPreview() {
        if (mCamera != null) _stopPreview();
        if (!mDoingPreview) return;
        if (mReadySurfaceHolder == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _startPreview();
                }
            });
            return;
        }

        if (Build.VERSION.SDK_INT >= 9) mCamera = Camera.open(mCameraIndex);
        else mCamera = Camera.open();

        _initCamcorderProfile(); // firstly
        _initCamera();           // then

        try {
            mCamera.setPreviewDisplay(mReadySurfaceHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void _stopPreview() {
        if (mCamera == null) return;
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    private void _initCamera() {
        Camera.Parameters parameters = mCamera.getParameters();

        // defining the right preview size is very important for video recording
        Camera.Size sizePreview = null;
        if (Build.VERSION.SDK_INT >= 11) {
            // if getSupportedVideoSizes() returns not null
            //  the preview stream is separated from video recording stream
            List<Camera.Size> listVideo = parameters.getSupportedVideoSizes();
            if (listVideo != null) {
                mVideoSize = _getBestSize(mMaxVideoWidth, mMaxVideoHeight, listVideo);

                // full screen with navigation bar stretches preview
                // http://stackoverflow.com/questions/19577299/android-camera-preview-stretched
                List<Camera.Size> listPreview = parameters.getSupportedPreviewSizes();
                // width should be less than height
//                int w = mSurfaceView.getMeasuredWidth();
//                int h = mSurfaceView.getMeasuredHeight();
//                if (w < h) { int t = w; w = h; h = t; }
//                sizePreview = _getOptimalPreviewSize(listPreview, w, h);
                if (sizePreview == null) {
                    sizePreview = parameters.getPreferredPreviewSizeForVideo();
                }
                // the last try for this situation
                if (sizePreview == null) {
                    sizePreview = _getBestSize(mMaxPreviewWidth, mMaxPreviewHeight, listPreview);
                }
            }
        }
        // if getSupportedVideoSizes() returns null
        //  that means that the preview stream will be used for video recording
        // and we should choose the right one
        if (sizePreview == null) {
            // try to synchronize size with recorder profile
            if (mCamcorderProfile != null) {
                List<Camera.Size> list = parameters.getSupportedPreviewSizes();
                for (Camera.Size s : list) {
                    if (s.width == mCamcorderProfile.videoFrameWidth &&
                            s.height == mCamcorderProfile.videoFrameHeight) {
                        sizePreview = s;
                        break;
                    }
                }
            }
        }
        // THE last try
        if (sizePreview == null) {
            sizePreview = _getBestSize(
                    Math.min(mMaxPreviewWidth, mMaxVideoWidth),
                    Math.min(mMaxPreviewHeight, mMaxVideoHeight),
                    parameters.getSupportedPreviewSizes());
        }

        if (sizePreview != null) {
            parameters.setPreviewSize(sizePreview.width, sizePreview.height);
            if (mRotationAngle == 90 || mRotationAngle == 270)
                mPreviewSize = mCamera.new Size(sizePreview.height, sizePreview.width);
            else mPreviewSize = sizePreview;
        }

        Camera.Size sizePicture = _getBestSize(
                mMaxPictureWidth, mMaxPictureHeight,
                parameters.getSupportedPictureSizes());
        if (sizePicture != null) parameters.setPictureSize(
                sizePicture.width, sizePicture.height);
        mPictureSize = sizePicture;

        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
//            mCallVideoAutoFocus = false;
//            mCallPictureAutoFocus = false;
            mCallVideoAutoFocus = true;
            mCallPictureAutoFocus = true;
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCallVideoAutoFocus = true;
                mCallPictureAutoFocus = true;
            }
        }

        parameters.setRotation(mRotationAngle);
        parameters.setPictureFormat(ImageFormat.JPEG);
        mCamera.setParameters(parameters);
        mCamera.setDisplayOrientation(mRotationAngle);
    }

    // taken from
    // http://stackoverflow.com/questions/19577299/android-camera-preview-stretched
    private Camera.Size _getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {

        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double)h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    /**
     * mCamcorderProfile will be used when recording starts
     * but it is needed for calculating preview size
     */
    private void _initCamcorderProfile() {
        // !!! see _initCamera() - if a device does not support two streams for preview and video
        //   then the size of the encoded video MUST correspond with preview size
        //   otherwise we risk of getting a native crash
        if (Build.VERSION.SDK_INT >= 11) {
            if (CamcorderProfile.hasProfile(mCameraIndex, CamcorderProfile.QUALITY_HIGH))
                mCamcorderProfile = CamcorderProfile.get(mCameraIndex, CamcorderProfile.QUALITY_HIGH);
            else if (CamcorderProfile.hasProfile(mCameraIndex, CamcorderProfile.QUALITY_1080P))
                mCamcorderProfile = CamcorderProfile.get(mCameraIndex, CamcorderProfile.QUALITY_1080P);
            else if (CamcorderProfile.hasProfile(mCameraIndex, CamcorderProfile.QUALITY_720P))
                mCamcorderProfile = CamcorderProfile.get(mCameraIndex, CamcorderProfile.QUALITY_720P);
            else if (CamcorderProfile.hasProfile(mCameraIndex, CamcorderProfile.QUALITY_480P))
                mCamcorderProfile = CamcorderProfile.get(mCameraIndex, CamcorderProfile.QUALITY_480P);
            else if (CamcorderProfile.hasProfile(mCameraIndex, CamcorderProfile.QUALITY_QVGA))
                mCamcorderProfile = CamcorderProfile.get(mCameraIndex, CamcorderProfile.QUALITY_QVGA);
        }
    }

    private Camera.Size _getBestSize(int width, int height, List<Camera.Size> listSizes) {
        Camera.Size result = null;
        long area = width * height;
        long minArea = Long.MAX_VALUE;
        Camera.Size minSize = null;
        for (Camera.Size size : listSizes) {
            long newArea = size.width * size.height;
            if (newArea < minArea) {
                minArea = newArea;
                minSize = size;
            }
            if (newArea <= area) {
                if (result == null) {
                    result = size;
                } else {
                    long resultArea = result.width * result.height;
                    if (newArea > resultArea) {
                        result = size;
                    }
                }
            }
        }
        if (result == null) return minSize;
        return result;
    }

    //////////////  SHOOTING  /////////////////

    public void setOutputImageFile(File file) {
        mOutputImageFile = file;
    }

    /**
     * Immediately stops recording!
     */
    public void takePicture(File file) {
        setOutputImageFile(file);
        takePicture((BitmapCreatedCallback) null);
    }

    /**
     * Immediately stops recording!
     *
     * @param callback
     */
    public void takePicture(final BitmapCreatedCallback callback) {
        BitmapCreatedCallback callbackWrapper = callback;
        final File outputFile = mOutputImageFile;
        if (outputFile != null && !outputFile.exists()) {
            callbackWrapper = new BitmapCreatedCallback() {
                BitmapCreatedCallback outsideCallback = callback;

                @Override
                public void done(Bitmap bmp) {
                    if (bmp != null) {
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(outputFile);
                            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (out != null) try {
                                out.close();
                            } catch (Exception ex) {
                            }
                        }
                    }
                    if (outsideCallback != null)
                        outsideCallback.done(bmp);
                }
            };
        }
        _startTakingPicture(callbackWrapper);
    }

    private void _startTakingPicture(final BitmapCreatedCallback callback) {
        if (callback == null) return;
        if (!mDoingPreview) return;
        if (mCamera == null) return;
        stopRecording();
        if (mShooter != null) return;
        mShooter = new Shooter(callback);
        mShooter.take();
    }

    private void _stopTakingPicture() {
        mShooter = null;
        if (!mDoingPreview) return;
        if (mCamera == null) return;
        try {
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ///////////// RECORDING VIDEO  ////////////

    public void setOutputVideoFile(File file) {
        mOutputVideoFile = file;
    }

    public void startRecording() {
        if (mOutputVideoFile == null) return;
        if (mOutputVideoFile.exists() &&
                !mOutputVideoFile.canWrite())
            return;
        if (mDoingRecord) return;
        mDoingRecord = true;
        _startRecording();
    }

    public void stopRecording() {
        if (!mDoingRecord) return;
        mDoingRecord = false;
        _stopRecording();
    }

    public boolean isRecording() {
        return mDoingRecord;
    }

    private void _startRecording() {
        if (mCamera == null) return;
        if (mRecorder != null) _stopRecording();
        if (!mDoingRecord) return;
        if (!mDoingPreview) return;
        if (mReadySurfaceHolder == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _startRecording();
                }
            });
            return;
        }

        mRecorder = new MediaRecorder();
        mCamera.unlock();
        try {
            mRecorder.setCamera(mCamera);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            // THIS
//            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
//            mRecorder.setProfile(profile);
            // OR THIS
//            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            // OR THIS
            if (mCamcorderProfile != null) {
                mRecorder.setProfile(mCamcorderProfile);
            } else {
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            }
            // OR THIS
//            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
//            mRecorder.setProfile(profile);

            mRecorder.setOutputFile(mOutputVideoFile.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 9)
                mRecorder.setOrientationHint(mRotationAngle);
            if (mVideoSize != null)
                mRecorder.setVideoSize(mVideoSize.width, mVideoSize.height);
            mRecorder.setPreviewDisplay(mReadySurfaceHolder.getSurface());
            mRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            mRecorder.release();
            mRecorder = null;
            mCamera.lock();
            mDoingRecord = false;
        }

        Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
            @Override public void onAutoFocus(boolean success, Camera camera) {
                if (!mDoingRecord) return;
                mRecorder.start();
            }};
        if (mCallVideoAutoFocus) {
            try {
                mCamera.autoFocus(focusCallback);
            } catch (Exception e) {
                mCallVideoAutoFocus = false;
                focusCallback.onAutoFocus(true, mCamera);
            }
        } else {
            focusCallback.onAutoFocus(true, mCamera);
        }
    }

    private void _stopRecording() {
        if (mRecorder == null) return;
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        if (mCamera == null) return;
//        mCamera.lock();
        try {
            mCamera.reconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Samsung sucks here - we have to restart preview
        // https://code.google.com/p/android/issues/detail?id=52734
        // http://stackoverflow.com/questions/15192075/camera-error-100-on-takepicture-only-after-stopping-a-video-recording
        if ("samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            mCamera.stopPreview();
            mCamera.startPreview();
        }
    }

}
