package com.radon.mobiquitychallenge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This Activity displays the photo that was selected from the listview
 * and allows the user to edit the photo and then overwrite it to the dropbox folder.
 * It also allows the user to delete a file in the folder.
 */
public class PhotoActivity extends Activity {

    //Dropbox creds.
    private static final String appKey = "fioblh0mvhjxrrm";
    private static final String appSecret = "2cqv659wo5wa942";

    Context context = this;

    //Variables
    String path;
    TextView photoTitle;
    ImageView imageView;
    Bitmap original;
    boolean isGray = false;
    private DbxAccountManager mDbxAcctMgr;
    DbxFileSystem dbxFs = null;

    //Buttons
    Button delete;
    Button grayscale;
    Button save;
    Button rotate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), appKey, appSecret);

        try {
            dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
        }catch(DbxException e){
            e.printStackTrace();
        }

        //Get data that was passed from parent activity
        Intent intent = getIntent();
        path = intent.getStringExtra("path");

        //Set the title
        photoTitle = (TextView) findViewById(R.id.photo_title);
        photoTitle.setText(path);

        imageView = (ImageView) findViewById(R.id.photo);

        //Get the bitmap from dropbox using the file path
        readInPhoto(path);

        //Delete button
        delete = (Button) findViewById(R.id.delete_button);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(dbxFs != null){
                    DbxPath dbxPath = new DbxPath(DbxPath.ROOT, path);
                    try {
                        //Delete the photo and then close the activity
                        dbxFs.delete(dbxPath);
                        ((PhotoActivity)context).finish();
                    }catch (DbxException e){
                        e.printStackTrace();
                    }
                }
            }
        });

        //GrayScale button
        grayscale = (Button) findViewById(R.id.grayscale);
        grayscale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Allow user to toggle between original image and a black and white version.
                if(!isGray) {
                    Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                    bitmap = toGrayscale(bitmap);
                    imageView.setImageBitmap(bitmap);
                    isGray = true;
                }else{
                    imageView.setImageBitmap(original);
                    isGray = false;
                }
            }
        });

        //Save button
        save = (Button) findViewById(R.id.save_button);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //The state of the image in the imageView will overwrite the original file
                //Saving takes time, but code in AsyncTask
                new savePhotoTask().execute();
                //Tell user that photo is saving
                Toast.makeText(context,"Saving...", Toast.LENGTH_SHORT).show();
            }
        });

        //Rotate button
        rotate = (Button) findViewById(R.id.rotate);
        rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Rotate photo 90 degrees every button tap.
                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    /**
     * Ask user if they are sure they want to exit activity, in case they forgot to save changes.
     */
    @Override
    public void onBackPressed() {
        // 1. Instantiate an AlertDialog.Builder with its constructor
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // 2. Chain together various setter methods to set the dialog characteristics
        builder.setMessage("All unsaved changes will be lost.")
                .setTitle("Go Back?");

        // Add the buttons
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                PhotoActivity.super.onBackPressed();
                dialog.dismiss();
                dialog.cancel();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                dialog.dismiss();
                dialog.cancel();
            }
        });
        // 3. Get the AlertDialog from create()
        AlertDialog dialog = builder.create();
        dialog.show();
    }



    //Helper method that makes an image black and white
    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    //Reads a photo from dropbox using its path.
    private void readInPhoto(String path) {
        try {

            Log.e("Path: " , path);
            DbxPath testPath = new DbxPath(DbxPath.ROOT, path);

            // Create DbxFileSystem for synchronized file access.
            DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());

            // Read and print the contents of test file.  Since we're not making
            // any attempt to wait for the latest version, this may print an
            // older cached version.  Use getSyncStatus() and/or a listener to
            // check for a new version.

            DbxFile dbxFile = dbxFs.open(testPath);
            FileInputStream inputStream = dbxFile.getReadStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            original = bitmap;
            imageView.setImageBitmap(bitmap);

            dbxFile.close();


        } catch (IOException e) {
            e.printStackTrace();
            Log.e("readInPhoto:", e.toString());
        }
    }

    //Save a photo to dropbox.
    private void savePhoto(){
        try {

            Bitmap bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
            //create a file to write bitmap data
            File photoFile = new File(this.getCacheDir(), path);
            Log.e("savePhotoFile:", photoFile.toString());

            //Convert Bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            //write the bytes in file
            FileOutputStream fos = new FileOutputStream(photoFile);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();


            DbxPath testPath = new DbxPath(DbxPath.ROOT, path);

            // Create DbxFileSystem for synchronized file access.
            DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());


            //Since we know file exists, use open instead of create. (Dropbox API will throw exception on .create if file exists.)
            DbxFile testFile = dbxFs.open(testPath);
            try {
                //Overwrite the file
                testFile.writeFromExistingFile(photoFile, false);
            }catch(IOException e){
                Log.e("IOEXC: ", e.toString());
            }finally{
                //Ensure close of file
                testFile.close();
            }
        } catch (IOException e) {
            Log.e("savePhoto:", e.toString());
        }

        //update the original on save.
        original = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
    }

    //AsyncTask that handles saving.
    private class savePhotoTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            savePhoto();
            return null;
        }
    }

}
