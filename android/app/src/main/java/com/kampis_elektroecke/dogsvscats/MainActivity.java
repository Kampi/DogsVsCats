package com.kampis_elektroecke.dogsvscats;

import android.Manifest;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;

// ToDo
//  - Beim erneuten Öffnen das letzte aktive Fragment öffnen und nicht das erste
//  - Settings Fragment UI schön machen
//  - Bei Start "Home" Menü öffnen

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
                                                                CameraFragment.ICameraFragment,
                                                                SettingsFragment.ISettings
{
    private final int RQ_CAMERA_CODE = 100;

    private CameraFragment _mCameraFragment;
    private SettingsFragment _mSettingsFragment;
    private AboutFragment _mAboutFragment;

    private Dialog _mNoPermissionDialog;

    private FragmentManager _mFragmentManager;

    private DrawerLayout _mDrawerLayout;

    private Bundle _mSettings;

    /**
     * Set the default settings for the application.
     * @return Bundle with default settings
     */
    private static Bundle _defaultSettings()
    {
        Bundle Settings = new Bundle();

        Settings.putString("CurrentLanguage", "English");
        Settings.putString("CurrentResolution", "1280x960");
        Settings.putString("ModelInputWidth", "227");
        Settings.putString("ModelInputHeight", "227");
        Settings.putString("ModelInputChannel", "3");
        Settings.putString("Threads", "3");
        Settings.putBoolean("UseNNAPI", true);
        Settings.putBoolean("UseAudio", false);

        return Settings;
    }

    /**
     *  Open the permission error dialog.
     */
    private void _showErrorDialog()
    {
        if(_mNoPermissionDialog == null)
        {
            _mNoPermissionDialog = new Dialog(this, R.style.NoPermissionDialog);
        }

        if(!_mNoPermissionDialog.isShowing())
        {
            _mNoPermissionDialog.setContentView(R.layout.dialog_nopermission);
            _mNoPermissionDialog.setTitle("Permission request");
            _mNoPermissionDialog.setCancelable(false);

            Button DialogButton = _mNoPermissionDialog.findViewById(R.id.Button_GetPermission);
            DialogButton.setOnClickListener((View view) ->
                {
                    _mNoPermissionDialog.dismiss();
                    _requestPermissions();
                }
            );

            _mNoPermissionDialog.show();
        }
    }

    /**
     * Request camera permissions.
     */
    private void _requestPermissions()
    {
        requestPermissions(new String[] {Manifest.permission.CAMERA}, RQ_CAMERA_CODE);
    }

    /**
     * Switch the current active fragment.
     * @param New: New fragment
     */
    private void _switchFragment(Fragment New)
    {
        FragmentTransaction _mFragmentTransaction;

        _mFragmentTransaction = _mFragmentManager.beginTransaction();
        _mFragmentTransaction.replace(R.id.CurrentFragment, New);
        _mFragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        _mFragmentTransaction.commit();
    }

    /**
     * On create callback.
     * @param savedInstanceState: Saved instance data bundle
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        _mSettings = new Bundle();

        _mFragmentManager = getFragmentManager();

        // Create the fragments
        _mCameraFragment = new CameraFragment();

        // Apply the default settings to the camera view
        _mSettings = _defaultSettings();

        _mCameraFragment.setArguments(_mSettings);
        _mCameraFragment.addListener(this);
        _mSettingsFragment = new SettingsFragment();
        _mSettingsFragment.addListener(this);
        _mAboutFragment = new AboutFragment();

        // Draw the toolbar
        Toolbar Bar = findViewById(R.id.Toolbar);
        setSupportActionBar(Bar);

        _mDrawerLayout = findViewById(R.id.DrawerLayout);

        // Draw the menu button
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, _mDrawerLayout, Bar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        _mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Setup the navigation view
        NavigationView NavView = findViewById(R.id.NavigationViewMain);
        NavView.setNavigationItemSelectedListener(this);

        // Select the first item at startup
        NavView.getMenu().getItem(0).setChecked(true);
        onNavigationItemSelected(NavView.getMenu().getItem(0));
    }

    /**
     * On resume callback.
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        // Check if the user gives camera permissions
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            // Check if the user was asked before and the user denied the needed permissions
            if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
            {
                _showErrorDialog();
            }
            // User has not denied the needed permissions
            else
            {
                _requestPermissions();
            }
        }
        // Permission granted. Load camera frame
        else
        {
            Log.d("AAA", "onResume");
        }
    }

    /**
     * On destroy callback.
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        _mCameraFragment = null;
        _mSettingsFragment = null;
        _mAboutFragment = null;
    }

    /**
     * On request permission result callback.
     * @param requestCode: Request code
     * @param permissions: Requested permissions
     * @param grantResults:
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if(requestCode == RQ_CAMERA_CODE)
        {
            // Permissions granted. Load camera frame
            if((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
            {
                Log.d("AAA", "onRequestPermissionsResult");
            }
            else
            {
                _showErrorDialog();
            }
        }
    }

    /**
     * Error callback.
     * @param ErrorCode: Error code
     */
    @Override
    public void onError(Long ErrorCode)
    {
        String Message;

        if(ErrorCode.equals(CameraFragment.ICameraFragment.NO_ERROR))
        {
            Message = "Model loaded";
        }
        else
        {
            Message = "Error: " + ErrorCode;
        }

        Toast.makeText(getApplicationContext(), Message, Toast.LENGTH_LONG).show();
    }

    /**
     *
     */
    @Override
    public void onBackPressed()
    {
        if(_mDrawerLayout.isDrawerOpen(GravityCompat.START))
        {
            _mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        else
        {
            super.onBackPressed();
        }
    }

    /**
     *
     * @param item: Menu item
     * @return
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item)
    {
        switch(item.getItemId())
        {
            case R.id.nav_home:
            {
                _mCameraFragment.setArguments(_mSettings);
                _switchFragment(_mCameraFragment);

                break;
            }
            case R.id.nav_settings:
            {
                // Copy all available camera resolutions to the settings fragment
                _mSettings.putStringArrayList("Resolutions", _mCameraFragment.getCameraSizes());

                // Get all supported languages
                _mSettings.putStringArrayList("Languages", _mCameraFragment.getLanguages());
                _mSettingsFragment.setArguments(_mSettings);

                _switchFragment(_mSettingsFragment);

                break;
            }
            case R.id.nav_about:
            {
                _switchFragment(_mAboutFragment);

                break;
            }
        }

        _mDrawerLayout.closeDrawer(GravityCompat.START);

        return true;
    }

    /**
     * Get the data from the settings fragment.
     * @param Data: Data bundle with settings
     */
    @Override
    public void getData(Bundle Data)
    {
        _mSettings = Data;
    }
}