package com.example.cameraexample_v4;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.PagerAdapter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {

    private static final String TAG ="AndroidCameraApi";

    //res
    private Button btnTake;
    private Button btnGallery;
    private Button btnRecord;
    private TextureView textureView;
    private ListView cameraIdlist;
    private ListView jpegSizeList;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static  {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    //variations that related to camera2 api
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;

    //store the image
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private File folder;
    private String foldername = "IrisPhoto";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private StreamConfigurationMap map;
    private long jpegsizeIndex;
    private int mCameraFacing;  //Front or back camera state

    //Media Recording
    private Size mPreviewSize;
    private Size mVideoSize;
    private MediaRecorder mMediaRecorder;   //only mp4
    /** Manager used to mute sounds and vibrations during video recording. */
    private AudioManager mAudioManager;
    private boolean mIsRecording = false;
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //Managing the activity lifecycle
    //onCreate(): Initialize the essential components of the activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG,"iris...onCreate() start");

        mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;   //init

        init();
        Log.e(TAG,"iris...onCreate() end");
    }   //onCreate() end

    private void init(){
        Log.e(TAG,"iris...initCamera() start");
        setContentView(R.layout.activity_main);     // define the layout for the activity's user interface.

        btnTake = findViewById(R.id.btnTake);       //btnTake(id) declared in activity_main.xml
        btnGallery = findViewById(R.id.btnGallery); //btnGallery(id) declared in activity_main.xml
        btnRecord = (Button) findViewById(R.id.btnRecord);

        if(btnTake !=null)
            //listen the event and define the button click activity
            btnTake.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    takePicture(jpegsizeIndex);
                }
            });
        if (btnGallery != null)
            btnGallery.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(MainActivity.this,CustomGalleryActivity.class);
                    startActivity(intent);
                }
            });
        //////////////////////////edit================================================
        if(btnRecord != null)
            btnRecord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cameraDevice.close();
                    closePreviewSession();
                    if(mIsRecording){
                        stopRecordingVideo();
                    }else{
                        startRecordingVideo();
                    }
                }
            });
        textureView = findViewById(R.id.texture);   //call the View that declared in activity_main.xml
        if(textureView != null){
            textureView.setSurfaceTextureListener(textureListener);}

        cameraIdlist = (ListView) findViewById(R.id.cameraIdList);
        ArrayList<Integer> dataCameraId = new ArrayList<>();
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this,R.layout.listview_camid,dataCameraId);
        cameraIdlist.setAdapter(adapter);

        jpegSizeList = (ListView) findViewById(R.id.SizeList);
        ArrayList<String> dataSize = new ArrayList<>();
        ArrayAdapter<String> adapter1 =new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,dataSize);
        jpegSizeList.setAdapter(adapter1);


        /** Use characteristics.get() to get Key value of each information
         - LENS_FACING : LENS_FACING_FRONT=0, LENS_FACING_BACK=1, LENS_FACING_EXTERNAL=2
         - SCALER_STREAM_CONFIGURATION_MAP : include some information that support camera
         -
         */
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()){
                //get some information of each cameraId using getCameraCharacteristics()
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                //throw the key about the needed information using get()
                dataCameraId.add(characteristics.get(CameraCharacteristics.LENS_FACING));
            }

        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        //adapter.notifyDataSetChanged();

        cameraIdlist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this,"camera ID : "+dataCameraId.get(position),Toast.LENGTH_SHORT).show();
                mCameraFacing = dataCameraId.get(position);
                dataSize.clear();
                cameraDevice.close();
                mCameraOpenCloseLock.release();
                init();
                try {
                    //list up the sizes
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(dataCameraId.get(position).toString());
                    Size[] jpegSizes = null;

                    if (characteristics != null) {
                        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                        //dataSize.add("Camera ID : " + dataCameraId.get(position));
                        //dataSize.add("Total: " + jpegSizes.length);
                        for (int i=0;i<jpegSizes.length;i++){
                            dataSize.add(jpegSizes[i].getWidth() + "*" + jpegSizes[i].getHeight());
                        }
                        jpegSizeList.setAdapter(adapter1);
                    }
                    //change the lens

                }
                catch(CameraAccessException e) {
                    e.printStackTrace();
                }
                //click jpeg size list
                jpegSizeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        if(jpegSizeList != null){
                            jpegsizeIndex = jpegSizeList.getItemIdAtPosition(position);
                        }
                        Toast.makeText(MainActivity.this,"size: "+dataSize.get(position),Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        Log.e(TAG,"iris...initCamera() end");
    }


    //SurfaceTextureListener: When the APP is resumed show camera preview.
    TextureView.SurfaceTextureListener textureListener =new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.e(TAG,"iris...onSurfaceTextureAvailable start");
            //Open your Camera here
            openCamera(mCameraFacing);
            Log.e(TAG,"iris...onSurfaceTextureAvailable end");
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            //Transform your image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        }
    };


    // A callback objects for receiving updates about the state of a camera device.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG,"iris...onOpened start");
            cameraDevice = camera;
            createCameraPreview();  //start preview
            mCameraOpenCloseLock.release();
            Log.e(TAG,"iris...onOpened end");
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG,"iris...onError start");
            cameraDevice.close();
            cameraDevice = null;
            mCameraOpenCloseLock.release(); //semaphore release
            Log.e(TAG,"iris...onError end");
        }
    };

    protected void startBackgroundThread(){
        Log.e(TAG,"iris...startBackgroundThread start");
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.e(TAG,"iris...startBackgroundThread end");
    }

    protected void stopBackgroundThread(){
        Log.e(TAG,"iris...stopBackgroundThread start");
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        Log.e(TAG,"iris...stopBackgroundThread end");
    }


    protected void takePicture(long jpegsizeIndex){
        Log.e(TAG,"iris...takepicture start");
        int jpegsizeIndex_o = Long.valueOf(jpegsizeIndex).intValue();
        if(cameraDevice == null){
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        if (!isExternalStorageAvailableForRW() || isExternalStorageReadOnly()){
            btnTake.setEnabled(false);
        }
        if(isStoragePermissionGranted()){
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try{
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                Size[] jpegSizes = null;
                if (characteristics != null){
                    jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                }
                int width =640;
                int height = 480;
                if(jpegSizes != null && jpegSizes.length > 0){
                    width = jpegSizes[jpegsizeIndex_o].getWidth();
                    height = jpegSizes[jpegsizeIndex_o].getHeight();
                }
                ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
                List<Surface> outputSurfaces = new ArrayList<>(2);
                outputSurfaces.add(reader.getSurface());
                outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(reader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                //Orientation
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));
                //save picture info
                file = null;
                folder = new File(foldername);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "IMG_"+timeStamp +".jpg";
                file = new File(getExternalFilesDir(foldername),"/"+imageFileName);
                if(!folder.exists()){
                    folder.mkdirs();
                }
                ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = null;
                        try {
                            image = reader.acquireLatestImage();
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            save(bytes);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (image != null) {
                                image.close();
                            }
                        }
                    }

                    private void save(byte[] bytes) throws IOException {
                        OutputStream output = null;
                        try {
                            output = new FileOutputStream(file);
                            output.write(bytes);
                        }finally {
                            if(null != output){
                                output.close();
                            }
                        }
                    }
                };
                reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
                final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Toast.makeText(MainActivity.this,"saved"+file, Toast.LENGTH_SHORT).show();
                        createCameraPreview();
                    }
                };

                cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try{
                            session.capture(captureBuilder.build(), captureListener,mBackgroundHandler);
                        }catch (CameraAccessException e){
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }
                },mBackgroundHandler);
            }catch (CameraAccessException e){
                    e.printStackTrace();
            }
        }
        Log.e(TAG,"iris...takepicture end");
    }


    private static  boolean isExternalStorageReadOnly(){
        String exStorageState = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(exStorageState)){
            return true;
        }
        return false;
    }
        private boolean isExternalStorageAvailableForRW(){
        String extStorageState = Environment.getExternalStorageState();
        if (extStorageState.equals(Environment.MEDIA_MOUNTED)){
            return true;
        }
        return false;
    }
    private boolean isStoragePermissionGranted() {
        Log.e(TAG,"iris...isStoragePermissionGranted start");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                //Permission is granted
                return true;
            } else {
                //Permission revoked
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else {
            //Permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }
    //onOpened(), onCaptureCompleted()에서 호출
    protected void createCameraPreview() {
        Log.e(TAG,"iris...createCameraPreview start");
        try{
            closePreviewSession();  //preview session close
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    //When the session is ready, we start displaying the preview
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {Toast.makeText(MainActivity.this,"Configuration change",Toast.LENGTH_SHORT).show();
                    Toast.makeText(MainActivity.this,"Configuration change",Toast.LENGTH_SHORT).show();
                }
            },null);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        Log.e(TAG,"iris...createCameraPreview end");
    }

    private void closePreviewSession() {
        if(cameraCaptureSessions != null){
            cameraCaptureSessions.close();
            //cameraDevice.close();
            cameraCaptureSessions = null;
        }
    }

    private void openCamera(int cameraIndex){
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        Log.e(TAG,"is camera open");
        try{
            cameraId = manager.getCameraIdList()[cameraIndex];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];       //jpeg
            //mVideoSize=chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            //CAMERA PERMISSION  && WRITE_EXTERNAL_STORAGE check!
            if(ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(MainActivity.this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO},REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId,stateCallback,null);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        Log.e(TAG,"openCamera end");
    }
    protected void updatePreview(){
        Log.e(TAG,"iris...updatePreview start");
        if(null==cameraDevice){
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        Log.e(TAG,"iris...updatePreview end");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        Log.e(TAG,"iris...onRequestPermissionResult start");
        super.onRequestPermissionsResult(requestCode, permissions,grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION){
            if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                Toast.makeText(MainActivity.this, "you cannot use this app without grant",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        Log.e(TAG,"iris...onRequestPermissionResult end");
    }
    //onResume(): captures all user input. / resume:restart
    @Override
    protected void onResume(){
        super.onResume();
        Log.e(TAG,"onResume");
        startBackgroundThread();
        if(textureView.isAvailable()){
            openCamera(mCameraFacing);
        }else{
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    //onPause(): activity is still partially visible, but the user is leaving the activity,
    // and the activity will soon enter the Stopped or Resumed state.
    @Override
    protected void onPause() {
        Log.e(TAG,"onPause");
        stopBackgroundThread();
        cameraDevice.close();
        super.onPause();
    }

    private void setUpMediaRecorder() throws IOException{
        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        //mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        //mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        String fileName = String.format("%d.mp4",System.currentTimeMillis());
        file = new File(getExternalFilesDir(foldername),"/"+fileName);
        //File path = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/videotest");
        if(!folder.exists()){
            folder.mkdirs();
        }

        mNextVideoAbsolutePath = file.getAbsolutePath();

        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); //video compression: H264
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
        mMediaRecorder.prepare();

    }
    private void startRecordingVideo(){
        try {
            closePreviewSession();
            //cameraDevice.close();
            setUpMediaRecorder();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture !=null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            //set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            //set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            captureRequestBuilder.addTarget(recorderSurface);

            //Start a capture session
            //Once the session starts, we can update the UI and start recording
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSessions = cameraCaptureSessions;
                    updatePreview();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //UI
                            btnRecord.setText("Stop");
                            mIsRecording = true;

                            //Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this,"Failed",Toast.LENGTH_SHORT).show();

                }
            },mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo(){
        //UI
        mIsRecording =false;
        btnRecord.setText("Record");
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);

        Toast.makeText(MainActivity.this,"Video saved: "+mNextVideoAbsolutePath,Toast.LENGTH_SHORT).show();
        Log.d(TAG,"Video saved: "+mNextVideoAbsolutePath);

        mNextVideoAbsolutePath=null;
        createCameraPreview();
    }

    //set video size with 3*4.
    private static Size chooseVideoSize(Size[] choices){
        for (Size size : choices){
            if(size.getWidth() == size.getHeight()*4/3 && size.getWidth() <= 1080){
                return size;
            }
        }
        Log.e(TAG,"Couldn't find any suitable video size");
        return choices[choices.length -1];
    }

    private static Size choooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio){
        List<Size> bigEnough = new ArrayList<>();
        int w=aspectRatio.getWidth();
        int h=aspectRatio.getHeight();
        for (Size option : choices){
            if(option.getHeight() == option.getWidth()*h/w &&
                option.getWidth() >= width && option.getHeight() >=height){
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0){
            return Collections.min(bigEnough, new CompareSizesByArea());
        }else{
            Log.e(TAG,"Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs){
            //We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

}

