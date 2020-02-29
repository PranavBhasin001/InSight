package com.example.insight;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionCloudImageLabelerOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.objects.FirebaseVisionObject;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    Map<Integer, String> categoryMap = new HashMap<>();
    static final int REQUEST_IMAGE_CAPTURE = 1;
    Button camera;
    Button gallery;
    ImageView imageView;
    static final int REQUEST_TAKE_PHOTO = 1;
    static final int RESULT_LOAD_IMG = 2;
    String currentPhotoPath;
    int PERMISSION_ALL = 1;
    //https://stackoverflow.com/questions/34342816/android-6-0-multiple-permissions
    String[] PERMISSIONS = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.GET_ACCOUNTS
    };
    Uri photoUri;
    Map<String, List> processorMap = new HashMap<>();
    private Canvas canvas = new Canvas();
    Bitmap bitmap;
    String itemLabel = null;
    LinkedList<Rect> rectangles = new LinkedList<>();
    LinkedList<Integer> categories = new LinkedList<>();
    LinkedList<String> labels = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        populateCategories();
        camera = findViewById(R.id.camera);
        gallery = findViewById(R.id.gallery);
        imageView = findViewById(R.id.image);
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        camera.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    dispatchTakePictureIntent();
                    return true;
                }

                return false;
            }
        });
        gallery.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    dispatchGetGalleryPictureIntent();
                    return true;
                }

                return false;
            }
        });
    }
    private void populateCategories() {
        this.categoryMap.put(0, "Category Unknown");
        this.categoryMap.put(1, "Home Good");
        this.categoryMap.put(2, "Fashion Good");
        this.categoryMap.put(3, "Food");
        this.categoryMap.put(4, "Place");
        this.categoryMap.put(5, "Plant");
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {

            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                photoUri = photoURI;
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
    private void dispatchGetGalleryPictureIntent() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode,data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Bundle extras = data.getExtras();
            // Bitmap imageBitmap = (Bitmap) extras.get("data");
            // Bitmap imageBitmap = (Bitmap) photo.;
            // imageView.setImageURI(photo);
            // imageView.setImageURI(photoUri);
            bitmap = BitmapFactory.decodeFile(currentPhotoPath);
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Matrix matrix = new Matrix();
            matrix.postRotate(getExifOrientation(currentPhotoPath));
            //create new rotated bitmap
            bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            processImage();
            //modifyImage();
            imageView.setImageBitmap(bitmap);

            galleryAddPic();
        } else if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK) {

            final Uri imageUri = data.getData();
            InputStream imageStream = null;
            try {
                imageStream = getContentResolver().openInputStream(imageUri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
            imageView.setImageBitmap(selectedImage);
        }
    }
    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private void processImage() {
        // Multiple object detection in static images
        FirebaseVisionObjectDetectorOptions options =
                new FirebaseVisionObjectDetectorOptions.Builder()
                        .setDetectorMode(FirebaseVisionObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .build();
        final FirebaseVisionObjectDetector objectDetector =
                FirebaseVision.getInstance().getOnDeviceObjectDetector(options);

        // Labeler
        FirebaseVisionCloudImageLabelerOptions labelerOptions =
                new FirebaseVisionCloudImageLabelerOptions.Builder()
//                        .setConfidenceThreshold(0.7f)
                        .build();
        final FirebaseVisionImageLabeler labeler = FirebaseVision.getInstance().getCloudImageLabeler(labelerOptions);


//        FirebaseVisionImage image = null;
//        try {
//            image = FirebaseVisionImage.fromFilePath(this, photoUri);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);


//        Bitmap workingCopy = convertToMutable(BitmapFactory.decodeFile(currentPhotoPath));


        canvas = new Canvas(bitmap);
//        canvas.rotate(90);
        final int size;
        objectDetector.processImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<FirebaseVisionObject>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionObject> detectedObjects) {
                                rectangles = new LinkedList<>();
                                categories = new LinkedList<>();
                                labels = new LinkedList<>();
                                processorMap.put("imageObjectData", detectedObjects);
                                Log.v("Check", processorMap.get("imageObjectData").toString());
                                for (int i = 0; i < detectedObjects.size(); i++) {
                                    FirebaseVisionObject obj = detectedObjects.get(i);
//                Integer id = ((FirebaseVisionObject) obj).getTrackingId();
                                    Rect rect = obj.getBoundingBox();
                                    int classificationCategory = obj.getClassificationCategory();
                                    rectanglesBuffer(rect);
                                    categoriesBuffer(classificationCategory);

                                    assert(rect.left < rect.right && rect.top < rect.bottom);
                                    Bitmap resultBitmap = Bitmap.createBitmap(rect.right-rect.left + 100, rect.bottom-rect.top + 100, Bitmap.Config.ARGB_8888);
                                    new Canvas(resultBitmap).drawBitmap(bitmap, -rect.left - 50, -rect.top - 50, null);
                                    FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(resultBitmap);
                                    labeler.processImage(image)
                                            .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                                                @Override
                                                public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                                                    for(FirebaseVisionImageLabel label : labels) {
                                                        Log.v("check", label.getText());
                                                        String itemLabels = label.getText();
                                                        labelBuffer(itemLabels);
                                                        break;
                                                    }
                                                }
                                            })
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    Log.e("Error", e.toString());
                                                }
                                            });



//                Log.e("id", bounds.toString());
//                // If classification was enabled:
//                int category = ((FirebaseVisionObject) obj).getClassificationCategory();
//                Log.e("Category", String.valueOf(category));
//                Float confidence = ((FirebaseVisionObject) obj).getClassificationConfidence();

                                }




                            }

                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("Error", e.toString());
                            }
                        });

//        imageView.invalidate();
        }

//        private String createLabelForObject(FirebaseVisionImageLabeler labeler, Bitmap bitmapToCrop, Rect rect) {
//            itemLabel = null;
//            assert(rect.left < rect.right && rect.top < rect.bottom);
//            Bitmap resultBitmap = Bitmap.createBitmap(rect.right-rect.left, rect.bottom-rect.top, Bitmap.Config.ARGB_8888);
//            new Canvas(resultBitmap).drawBitmap(bitmapToCrop, -rect.left, -rect.top, null);
//
//            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(resultBitmap);
//            labeler.processImage(image)
//                    .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
//                        @Override
//                        public void onSuccess(List<FirebaseVisionImageLabel> labels) {
//                            itemLabel = labels.get(0).toString();
//                        }
//                    })
//                    .addOnFailureListener(new OnFailureListener() {
//                        @Override
//                        public void onFailure(@NonNull Exception e) {
//                            Log.e("Error", e.toString());
//                        }
//                    });
//            return itemLabel;
//        }

//        private void modifyImage() {
//            Bitmap workingCopy = convertToMutable(BitmapFactory.decodeFile(currentPhotoPath));
//            canvas = new Canvas(workingCopy);
//
//            for (Object obj: processorMap.get("imageObjectData")) {
//
////                Integer id = ((FirebaseVisionObject) obj).getTrackingId();
//                Rect rect = ((FirebaseVisionObject) obj).getBoundingBox();
//                onDraw(rect);
//
////                Log.e("id", bounds.toString());
////                // If classification was enabled:
////                int category = ((FirebaseVisionObject) obj).getClassificationCategory();
////                Log.e("Category", String.valueOf(category));
////                Float confidence = ((FirebaseVisionObject) obj).getClassificationConfidence();
//            }
//
//            imageView.setImageBitmap(workingCopy);
//
//
//        }
        private void draw(Rect rect, int classificationCategory, String label) {
            Paint strokePaint = new Paint();
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.RED);
            strokePaint.setStrokeWidth(10);
            canvas.drawRect(rect, strokePaint);

            Paint text = new Paint();
            text.setStyle(Paint.Style.FILL);
            text.setColor(Color.RED);
            text.setTextSize(100);
            if ((rect.top - 100) <= 0) {
                canvas.drawText(label, rect.left, rect.bottom + 100, text);
            } else {
                canvas.drawText(label, rect.left, rect.top - 50, text);
            }

            imageView.postInvalidate();
        }
    public void labelBuffer(String label) {
        Log.d("AsyncCall", label);
        this.labels.add(label);
        startDraw();


    }
    public void rectanglesBuffer(Rect rect) {
        Log.d("AsyncCall", rect.toString());
        this.rectangles.add(rect);

    }
    public void categoriesBuffer(int category) {
        Log.d("AsyncCall", String.valueOf(category));
        this.categories.add(category);
    }
    public void startDraw() {
        draw(rectangles.remove(), categories.remove(), labels.remove());

    }
    /**
     * https://stackoverflow.com/questions/4349075/bitmapfactory-decoderesource-returns-a-mutable-bitmap-in-android-2-2-and-an-immu
     * Converts a immutable bitmap to a mutable bitmap. This operation doesn't allocates
     * more memory that there is already allocated.
     *
     * @param imgIn - Source image. It will be released, and should not be used more
     * @return a copy of imgIn, but muttable.
     */
    public static Bitmap convertToMutable(Bitmap imgIn) {
        try {
            //this is the file going to use temporally to save the bytes.
            // This file will not be a image, it will store the raw image data.
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.tmp");

            //Open an RandomAccessFile
            //Make sure you have added uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
            //into AndroidManifest.xml file
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

            // get the width and height of the source bitmap.
            int width = imgIn.getWidth();
            int height = imgIn.getHeight();
            Bitmap.Config type = imgIn.getConfig();

            //Copy the byte to the file
            //Assume source bitmap loaded using options.inPreferredConfig = Config.ARGB_8888;
            FileChannel channel = randomAccessFile.getChannel();
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, imgIn.getRowBytes()*height);
            imgIn.copyPixelsToBuffer(map);
            //recycle the source bitmap, this will be no longer used.
            imgIn.recycle();
            System.gc();// try to force the bytes from the imgIn to be released

            //Create a new bitmap to load the bitmap again. Probably the memory will be available.
            imgIn = Bitmap.createBitmap(width, height, type);
            map.position(0);
            //load it back from temporary
            imgIn.copyPixelsFromBuffer(map);
            //close the temporary file and channel , then delete that also
            channel.close();
            randomAccessFile.close();

            // delete the temp file
            file.delete();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return imgIn;
    }

    public static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognise a subset of orientation tag values.
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }

        return degree;
    }
}
