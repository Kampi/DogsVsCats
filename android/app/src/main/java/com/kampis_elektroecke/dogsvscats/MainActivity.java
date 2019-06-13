package com.kampis_elektroecke.dogsvscats;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.res.AssetManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.kampis_elektroecke.dogsvscats.ObjectDetection.*;

public class MainActivity extends AppCompatActivity
{
    private AssetManager _mAssetManager;

    private ObjectDetection _mDetection;
    private List<String> _mLabels;
    private List<Float> _mResults;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _mLabels = new ArrayList<String>();
        _mResults = new ArrayList<Float>();

        _mAssetManager = getAssets();
        _mDetection = new ObjectDetection(_mAssetManager, 32, 32);
        _mLabels = _mDetection.GetLabel();

        //System.loadLibrary("tensorflow_inference");

        ImageView img = findViewById(R.id.Preview);
        TextView Text = findViewById(R.id.textView);

        try
        {
            InputStream Stream = _mAssetManager.open("20_horse.png");
            Bitmap bitmap = BitmapFactory.decodeStream(Stream);
            img.setImageBitmap(bitmap);

            _mResults = _mDetection.Classify(bitmap);

            // Get the highest probability and the index
            int Index = 0;
            float Max = _mResults.get(0);
            for(int i = 1; i < _mResults.size(); i++)
            {
                if (_mResults.get(i) > Max)
                {
                    Max = _mResults.get(i);
                    Index = i;
                }
            }

            Log.d("ObjectDetection", "Index - " + Index);
            Log.d("ObjectDetection", "Probability - " + Max);
            Text.setText(_mLabels.get(Index));

        }
        catch(IOException e)
        {
            Log.d("Main", e.toString());
        }
        finally
        {
        }
    }
}