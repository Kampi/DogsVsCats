package com.kampis_elektroecke.dogsvscats.CameraView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kampis_elektroecke.dogsvscats.R;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CameraView extends LinearLayout
{
    public interface ICameraView
    {
        Long NO_ERROR = 0L;
        Long CAMERA_ERROR = 1L;
        Long CAPTURE_ERROR = 2L;
        Long NO_CAMERA_FOUND = 4L;
        Long WRONG_IMAGE_FORMAT = 8L;

        void onCameraError(Long ErrorCode);

        void onBitmapAvailable(Bitmap Image);
    }

    private final int STROKE_WIDTH = 10;

    private List<ICameraView> _mInterfaceListener = new ArrayList<>();

    private String _mCameraID;

    private Size[] _mImageSizes;

    private CameraDevice _mCamera;

    private CameraManager _mCameraManager;

    private CameraCaptureSession _mActiveSession;

    private CaptureRequest.Builder _mCapturePreviewBuilder;
    private CaptureRequest.Builder _mCaptureBuilder;

    private ImageReader _mImageReader;

    private Rect _mROI;

    private FrameLayout _mImageFrame;

    private SurfaceView _mCameraPreview;
    private SurfaceView _mCameraOverlay;

    private SurfaceHolder _mOverlayHolder;
    private SurfaceHolder _mCameraHolder;

    private int _mOverlayWidth;
    private int _mOverlayHeight;

    /**
     * Trigger all registered onCameraError callbacks.
     * @param ErrorCode: Error code
     */
    private void _notifyErrorListener(@NonNull Long ErrorCode)
    {
        for(ICameraView Listener: _mInterfaceListener)
        {
            Listener.onCameraError(ErrorCode);
        }
    }

    /**
     * Trigger all registered onBitmapAvailable callbacks.
     * @param Image: Bitmap image
     */
    private void _notifyOnBitmapAvailable(@NonNull Bitmap Image)
    {
        for(ICameraView Listener: _mInterfaceListener)
        {
            Listener.onBitmapAvailable(Image);
        }
    }

    /**
     * Try to find a device camera.
     */
    private void _findCamera()
    {
        try
        {
            // Loop over each camera
            String[] CameraIDs = _mCameraManager.getCameraIdList();
            for(String CameraID: CameraIDs)
            {
                // Get the data from the current camera
                CameraCharacteristics Characteristics = _mCameraManager.getCameraCharacteristics(CameraID);
                Log.d("CameraFragment", "Camera" + " : " + Characteristics.toString());

                // Get the lens facing direction
                Integer lensFacing = Characteristics.get(CameraCharacteristics.LENS_FACING);
                if((lensFacing != null) && (lensFacing == CameraCharacteristics.LENS_FACING_BACK))
                {
                    _mCameraID = CameraID;

                    // Get all possible image sizes
                    StreamConfigurationMap StreamConfigs = Characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(StreamConfigs != null)
                    {
                        _mImageSizes = StreamConfigs.getOutputSizes(SurfaceHolder.class);
                    }

                    // Abort if one camera was found
                    return;
                }
            }
        }
        catch(CameraAccessException | NullPointerException e)
        {
            Log.e("CameraView", "Camera exception: " + e);

            _notifyErrorListener(ICameraView.CAMERA_ERROR);
        }
    }

    /**
     * Create a new capture session.
     */
    private void _createCaptureSession()
    {
        List<Surface> ImageOutputs = new ArrayList<>();

        // Image outputs for the camera
        ImageOutputs.add(_mCameraHolder.getSurface());
        ImageOutputs.add(_mImageReader.getSurface());

        try
        {
            // Create the image builder
            _mCapturePreviewBuilder = _mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            _mCapturePreviewBuilder.addTarget(_mCameraHolder.getSurface());
            _mCaptureBuilder = _mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            _mCaptureBuilder.addTarget(_mImageReader.getSurface());

            // Create the camera capture session
            _mCamera.createCaptureSession(ImageOutputs, _captureSessionCallback, new Handler());
        }
        catch(Exception e)
        {
            Log.e("CameraView", "Capture exception: " + e);

            _notifyErrorListener(ICameraView.CAPTURE_ERROR);
        }
    }

    /**
     * Draw the overlay for the camera view.
     * @param OverlayWidth: Width of the overlay
     * @param OverlayHeight: Height of the overlay
     */
    private void _drawOverlay(int OverlayWidth, int OverlayHeight)
    {
        // Get a canvas from the surface holder
        Canvas DrawingCanvas = _mOverlayHolder.lockCanvas();

        if(DrawingCanvas != null)
        {
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(STROKE_WIDTH);
            paint.setColor(Color.rgb(255, 0, 0));

            int Left = (_mCameraPreview.getWidth() / 2) - (OverlayWidth / 2);
            int Top = (_mCameraPreview.getHeight() / 2) - (OverlayHeight / 2);
            _mROI = new Rect(Left,
                    Top,
                    Left + (2 * STROKE_WIDTH) + OverlayWidth,
                    Top + (2 * STROKE_WIDTH) + OverlayHeight
            );

            DrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            DrawingCanvas.drawRect(_mROI, paint);

            // Unlock the canvas and draw the result
            _mOverlayHolder.unlockCanvasAndPost(DrawingCanvas);
        }
    }

    /**
     * Initialize the camera view.
     * @param context: Current context
     */
    private void _init(@NonNull Context context)
    {
        _mCameraManager = context.getSystemService(CameraManager.class);

        View _mRoot = inflate(context, R.layout.view_camera, this);

        _mCameraPreview = _mRoot.findViewById(R.id.CameraPreview);
        _mCameraOverlay = _mRoot.findViewById(R.id.Overlay);
        _mImageFrame = _mRoot.findViewById(R.id.ImageFrame);

        _mOverlayHolder = _mCameraOverlay.getHolder();
        _mOverlayHolder.addCallback(_OverlayCallbacks);
        _mOverlayHolder.setFormat(PixelFormat.TRANSPARENT);
        _mOverlayHolder.setFixedSize(_mCameraOverlay.getWidth(), _mCameraOverlay.getHeight());

        _findCamera();
    }

    /**
     * Constructor.
     * @param context: Current context
     */
    public CameraView(@NonNull Context context)
    {
        super(context);

        _init(context);
    }

    /**
     * Constructor.
     * @param context: Current context
     * @param attrs: Attributes
     */
    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);

        _init(context);
    }

    /**
     * Add a new callback listener.
     * @param Listener: Callback listener
     */
    public void addListener(@NonNull final ICameraView Listener)
    {
        _mInterfaceListener.add(Listener);
    }

    /**
     * Remove a callback listener.
     * @param Listener: Callback listener
     */
    public void removeListener(@NonNull final ICameraView Listener)
    {
        _mInterfaceListener.remove(Listener);
    }

    /**
     * Set the width and the height of the overlay.
     * @param Width: Width of the overlay
     * @param Height: Height of the overlay
     */
    public void setOverlaySize(int Width, int Height)
    {
        _mOverlayWidth = Width;
        _mOverlayHeight = Height;

        if(_mOverlayHolder != null)
        {
            _OverlayCallbacks.surfaceCreated(_mOverlayHolder);
        }
    }

    /**
     * Set the visibility of the camera view.
     * @param Visibility: Visibility
     */
    public void setVisibility(int Visibility)
    {
        _mCameraPreview.setVisibility(Visibility);
        _mCameraOverlay.setVisibility(Visibility);
    }

    /**
     * Get the available sizes for the camera device.
     * @return Array with camera sizes
     */
    public Size[] getCameraSizes()
    {
        return _mImageSizes;
    }

    /**
     * Starts the chosen camera.
     * @param ImageSize: Size for the image
     */
    public void startCamera(Size ImageSize)
    {
        _mCameraHolder = _mCameraPreview.getHolder();
        _mCameraHolder.addCallback(_CameraViewCallbacks);

        _mImageFrame.setLayoutParams(new LayoutParams(ImageSize.getWidth(), ImageSize.getHeight()));
        _mOverlayHolder.setFixedSize(ImageSize.getWidth(), ImageSize.getHeight());

        // Get the camera and the possible image sizes
        if((_mCameraID == null) || (_mImageSizes == null))
        {
            Log.e("CameraView", "No camera found!");

            _notifyErrorListener(ICameraView.NO_CAMERA_FOUND);
        }
        else
        {
            // Get the display metrics
            DisplayMetrics Metrics = getResources().getDisplayMetrics();

            if((ImageSize.getWidth() > Metrics.widthPixels) || (ImageSize.getHeight() > Metrics.heightPixels))
            {
                Log.e("CameraView", "Image has the wrong size!");

                _notifyErrorListener(ICameraView.WRONG_IMAGE_FORMAT);
            }

            _mImageReader = ImageReader.newInstance(ImageSize.getWidth(), ImageSize.getHeight(), ImageFormat.JPEG, 2);
            _mImageReader.setOnImageAvailableListener(_onImageAvailable, null);

            _mCameraPreview.setOnClickListener(_onClick);
        }

        // Make the surface views visible
        _mCameraPreview.setVisibility(View.VISIBLE);
    }

    /**
     * Pause the chosen camera.
     */
    public void pauseCamera()
    {
        _mCameraPreview.setVisibility(View.GONE);

        if(_mCamera != null)
        {
            // Close the current session
            if(_mActiveSession != null)
            {
                _mActiveSession.close();
                _mActiveSession = null;
            }

            // Close the camera
            _mCamera.close();
            _mCamera = null;

            // Remove the surface holder callbacks
            if(_mCameraHolder != null)
            {
                _mCameraHolder.removeCallback(_CameraViewCallbacks);
            }
        }
    }

    /**
     * Surface holder callbacks for the overlay surface holder.
     */
    private final SurfaceHolder.Callback _OverlayCallbacks = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder)
        {
            _drawOverlay(_mOverlayWidth, _mOverlayWidth);
            Log.d("CameraView", "Overlay surfaceCreated");
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2)
        {
            Log.d("CameraView", "Overlay surfaceChanged");
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder)
        {
            Log.d("CameraView", "Overlay surfaceDestroyed");
        }
    };

    /**
     * Surface holder callbacks for the camera view surface holder.
     */
    private final SurfaceHolder.Callback _CameraViewCallbacks = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder)
        {
            // Open the camera when the surface is created
            try
            {
                _mCameraManager.openCamera(_mCameraID, _cameraDeviceCallbacks, null);
            }
            catch(SecurityException | CameraAccessException e)
            {
                Log.e("CameraView", "Surface exception: " + e);

                _notifyErrorListener(ICameraView.CAMERA_ERROR);
            }

            Log.d("CameraView", "Preview surfaceCreated");
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2)
        {
            Log.d("CameraView", "Preview surfaceChanged");
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder)
        {
            Log.d("CameraView", "Preview surfaceDestroyed");
        }
    };

    /**
     * Camera device state callbacks.
     */
    private final CameraDevice.StateCallback _cameraDeviceCallbacks = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice)
        {
            _mCamera = cameraDevice;
            _createCaptureSession();

            Log.d("CameraView", "Camera onOpened");
        }

        @Override
        public void onClosed(@NonNull CameraDevice cameraDevice)
        {
            Log.d("CameraView", "Camera onClosed");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice)
        {
            Log.d("CameraView", "Camera onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i)
        {
            Log.d("CameraView", "Camera onError: " + i);
        }
    };

    /**
     * Camera capture session state callbacks.
     */
    private final CameraCaptureSession.StateCallback _captureSessionCallback = new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
        {
            try
            {
                // Create a repeating capture session for the camera preview
                cameraCaptureSession.setRepeatingRequest(_mCapturePreviewBuilder.build(), null, null);

                _mActiveSession = cameraCaptureSession;
            }
            catch(CameraAccessException e)
            {
                Log.e("CameraView", "Camera access exception: " + e);

                _notifyErrorListener(ICameraView.CAPTURE_ERROR);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
        {
            Log.e("CameraView", "Session configuration failed!");

            _notifyErrorListener(ICameraView.CAPTURE_ERROR);
        }
    };

    /**
     * On click callback for the overlay.
     */
    private final OnClickListener _onClick = new OnClickListener()
    {
        @Override
        public void onClick(@NonNull View v)
        {
            try
            {
                _mActiveSession.capture(_mCaptureBuilder.build(), null, new Handler());
            }
            catch(CameraAccessException e)
            {
                Log.e("CameraView", "onClick error: " + e);

                _notifyErrorListener(ICameraView.CAPTURE_ERROR);
            }
        }
    };

    /**
     * Image reader callbacks.
     */
    private final ImageReader.OnImageAvailableListener _onImageAvailable = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(@NonNull ImageReader imageReader)
        {
            Image image = imageReader.acquireLatestImage();

            if(image != null)
            {
                byte[] imageBytes;

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                imageBytes = new byte[buffer.remaining()];
                buffer.get(imageBytes);
                final Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes,0, imageBytes.length);
                image.close();

                Bitmap ROI = Bitmap.createBitmap(bmp, _mROI.left + STROKE_WIDTH,
                        _mROI.top + STROKE_WIDTH,
                        _mROI.right - _mROI.left - (2 * STROKE_WIDTH),
                        _mROI.bottom - _mROI.top - (2 * STROKE_WIDTH)
                );

                _notifyOnBitmapAvailable(ROI);
            }
        }
    };
}