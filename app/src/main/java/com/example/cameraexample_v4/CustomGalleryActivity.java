package com.example.cameraexample_v4;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.util.ArrayList;

public class CustomGalleryActivity extends AppCompatActivity {

    ArrayList<String> f = new ArrayList<>(); //List of file paths
    File[] listFile;
    private String folderName = "IrisPhoto";
    //Creating object of ViewPager": Change the page with L&R Scroll
    ViewPager mViewPager;
    //Creating Object of ViewPagerAdapter
    ViewPagerAdapter mViewPagerAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_gallery);
        getFromSdcard();
        //Initializing the ViewPager Object
        mViewPager = findViewById(R.id.ViewPagerMain);
        //Initializing the ViewPagerAdapter
        mViewPagerAdapter = new ViewPagerAdapter(this,f);
        //Adding the Adapter to the ViewPager
        mViewPager.setAdapter(mViewPagerAdapter);
    }

    public void getFromSdcard() {
        File file = new File(getExternalFilesDir(folderName),"/");
        if(file.isDirectory()){
            listFile = file.listFiles();
            for (int i =0; i<listFile.length; i++){
                f.add(listFile[i].getAbsolutePath());
            }
        }
    }
}
