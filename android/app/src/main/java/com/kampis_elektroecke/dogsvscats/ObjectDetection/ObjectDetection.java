package com.kampis_elektroecke.dogsvscats.ObjectDetection;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetection
{
    public interface IObjectDetection
    {
        Long NO_ERROR = 0L;
        Long MODEL_ERROR = 1L;
        Long LABEL_ERROR = 2L;
        Long FILE_ERROR = 4L;

        void onDetectionError(Long ErrorCode);

        void onModelReady();
    }

    private static class Loader extends AsyncTask<String, String, Long>
    {
        private WeakReference<ObjectDetection> _mReference;

        /**
         * Constructor.
         * @param context: Reference to parent object
         */
        Loader(@NonNull ObjectDetection context)
        {
            _mReference = new WeakReference<>(context);
        }

        /**
         * Thread function to load all files.
         * @param stringParams: Path to model and label file
         */
        @Override
        protected Long doInBackground(String... stringParams)
        {
            ObjectDetection Reference = _mReference.get();

            return (Reference._loadModel(stringParams[0]) | Reference._loadLabel(stringParams[1]));
        }

        @Override
        protected void onProgressUpdate(String... stringParams)
        {
        }

        /**
         * The worker publish the results with this function to the main_activity thread if it has finished.
         * @param Result: Error code from `doInBackground`
         */
        @Override
        protected void onPostExecute(@NonNull Long Result)
        {
            ObjectDetection Reference = _mReference.get();

            if(Result.equals(IObjectDetection.NO_ERROR))
            {
                Reference._mOutput = new float[1][Reference._mClassLabels.size()];

                // Perform an empty prediction, because the first result is wrong
                // when the Android NN API is used
                Reference._mTfLite.run(Reference._mInput, Reference._mOutput);

                Reference._mIsModelReady = true;

                Reference._notifyReadyListeners();
            }
            else
            {
                Reference._notifyErrorListeners(Result);
            }
        }
    }

    private ArrayList<String> _mClassLabels;

    private List<IObjectDetection> _mObjectDetectionListener;

    private Interpreter _mTfLite;

    private AssetManager _mAssetManager;

    private ByteBuffer _mInput;

    private Interpreter.Options _mTfliteOptions;

    private float[][] _mOutput;

    private int _mChannels;

    private boolean _mIsModelReady;

    private String _getSettingsString(@NonNull Bundle Data, String Key)
    {
        String Result;

        try
        {
            Result = Data.getString(Key);
        }
        catch(NullPointerException e)
        {
            Result = "";
        }

        return Result;
    }

    /**
     * Trigger all registered onDetectionError callbacks.
     * @param ErrorCode: Error code
     */
    private void _notifyErrorListeners(@NonNull Long ErrorCode)
    {
        for(IObjectDetection Listener: _mObjectDetectionListener)
        {
            Listener.onDetectionError(ErrorCode);
        }
    }

    /**
     * Trigger all registered onModelReady callbacks.
     */
    private void _notifyReadyListeners()
    {
        for(IObjectDetection Listener: _mObjectDetectionListener)
        {
            Listener.onModelReady();
        }
    }

    /**
     * This function loads the TensorFlow Lite model and return a mapped byte buffer with the model data.
     * @param Manager: Asset manager
     * @param Path: Path to model file
     * @return Mapped byte buffer with model data
     * @throws IOException: I/O exception
     */
    private MappedByteBuffer _loadModelFile(@NonNull AssetManager Manager, @NonNull String Path) throws IOException
    {
        AssetFileDescriptor FileDescriptor = Manager.openFd(Path);
        FileInputStream inputStream = new FileInputStream(FileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, FileDescriptor.getStartOffset(), FileDescriptor.getDeclaredLength());
    }

    /**
     * This function loads the TensorFlow model from the device.
     * @param Path: Path to TensorFlow Lite model
     * @return Error code
     */
    private Long _loadModel(@NonNull String Path)
    {
        try
        {
            // Load the model
            _mTfLite = new Interpreter(_loadModelFile(_mAssetManager, Path), _mTfliteOptions);

            return IObjectDetection.NO_ERROR;
        }
        catch(IOException e)
        {
            Log.e("ObjectDetection", "Load model IO exception: " + e.toString());

            return IObjectDetection.MODEL_ERROR;
        }
    }

    /**
     * This function loads the label file from the device
     * @param Path: Path to label file
     * @return Error code
     */
    private Long _loadLabel(@NonNull String Path)
    {
        Long ErrorCode = IObjectDetection.NO_ERROR;

        // Remove all old labels
        _mClassLabels.clear();

        BufferedReader FileReader = null;
        try
        {
            FileReader = new BufferedReader(new InputStreamReader(_mAssetManager.open(Path)));

            String mLine;
            while((mLine = FileReader.readLine()) != null)
            {
                _mClassLabels.add(mLine);
            }
        }
        catch(IOException e)
        {
            Log.e("ObjectDetection", "Unable to open label file!");

            ErrorCode = IObjectDetection.LABEL_ERROR;
        }
        finally
        {
            if(FileReader != null)
            {
                try
                {
                    FileReader.close();

                    ErrorCode = IObjectDetection.NO_ERROR;

                }
                catch (IOException e)
                {
                    Log.e("ObjectDetection", "Unable to close reader!");

                    ErrorCode = IObjectDetection.FILE_ERROR;
                }
            }
        }

        return ErrorCode;
    }

    /**
     * Constructor.
     * @param Manager: Asset manager
     * @param ModelPath: Path to TensorFlow Lite model
     * @param LabelPath: Path to labels as text file
     * @param Settings: Bundle with settings
     */
    public ObjectDetection(@NonNull AssetManager Manager, @NonNull String ModelPath, @NonNull String LabelPath, @NonNull Bundle Settings)
    {
        _mObjectDetectionListener = new ArrayList<>();

        _mIsModelReady = false;
        _mAssetManager = Manager;
        _mClassLabels = new ArrayList<>();

        // Configure TensorFlow Lite
        _mTfliteOptions = new Interpreter.Options();
        _mTfliteOptions.setNumThreads(Integer.parseInt(_getSettingsString(Settings, "Threads")));
        _mTfliteOptions.setUseNNAPI(Settings.getBoolean("UseNNAPI"));

        // Create the input and output buffer
        _mChannels = Integer.parseInt(_getSettingsString(Settings, "ModelInputChannel"));
        _mInput = ByteBuffer.allocateDirect(4 * Integer.parseInt(_getSettingsString(Settings, "ModelInputWidth")) * Integer.parseInt(_getSettingsString(Settings, "ModelInputHeight"))* _mChannels);
        _mInput.order(ByteOrder.nativeOrder());

        new Loader(this).execute(ModelPath, LabelPath);
    }

    /**
     * Add a new listener.
     * @param Listener: Object detection listener
     */
    public void addListener(@NonNull final IObjectDetection Listener)
    {
        _mObjectDetectionListener.add(Listener);
    }

    /**
     * Remove a listener.
     * @param Listener: Object detection listener
     */
    public void removeListener(@NonNull final IObjectDetection Listener)
    {
        _mObjectDetectionListener.remove(Listener);
    }

    /**
     * Check if the model is loaded and ready.
     * @return true if the model is ready to use
     */
    public boolean getIsModelReady()
    {
        return _mIsModelReady;
    }

    /**
     * Get the classification labels as list.
     * @return List with string labels
     */
    public ArrayList<String> getLabel()
    {
        return _mClassLabels;
    }

    /**
     * Classify the given image.
     * @param InputImage: Input image
     * @return List with floating point predictions for each class
     */
    public List<Float> Classify(@NonNull Bitmap InputImage)
    {
        if(_mIsModelReady)
        {
            _mInput = ImagePreprocessing.Bitmap2ByteBuffer(InputImage, _mChannels);

            // Run the prediction
            _mTfLite.run(_mInput, _mOutput);

            List<Float> Result = new ArrayList<>(_mOutput[0].length);
            for(float f : _mOutput[0])
            {
                Result.add(f);
            }

            return Result;
        }

        return new ArrayList<>(0);
    }
}