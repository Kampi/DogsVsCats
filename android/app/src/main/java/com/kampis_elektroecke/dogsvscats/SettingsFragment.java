package com.kampis_elektroecke.dogsvscats;

import android.app.Fragment;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment
{
    public interface ISettings
    {
        void getData(Bundle Data);
    }

    private List<ISettings> _mInterface = new ArrayList<>();

    private Bundle _mSettings;

    private Spinner _mCameraResolution;
    private Spinner _mLanguage;

    private TextView _mModelInputWidth;
    private TextView _mModelInputHeight;
    private TextView _mModelInputChannels;

    private Switch _mUseNNAPI;
    private Switch _mUseAudio;

    private Spinner _mThreads;

    private ArrayAdapter<String> _mResolutionAdapter;
    private ArrayAdapter<String> _mLanguageAdapter;
    private ArrayAdapter<String> _mThreadsAdapter;

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
     * Trigger all registered getData callbacks.
     * @param Data: Data to transmit
     */
    private void _sendData(Bundle Data)
    {
        for(ISettings Listener: _mInterface)
        {
            Listener.getData(Data);
        }
    }

    /**
     * Fill the views with the settings
     * @param Data: Settings bundle
     */
    private void _setViews(Bundle Data)
    {
        int Index = _mResolutionAdapter.getPosition(Data.getString("CurrentResolution"));
        _mCameraResolution.setSelection(Index);

        Index = _mLanguageAdapter.getPosition(Data.getString("CurrentLanguage"));
        _mLanguage.setSelection(Index);
        _mLanguage.setEnabled(Data.getBoolean("UseAudio"));

        Index = _mThreadsAdapter.getPosition(Data.getString("Threads"));
        _mThreads.setSelection(Index);

        _mModelInputWidth.setText(Data.getString("ModelInputWidth"));
        _mModelInputHeight.setText(Data.getString("ModelInputHeight"));
        _mModelInputChannels.setText(Data.getString("ModelInputChannel"));

        _mUseNNAPI.setChecked(Data.getBoolean("UseNNAPI"));
        _mUseAudio.setChecked(Data.getBoolean("UseAudio"));
    }

    /**
     * Settings fragment on create view callback.
     * @param inflater: Layout inflater
     * @param container: Current view group
     * @param savedInstanceState: Bundle with saved data
     * @return Inflated view
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    /**
     * Settings fragment on activity created callback.
     * @param savedInstanceState: Bundle with saved instance data
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        _mSettings = getArguments();

        /*
         * Fill the "Threads" spinner
         */
        ArrayList<String> Threads = new ArrayList<>();
        for(int i = 1; i < 9; i++)
        {
            Threads.add(Integer.toString(i));
        }

        _mThreadsAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Threads);

        /*
         * Fill the "Camera resolution" spinner
         */
        ArrayList<String> Resolutions = _mSettings.getStringArrayList("Resolutions");
        if(Resolutions != null)
        {
            _mResolutionAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Resolutions);
        }

        /*
         * Fill the "Audio language" spinner
         */
        ArrayList<String> Languages = _mSettings.getStringArrayList("Languages");
        if(Languages != null)
        {
            _mLanguageAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Languages);
        }

        /*
         * "Camera resolutions" spinner
         */
        _mCameraResolution = _getView().findViewById(R.id.SpinnerResolution);
        _mCameraResolution.setAdapter(_mResolutionAdapter);
        _mCameraResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
                                                    {
                                                        @Override
                                                        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
                                                        {
                                                            _mSettings.putString("CurrentResolution", _mCameraResolution.getSelectedItem().toString());
                                                        }

                                                        @Override
                                                        public void onNothingSelected(AdapterView<?> adapterView)
                                                        {
                                                        }
                                                    }
        );

        /*
         * "Audio language" slider
         */
        _mLanguage = _getView().findViewById(R.id.SpinnerLanguages);
        _mLanguage.setAdapter(_mLanguageAdapter);
        _mLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
                                                     {
                                                         @Override
                                                         public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
                                                         {
                                                             _mSettings.putString("CurrentLanguage", _mLanguage.getSelectedItem().toString());
                                                         }

                                                         @Override
                                                         public void onNothingSelected(AdapterView<?> adapterView)
                                                         {
                                                         }
                                                     }
        );

        /*
         * "Input width" text box
         */
        _mModelInputWidth = _getView().findViewById(R.id.TextViewModelWidth);
        _mModelInputWidth.setFocusable(false);

        /*
         * "Input height" text box
         */
        _mModelInputHeight = _getView().findViewById(R.id.TextViewModelHeight);
        _mModelInputHeight.setFocusable(false);

        /*
         * "Input channels" text box
         */
        _mModelInputChannels = _getView().findViewById(R.id.TextViewModelChannels);
        _mModelInputChannels.setFocusable(false);

        /*
         * "Use NNAPI" checkbox
         */
        _mUseNNAPI = _getView().findViewById(R.id.SwitchUseNNAPI);
        _mUseNNAPI.setOnCheckedChangeListener((view, isChecked) ->
                _mSettings.putBoolean("UseNNAPI", _mUseNNAPI.isChecked())
        );

        /*
         * "Use Audio" checkbox
         */
        _mUseAudio = _getView().findViewById(R.id.SwitchUseAudio);
        _mUseAudio.setOnCheckedChangeListener((view, isChecked) ->
                {
                    _mSettings.putBoolean("UseAudio", _mUseAudio.isChecked());
                    _mLanguage.setEnabled(_mUseAudio.isChecked());
                }
        );

        /*
         * "Threads" spinner
         */
        _mThreads = _getView().findViewById(R.id.SpinnerThreads);
        _mThreads.setAdapter(_mThreadsAdapter);
        _mThreads.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
                                                     {
                                                         @Override
                                                         public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
                                                         {
                                                             _mSettings.putString("Threads", _mThreads.getSelectedItem().toString());
                                                         }

                                                         @Override
                                                         public void onNothingSelected(AdapterView<?> adapterView)
                                                         {
                                                         }
                                                     }

        );
    }

    /**
     * Settings fragment on start callback.
     */
    @Override
    public void onStart()
    {
        super.onStart();

        // Get the data from the parent
        _mSettings = getArguments();

        // Set the views according to the UI elements
        _setViews(_mSettings);
    }

    /**
     * Settings fragment on pause callback.
     */
    @Override
    public void onPause()
    {
        super.onPause();

        _sendData(_mSettings);
    }

    /**
     * Settings fragment on destroy callback.
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        _mSettings = null;
    }

    /**
     * Add a new callback listener.
     * @param Listener: Callback listener
     */
    public void addListener(@NonNull final ISettings Listener)
    {
        _mInterface.add(Listener);
    }

    /**
     * Remove a callback listener.
     * @param Listener: Callback listener
     */
    public void removeListener(@NonNull final ISettings Listener)
    {
        _mInterface.remove(Listener);
    }
}