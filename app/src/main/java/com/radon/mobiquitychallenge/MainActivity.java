package com.radon.mobiquitychallenge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileInfo;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

/**
 * MainActivity connects to dropbox and lists all files inside the directory.
 * Displays a listview of the files.
 * Also has a button that launches the camera to add photos to the directory.
 *
 * This activity is locked in portrait mode because returning from the camera activity in landscape
 * loses the photo Uri and crashes not only the app, but my entire phone. All other activities can be rotated.
 * Locking the orientation programmatically could be a better option if this activity needed to support both
 * however for the scope of this project, I just added it to the manifest.
 *
 * First experience with DROPBOX SYNC API. Pretty cool stuff.
 */

public class MainActivity extends Activity {

    //Dropbox Sync API Credentials
    private static final String appKey = "fioblh0mvhjxrrm";
    private static final String appSecret = "2cqv659wo5wa942";

    //Activity result constants
    private static final int REQUEST_LINK_TO_DBX = 0;
    private static final int CAMERA_REQUEST = 1888;

    //Path where full size image is saved after camera takes a photo
    Uri mImageUri;

    //Variables
    private TextView mTestOutput;
    private Button mLinkButton;
    private Button cameraButton;
    private DbxAccountManager mDbxAcctMgr;
    ProgressDialog loadWheel;

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> files = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTestOutput = (TextView) findViewById(R.id.test_output);

        listView = (ListView) findViewById(R.id.file_list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, files);
        listView.setAdapter(adapter);

        //Initialize the progress dialog that shows when a photo is being retrieved from external storage
        //and then uploaded to DropBox
        loadWheel = new ProgressDialog(this);
        loadWheel.setMessage("Please wait...");
        loadWheel.setTitle("Uploading");
        loadWheel.setIndeterminate(true);

        //When a contact is clicked, launch new Activity
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                //When a list item is tapped, launch a new activity that shows the photo
                Intent intent = new Intent(MainActivity.this, PhotoActivity.class);

                //Send the photo path to the new activity so it can look it up from Dropbox
                intent.putExtra("path", adapter.getItem(position));
                MainActivity.this.startActivity(intent);
            }
        });

        //Link button incase sync to Dropbox fails
        //Should remain hidden from user in most cases
        mLinkButton = (Button) findViewById(R.id.link_button);
        mLinkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickLinkToDropbox();
            }
        });

        //Button that launches the camera activity
        cameraButton = (Button) findViewById(R.id.camera_button);
        cameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                //Create intent and place to store picture that will be taken
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                File photo;
                try
                {
                    // place where to store camera taken picture
                    photo = createTemporaryFile("picture", ".jpg");
                    photo.delete();
                }
                catch(Exception e)
                {
                    Log.e("CAMERA:", "Can't create file to take picture!");
                    Toast.makeText(getParent(), "Please check SD card! Image shot is impossible!", Toast.LENGTH_SHORT).show();
                    return;
                }
                mImageUri = Uri.fromFile(photo);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
        });

        //Initialize the Dropbox account manager using credentials
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), appKey, appSecret);
    }

    //helper method to create a temporary file where photo is stored
    private File createTemporaryFile(String part, String ext) throws Exception
    {
        File tempDir= Environment.getExternalStorageDirectory();
        tempDir=new File(tempDir.getAbsolutePath()+"/.temp/");
        if(!tempDir.exists())
        {
            tempDir.mkdir();
        }
        return File.createTempFile(part, ext, tempDir);
    }

    //Helper method that retrieves photo after it is taken and stored
    public Bitmap grabImage()
    {
        this.getContentResolver().notifyChange(mImageUri, null);
        ContentResolver cr = this.getContentResolver();
        Bitmap bitmap = null;
        try
        {
            //This line below takes awhile. Therefore this method is called from an AsyncTask
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, mImageUri);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
            Log.d("FAILED: ", "Failed to load", e);
        }
        return bitmap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDbxAcctMgr.hasLinkedAccount()) {
            showLinkedView();
            doDropboxTest();
        } else {
            showUnlinkedView();
        }
    }

    private void showLinkedView() {
        mLinkButton.setVisibility(View.GONE);
        mTestOutput.setVisibility(View.VISIBLE);
    }

    private void showUnlinkedView() {
        mLinkButton.setVisibility(View.VISIBLE);
        mTestOutput.setVisibility(View.GONE);
    }

    private void onClickLinkToDropbox() {
        mDbxAcctMgr.startLink((Activity) this, REQUEST_LINK_TO_DBX);
    }

    /**
     * This method handles what happens when another activity finishes (Camera activity and Linking to dropbox)
     *
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LINK_TO_DBX) {
            if (resultCode == Activity.RESULT_OK) {
                doDropboxTest();
            } else {
                mTestOutput.setText("Link to Dropbox failed or was cancelled.");
            }
        }else if(requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            //When camera is done taking a photo, call asyncTask to retrieve it from storage
            //Accessing storage is slow.
            new grabImageTask().execute();
        }else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    //This method was taken from "Hello Dropbox" sample project and modified to fit my needs.
    //This lists the contents of the Dropbox folder the app uses.
    private void doDropboxTest() {
        mTestOutput.setText("Dropbox Sync API Version "+DbxAccountManager.SDK_VERSION_NAME+"\n");
        try {
            // Create DbxFileSystem for synchronized file access.
            DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());

            // Print the contents of the root folder.  This will block until we can
            // sync metadata the first time.
            List<DbxFileInfo> infos = dbxFs.listFolder(DbxPath.ROOT);
            mTestOutput.append("\nContents of app folder:\n");
            files.clear();
            for (DbxFileInfo info : infos) {
                //get the file names and remove the prefix slash(/)
                files.add(info.path.toString().substring(1));
            }

            //Update listView adapter
            adapter.notifyDataSetChanged();


        } catch (IOException e) {
            Log.e("Error:", e.toString());
        }
    }

    //Uploades the photo to Dropbox folder
    private void uploadPhoto(Bitmap bitmap){
        try {
            //Default display images in portrait
            if(bitmap.getWidth() > bitmap.getHeight()) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            //Create a unique file name with a timestamp
            Long tsLong = System.currentTimeMillis()/1000;
            String ts = tsLong.toString();
            final String TEST_FILE_NAME = ts + ".png";

            //create a file to write bitmap data
            File photoFile = new File(this.getCacheDir(), TEST_FILE_NAME);
            Log.e("photoFile:", photoFile.toString());

            //Convert Bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            //write the bytes in file
            FileOutputStream fos = new FileOutputStream(photoFile);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();

            //Dropbox path
            DbxPath testPath = new DbxPath(DbxPath.ROOT, TEST_FILE_NAME);

            // Create DbxFileSystem for synchronized file access.
            DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());

            // Create a test file only if it doesn't already exist.
            if (!dbxFs.exists(testPath)) {
                DbxFile testFile = dbxFs.create(testPath);
                try {
                    testFile.writeFromExistingFile(photoFile,false);

                } finally {
                    testFile.close();
                }
                mTestOutput.append("\nCreated new file '" + testPath + "'.\n");

                //Add new photo to the listview
                List<DbxFileInfo> infos = dbxFs.listFolder(DbxPath.ROOT);
                String withSlash = infos.get(infos.size()-1).path.toString();
                //Get rid of pesky slash.
                String noSlash = withSlash.substring(1);
                files.add(noSlash);
                Log.e("ADDED FILE: ", noSlash);

                //Update listview adapter
                adapter.notifyDataSetChanged();
            }

        } catch (IOException e) {
            mTestOutput.setText("Upload failed: " + e);
        } finally {
            //Dismiss the progress dialog once photo is uploaded
            loadWheel.dismiss();
        }


    }

    //AsyncTask that handles retrieving photos from storage
    public class grabImageTask extends AsyncTask<Void, Void, Bitmap>{

        @Override
        protected Bitmap doInBackground(Void... params) {
            return grabImage();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //Show the progress dialog
            loadWheel.show();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //Once photo is retrieved, upload it to dropbox
            uploadPhoto(bitmap);
        }
    }

}
