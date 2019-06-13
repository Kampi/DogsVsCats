package com.kampis_elektroecke.dogsvscats.ObjectDetection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.TensorFlow;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ObjectDetection
{
    private String[] _mOutputName;
    private int _mWidth;
    private int _mHeight;
    private AssetManager _mAssetManager;
    private List<String> _mClassLabels;
    private TensorFlowInferenceInterface _mTensorFlowInterface;

    /**
     *
     * @param Manager
     */
    public ObjectDetection(AssetManager Manager, int Width, int Height)
    {
        _mWidth = Width;
        _mHeight = Height;
        _mAssetManager = Manager;
        _mClassLabels = new ArrayList<String>();
        _mOutputName = new String[]{"output_node0"};
        _mTensorFlowInterface = new TensorFlowInferenceInterface(_mAssetManager, "file:///android_asset/Model.pb");
    }

    public List<String> GetLabel()
    {
        BufferedReader FileReader = null;
        try
        {
            FileReader = new BufferedReader(new InputStreamReader(_mAssetManager.open("Labels.txt")));

            String mLine;
            while((mLine = FileReader.readLine()) != null)
            {
                _mClassLabels.add(mLine);
            }
        }
        catch(IOException e)
        {
            Log.d("ObjectDetection", "Unable to open label file");
        }
        finally
        {
            if(FileReader != null)
            {
                try
                {
                    FileReader.close();
                }
                catch (IOException e)
                {
                    Log.d("ObjectDetection", "Unable to close reader");
                }
            }
        }

        return _mClassLabels;
    }

    public List<Float> Classify(Bitmap InputImage)
    {
        float[] Results = new float[_mClassLabels.size()];
        float[] FloatArray = new float[_mWidth * _mHeight * 3];
        int[] ImageArray = new int[_mWidth * _mHeight];

        InputImage.getPixels(ImageArray,0, InputImage.getWidth(), 0, 0, InputImage.getWidth(), InputImage.getHeight());

        int j = 0;
        for(int i = 0; i < ImageArray.length * 3; i = i + 3)
        {
            final int Pixel = ImageArray[j];

            // Normalize the image
            FloatArray[i + 2] = (((Pixel >> 16) & 0xFF)) / 255.0f;
            FloatArray[i + 1] = (((Pixel >> 8) & 0xFF)) / 255.0f;
            FloatArray[i + 0] = ((Pixel & 0xFF)) / 255.0f;

            j++;
        }

        _mTensorFlowInterface.feed("conv2d_1_input", FloatArray, 1L, _mWidth, _mHeight, 3);
        _mTensorFlowInterface.run(_mOutputName, false);
        _mTensorFlowInterface.fetch(_mOutputName[0], Results);

        // Convert the results into a list
        List<Float> Result = new ArrayList<Float>(Results.length);
        for(float f : Results)
        {
            Result.add(f);
        }

        return Result;
    }
}