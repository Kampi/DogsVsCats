package com.kampis_elektroecke.dogsvscats;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AboutFragment extends Fragment
{
    private TextView _mTextViewInfo;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        _mTextViewInfo = _getView().findViewById(R.id.TextViewInfo);
        _mTextViewInfo.setMovementMethod(ScrollingMovementMethod.getInstance());

        FloatingActionButton MailButton = _getView().findViewById(R.id.ButtonMail);
        MailButton.setOnClickListener((view) ->
        {
            Intent Email = new Intent(Intent.ACTION_SEND);
            Email.setType("text/email");
            Email.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.email)});
            startActivity(Intent.createChooser(Email, "Send Feedback:"));
        });

        BufferedReader Reader = null;

        try
        {
            Reader = new BufferedReader(new InputStreamReader(getActivity().getAssets().open("Info.txt")));

            String Line;
            while((Line = Reader.readLine()) != null)
            {
                _mTextViewInfo.append(Line);
            }
        }
        catch(IOException e)
        {
            _mTextViewInfo.setText(getString(R.string.no_info));

            Log.d("AboutFragment", "No 'Info.txt' file!");
        }
        finally
        {
            if(Reader != null)
            {
                try
                {
                    Reader.close();
                }
                catch(IOException e)
                {
                    Log.d("AboutFragment", "Unable to close reader!");
                }
            }
        }
    }
}