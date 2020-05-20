package com.alsaeedcullivan.ourtrips;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.alsaeedcullivan.ourtrips.cloud.AccessBucket;
import com.alsaeedcullivan.ourtrips.cloud.AccessDB;
import com.alsaeedcullivan.ourtrips.fragments.CustomDialogFragment;
import com.alsaeedcullivan.ourtrips.utils.Const;
import com.alsaeedcullivan.ourtrips.utils.Utilities;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * This activity uses the Android Image Cropper open source library
 * Android Image Cropper is licensed under the Apache License, Version 2.0
 * Android Image Cropper can be found on github at https://github.com/ArthurHub/Android-Image-Cropper
 * The library has been in no way modified, we merely implement it in this activity for photo cropping
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String URI_KEY = "uri_key";

    private FirebaseAuth mAuth;
    private FirebaseUser mUser;

    private ImageView mProfileImageView;
    private EditText mNameEditText, mEmailEditText, mAffiliationEditText, mBirthdayEditText;
    private RadioGroup mInputGender;
    private RadioButton mFemaleRadioButton, mMaleRadioButton, mOtherRadioButton;
    private Button mChangePictureButton;

    private Uri mProfileUri;
    private Drawable mProfilePic;

    private boolean mPermission;
    private boolean mRegistered;

    public String mSourceExtra;   // source activity intent extra
    private String mUserToastMessage = "";  // for toasting based on source activity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // initialize the FireBaseAuth
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();

        // get reference to the ImageView
        mProfileImageView = findViewById(R.id.img_profile);

        // get references to the EditTexts & Radios
        mNameEditText = findViewById(R.id.edit_name);
        mEmailEditText = findViewById(R.id.edit_email);
        mAffiliationEditText = findViewById(R.id.edit_affiliation);
        mBirthdayEditText = findViewById(R.id.edit_birthday);
        mInputGender = findViewById(R.id.input_gender);
        mFemaleRadioButton = findViewById(R.id.edit_gender_female);
        mMaleRadioButton = findViewById(R.id.edit_gender_male);
        mOtherRadioButton = findViewById(R.id.edit_gender_other);

        // get reference to the change picture Button
        mChangePictureButton = findViewById(R.id.change_picture);

        // display the user's email as uneditable
        mEmailEditText.setText(mUser.getEmail());
        mEmailEditText.setEnabled(false);

        // get source activity & load profile is source: MainActivity
        mSourceExtra = getIntent().getStringExtra(Const.SOURCE_TAG);
        if (mSourceExtra != null) {
            if (mSourceExtra.equalsIgnoreCase(Const.MAIN_TAG)) {
                loadProfile();
            }
        }

        // request read/write permissions
        updatePermission();
        requestPermission();
    }

    // handle lifecycle //

    @Override
    protected void onResume() {
        super.onResume();
        // update permissions
        updatePermission();
        // get the user that is signed in or null if there is no user signed in
        mUser = mAuth.getCurrentUser();

        if (mUser != null && mUser.isEmailVerified()) {
            // they are logged in and verified, send them to main activity
            Toast.makeText(this, "got to main", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // save picture uri
        if (mProfileUri != null) outState.putParcelable(URI_KEY, mProfileUri);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // if there is a picture uri, restore it and set the pic
        if (savedInstanceState.getParcelable(URI_KEY) != null) {
            mProfileUri = savedInstanceState.getParcelable(URI_KEY);
            setPic(mProfileUri);
        }
    }

    // handle permissions //

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Const.GALLERY_PERMISSION_REQUEST_CODE) {
            // check permissions status from dialog fragment & Manifest
            if (grantResults.length > 0 && (grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                    grantResults[1] != PackageManager.PERMISSION_GRANTED) &&
                    (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                            shouldShowRequestPermissionRationale(Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE))) {
                // keep change picture button disabled
                mChangePictureButton.setClickable(false);
            } else {
                // enable change picture button
                mChangePictureButton.setClickable(true);
            }
        }
    }

    // handle menu //

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_register, menu);
        // enable back button
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        // check source activity & set-up title & visible menu button accordingly
        if (mSourceExtra != null && mSourceExtra.equals(Const.VERIFY_TAG)) {
            menu.findItem(R.id.register_button).setVisible(true);
            menu.findItem(R.id.update_button).setVisible(false);
            // set up activity title
            setTitle(getString(R.string.title_activity_register));
            // prepare toast message: register
            mUserToastMessage = getString(R.string.toast_registered);
            // user has not previously registered
            mRegistered = false;
        } else if (mSourceExtra != null && mSourceExtra.equals(Const.MAIN_TAG)) {
            menu.findItem(R.id.register_button).setVisible(false);
            menu.findItem(R.id.update_button).setVisible(true);
            // set up activity title
            setTitle(getString(R.string.title_activity_Edit));
            // disable editing email
            mEmailEditText.setEnabled(false);
            // prepare toast message: update profile
            mUserToastMessage = getString(R.string.toast_profileUpdated);
            // user is registered
            mRegistered = true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.register_button:
            case R.id.update_button:
                onRegisterClicked();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    // on click listeners //

    public void onChangeClicked(View view) {
        // the user has given permission
        // select the pic
        selectPic();
    }

    // handles Register & Update Profile clicks
    public void onRegisterClicked() {
        removeErrors();

        // get name, gender, email, affiliation & birthday
        String inputName = mNameEditText.getText().toString();
        String inputEmail = mEmailEditText.getText().toString();
        String inputAffiliation = mAffiliationEditText.getText().toString();
        String inputBirthday = mBirthdayEditText.getText().toString();
        int inputGender = -1;
        if (mMaleRadioButton.isChecked()) inputGender = 1;
        else if (mFemaleRadioButton.isChecked()) inputGender = 0;
        else inputGender = 2;

        // issues -> false --> NOT save & inform user
        boolean goodProfile = true;
        // focus view on incorrect field
        View focusView = null;

        // ensure email validity //
        if (!Utilities.isValidEmail(inputEmail)) {
            mEmailEditText.setError(getString(R.string.invalid_username));
            focusView = mEmailEditText;
            goodProfile = false;
        }

        // ensure name, gender, email & birthday NOT empty
        if (inputName.length() == 0) {
            mEmailEditText.setError(getString(R.string.required_field));
            focusView = mEmailEditText;
            goodProfile = false;
        }
        if (inputEmail.length() == 0) {
            mEmailEditText.setError(getString(R.string.required_field));
            focusView = mEmailEditText;
            goodProfile = false;
        }
        if (inputBirthday.length() == 0) {
            mBirthdayEditText.setError(getString(R.string.required_field));
            focusView = mBirthdayEditText;
            goodProfile = false;
        }
        if (goodProfile && inputGender == -1) {
            Toast message = Toast.makeText(this, R.string.required_gender,
                    Toast.LENGTH_SHORT);
            message.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
            message.show();
            focusView = mInputGender;
            goodProfile = false;
        }

        // field has error -> focus
        if (focusView != null) focusView.requestFocus();

        // no errors triggered -> save profile
        if (goodProfile) {// save || update profile

            // create || update profile
            if (mRegistered) updateProfile();
            else createUser();

            // toast user: registration || profile update
            Toast message = Toast.makeText(this, mUserToastMessage,
                    Toast.LENGTH_SHORT);
            message.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
            message.show();

            // return to LoginActivity || MainActivity
            finish();
        }
    }

    // handle setting profile pic //

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // if the activity was an image crop
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            // get the result of the Crop
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                // get the Uri of the cropped pic
                if (result != null) mProfileUri = result.getUri();
                setPic(mProfileUri);

            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                // get the error
                Exception error = null;
                if (result != null) error = result.getError();
                if (error != null)
                    Log.d(Const.TAG, "onActivityResult: " + Log.getStackTraceString(error));
            }
        }
    }

    //  ******************************* private helper methods ******************************* //

    // handle picture //

    /**
     * selectPic()
     * allows the user to select a picture from their gallery and crop it
     */
    private void selectPic() {
        // start picker to get the image for cropping and then use the result
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setFixAspectRatio(true)
                .setAspectRatio(1, 1)
                .start(this);
    }

    /**
     * setPic()
     * set a picture on ImageView
     */
    private void setPic(Uri uri) {
        try {
            // open an InputStream from the Uri
            InputStream is = getContentResolver().openInputStream(uri);
            mProfilePic = Drawable.createFromStream(is, uri.toString());
            mProfileImageView.setImageDrawable(mProfilePic);
        } catch (IOException e) {
            Log.d(Const.TAG, Log.getStackTraceString(e));
        }
    }

    // handle permissions //

    // public: accessed from CustomDialogFragment
    public void requestPermission() {
        // request read/write permissions from user if not given
        if (!mPermission) {
            mChangePictureButton.setClickable(false);   // disable change picture button
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, Const.GALLERY_PERMISSION_REQUEST_CODE);
        } else {
            // make sure change picture button is enabled
            mChangePictureButton.setClickable(true);
        }
    }

    private void updatePermission() {
        // update read & write permissions
        mPermission = (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED);
    }

    // handle dialog fragment //

    private void createImportantDialogFragment() {
        // display dialog fragment
        DialogFragment dialog = CustomDialogFragment.newInstance(CustomDialogFragment
                .PERMISSION_IMPORTANT_ID);
        dialog.show(getSupportFragmentManager(), Const.DIALOG_TAG);
    }

    // handle profile tasks //

    /**
     * removeErrors()
     * method to remover errors from required text widgets: name, email & birthday
     */
    private void removeErrors() {
        mNameEditText.setError(null);
        mEmailEditText.setError(null);
        mBirthdayEditText.setError(null);
    }

    /**
     * createUser()
     * method to add this user to FireStore
     */
    private void createUser() {
        // create a map with the user's information
        Map<String, Object> data = new HashMap<>();
        data.put(Const.USER_ID_KEY, mUser.getUid());
        data.put(Const.USER_EMAIL_KEY, mUser.getEmail());
        data.put(Const.USER_NAME_KEY, mNameEditText.getText().toString());
        data.put(Const.USER_BIRTHDAY_KEY, mBirthdayEditText.getText().toString());
        String gender;
        int checked = mInputGender.getCheckedRadioButtonId();
        if (checked == R.id.edit_gender_female) gender = "Female";
        else if (checked == R.id.edit_gender_male) gender = "Male";
        else gender = "Other";
        data.put(Const.USER_GENDER_KEY, gender);
        data.put(Const.USER_AFFILIATION_KEY, mAffiliationEditText.getText().toString());
        data.put(Const.DATE_LIST_KEY, new ArrayList<String>());

        // if they have set a profile pic
        if (mProfileUri != null) {
            // establish the path where the profile pic will be stored in the bucket
            String path = Const.PROFILE_PIC_PATH + "/" + mUser.getUid() + "/" + Const.PROFILE_PIC_NAME;
            try {
                // open an input stream from the photo Uri and upload to the bucket
                InputStream is = getContentResolver().openInputStream(mProfileUri);
                AccessBucket.uploadPicture(path, is);

                // add the storage path to data
                data.put(Const.USER_PROFILE_PIC_KEY, path);

            } catch (IOException e) {
                Log.d(Const.TAG, Log.getStackTraceString(e));
            }
        }

        // add the user's data to the database
        Task<Void> addTask = AccessDB.addNewUser(mUser.getUid(), data);
        addTask.addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Inform the user that they were registered successfully
                    Toast t = Toast.makeText(RegisterActivity.this,
                            R.string.string_register_success, Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                    t.show();

                    // send the user to MainActivity
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.putExtra(Const.MAIN_TAG, Const.REGISTER_TAG);
                    startActivity(intent);
                    finish();
                } else {
                    // inform that their data could not be added
                    Toast t = Toast.makeText(RegisterActivity.this,
                            R.string.string_register_failure, Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                    t.show();
                }
            }
        });
    }

    /**
     * updateProfile()
     * method to update a user's profile info in the FireStore database
     */
    private void updateProfile() {
        // create a map with the new profile info
        Map<String, Object> data = new HashMap<>();
        data.put(Const.USER_NAME_KEY, mNameEditText.getText().toString());
        data.put(Const.USER_BIRTHDAY_KEY, mBirthdayEditText.getText().toString());
        String gender;
        int checked = mInputGender.getCheckedRadioButtonId();
        if (checked == R.id.edit_gender_female) gender = "f";
        else if (checked == R.id.edit_gender_male) gender = "m";
        else gender = "o";
        data.put(Const.USER_GENDER_KEY, gender);
        data.put(Const.USER_AFFILIATION_KEY, mAffiliationEditText.getText().toString());

        // if they have set a profile pic
        if (mProfileUri != null) {
            // establish the path where the profile pic will be stored in the bucket
            String path = Const.PROFILE_PIC_PATH + "/" + mUser.getUid() + "/" + Const.PROFILE_PIC_NAME;
            try {
                // open an input stream from the photo Uri and upload to the bucket
                InputStream is = getContentResolver().openInputStream(mProfileUri);
                AccessBucket.uploadPicture(path, is);

                // add the storage path to data
                data.put(Const.USER_PROFILE_PIC_KEY, path);

            } catch (IOException e) {
                Log.d(Const.TAG, Log.getStackTraceString(e));
            }
        }

        // update the profile in the database
        Task<Void> updateTask = AccessDB.updateUserProfile(mUser.getUid(), data);
        updateTask.addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // inform the user that their profile has been updated
                    Toast t = Toast.makeText(RegisterActivity.this,
                            R.string.string_update_profile, Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                    t.show();
                    // finish the activity
                    finish();
                } else {
                    // inform that their data could not be updated
                    Toast t = Toast.makeText(RegisterActivity.this,
                            R.string.update_profile_fail, Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                    t.show();
                }
            }
        });
    }

    /**
     * loadProfile()
     * loads the profile info of a user from the FireStore database
     */
    private void loadProfile() {
        // load data if it exists
        AccessDB.loadUserProfile(mUser.getUid())
                .addOnCompleteListener(new OnCompleteListener<Map<String, Object>>() {
                    @Override
                    public void onComplete(@NonNull Task<Map<String, Object>> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            populateFields(task.getResult());
                        }
                    }
                });
    }

    /**
     * populateFields()
     * helper method to fill in the widgets with the user's profile info
     * @param data the object containing all the user's profile data
     */
    private void populateFields(Map<String, Object> data) {
        if (data == null) return;

        // set the profile pic
        String path = (String) data.get(Const.USER_PROFILE_PIC_KEY);
        if (path != null && path.length() > 0) {
            Glide.with(this)
                    .load(FirebaseStorage.getInstance().getReference().child(path))
                    .into(mProfileImageView);
        }

        // set the name
        String name = (String) data.get(Const.USER_NAME_KEY);
        if (name != null) mNameEditText.setText(name);

        // set the affiliation
        String aff = (String) data.get(Const.USER_AFFILIATION_KEY);
        if (aff != null) mAffiliationEditText.setText(aff);

        // set the birthday
        String bDay = (String) data.get(Const.USER_BIRTHDAY_KEY);
        if (bDay != null) mBirthdayEditText.setText(bDay);

        // set the gender
        String gender = (String) data.get(Const.USER_GENDER_KEY);
        if (gender != null) {
            switch (gender) {
                case "f":
                    mInputGender.check(R.id.edit_gender_female);
                    break;
                case "m":
                    mInputGender.check(R.id.edit_gender_male);
                    break;
                default:
                    mInputGender.check(R.id.edit_gender_other);
            }
        }
    }
}
