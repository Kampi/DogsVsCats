package com.kampis_elektroecke.dogsvscats;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kampis_elektroecke.dogsvscats.CameraView.CameraView;
import com.kampis_elektroecke.dogsvscats.ObjectDetection.ObjectDetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CameraFragment extends Fragment implements CameraView.ICameraView, ObjectDetection.IObjectDetection
{
    public interface ICameraFragment
    {
        Long NO_ERROR = 0L;

        void onError(Long ErrorCode);
    }

    private ArrayList<String> _mCameraSizes;
    private ArrayList<String> _mLabels;
    private ArrayList<String> _mLanguages;

    private List<ICameraFragment> _mInterfaceListener = new ArrayList<>();

    private Long _mCameraError = CameraView.ICameraView.NO_ERROR;
    private Long _mDetectionError = ObjectDetection.IObjectDetection.NO_ERROR;

    private CameraView _mCameraView;

    private TableLayout _mResultTable;

    private ObjectDetection _mDetection;

    private Bundle _mSettings;

    private TextToSpeech _mTTS;

    private int _mModelInputHeight;
    private int _mModelInputWidth;

    /**
     *  Small method to get the view and avoid the warning
     *  "Method invocation 'findViewById' may produce 'NullPointerException"
     *  from Android Studio
     * @return View
     */
    private View _getView()
    {
        View view = getView();
        assert(view != null);

        return view;
    }

    /**
     * Trigger all registered onError callbacks.
     */
    private void _notifyErrorListeners()
    {
        for(ICameraFragment Listener: _mInterfaceListener)
        {
            Listener.onError(_mCameraError | _mDetectionError);
        }
    }

    /**
     * Camera fragment on create view callback.
     * @param inflater: Layout inflater
     * @param container: Current view group
     * @param savedInstanceState: Bundle with saved data
     * @return Inflated view
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        _mTTS = null;

        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    /**
     * Camera fragment on activity created callback.
     * @param savedInstanceState: Bundle with saved instance data
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        _mSettings = getArguments();

        _mResultTable = _getView().findViewById(R.id.TableResults);

        _mModelInputWidth = Integer.parseInt(_mSettings.getString("ModelInputWidth"));
        _mModelInputHeight = Integer.parseInt(_mSettings.getString("ModelInputHeight"));

        _mCameraView = _getView().findViewById(R.id.CameraView);
        _mCameraView.addListener(this);

        // Load the TensorFlow model
        _mDetection = new ObjectDetection(getActivity().getAssets(),
                                "Model.tflite",
                                "Label.txt",
                                _mSettings
                                );
        _mDetection.addListener(this);

        _mLabels = new ArrayList<>();
    }

    /**
     * Camera fragment on resume callback.
     */
    @Override
    public void onResume()
    {
        super.onResume();

        _mSettings = getArguments();

        _mCameraView.startCamera(Size.parseSize(_mSettings.getString("CurrentResolution")));
        _mCameraView.setOverlaySize(_mModelInputWidth, _mModelInputHeight);

        _mTTS = new TextToSpeech(getActivity().getApplicationContext(), (int Status) ->
        {
            if(Status != TextToSpeech.ERROR)
            {
                _mTTS.setLanguage(Locale.UK);
            }
        });
    }

    /**
     * Camera fragment on pause callback.
     */
    @Override
    public void onPause()
    {
        super.onPause();

        if(_mTTS != null)
        {
            _mTTS.stop();
            _mTTS.shutdown();
        }

        _mCameraView.pauseCamera();
    }

    /**
     * Camera fragment on destroy callback.
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if(_mTTS != null)
        {
            _mTTS.stop();
            _mTTS.shutdown();
        }
    }

    /**
     * On object detection error.
     * @param ErrorCode: Object detection error code
     */
    @Override
    public void onDetectionError(@NonNull Long ErrorCode)
    {
        _mDetectionError = ErrorCode;

        _notifyErrorListeners();
    }

    /**
     * On camera error callback.
     * @param ErrorCode: Camera error code
     */
    @Override
    public void onCameraError(@NonNull Long ErrorCode)
    {
        _mCameraError = ErrorCode;

        _notifyErrorListeners();
    }

    /**
     * On model ready callback.
     */
    @Override
    public void onModelReady()
    {
        // Get the label
        _mLabels = _mDetection.getLabel();

        Toast.makeText(getActivity().getApplicationContext(), "Model loaded", Toast.LENGTH_LONG).show();
    }

    /**
     * On bitmal available callback.
     * @param Image: Image as bitmap
     */
    @Override
    public void onBitmapAvailable(@NonNull Bitmap Image)
    {
        List<Float> Results = _mDetection.Classify(Image);

        if(Results.size() > 0)
        {
            // Clear the table
           _mResultTable.removeAllViews();

            for(int i = 0; i < Results.size(); i++)
            {
                // Create a new table row
                TableRow tr = new TableRow(getContext());

                // Create a new TextView
                TextView Text = new TextView(getContext());
                Text.setText(getString(R.string.resultlabel, _mLabels.get(i), Results.get(i)));

                // Add the TextView to the table row
                tr.addView(Text);

                // Add the table row to the table
                _mResultTable.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
            }

            if(_mSettings.getBoolean("UseAudio"))
            {
                Locale Language;
                String Key = _mSettings.getString("CurrentLanguage");
                if(Key != null)
                {
                    Language = new Locale.Builder().setLanguageTag(Key).build();
                }
                else
                {
                    Language = Locale.forLanguageTag("English");
                }

                _mTTS.setLanguage(Language);
                _mTTS.speak(_mLabels.get(Results.indexOf(Collections.max(Results))), TextToSpeech.QUEUE_FLUSH, null, Long.toString(System.currentTimeMillis()));
            }
        }
    }

    /**
     * Add a new callback listener.
     * @param Listener: Callback listener
     */
    public void addListener(@NonNull final ICameraFragment Listener)
    {
        _mInterfaceListener.add(Listener);
    }

    /**
     * Remove a callback listener.
     * @param Listener: Callback listener
     */
    public void removeListener(@NonNull final ICameraFragment Listener)
    {
        _mInterfaceListener.remove(Listener);
    }

    /**
     * Get the available sizes for the camera device according to the model input sizes.
     * @return Array with camera sizes
     */
    public ArrayList<String> getCameraSizes()
    {
        if(_mCameraSizes == null)
        {
            _mCameraSizes = new ArrayList<>();

            for(Size Resolution : _mCameraView.getCameraSizes())
            {
                if((_mModelInputWidth <= Resolution.getWidth()) && (_mModelInputHeight <= Resolution.getHeight()))
                {
                    _mCameraSizes.add(Resolution.toString());
                }
            }
        }

        return _mCameraSizes;
    }

    /**
     * Get the supported languages for the Text to Speech module.
     * @return Hashtable with strings and locales
     */
    public ArrayList<String> getLanguages()
    {
        if(_mLanguages == null)
        {
            _mLanguages = new ArrayList<>();

            for(String lang : Locale.getISOLanguages())
            {
                Locale Loc = new Locale(lang);
                switch (_mTTS.isLanguageAvailable(Loc))
                {
                    case TextToSpeech.LANG_MISSING_DATA:
                    case TextToSpeech.LANG_NOT_SUPPORTED:
                    {
                        break;
                    }
                    default:
                    {
                        String Key = Loc.getDisplayLanguage();
                        if(!_mLanguages.contains(Key))
                        {
                            _mLanguages.add(Key);
                        }

                        break;
                    }
                }
            }
        }

        return _mLanguages;
    }
}