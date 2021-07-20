package com.d0klabs.cryptowalt.data;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public interface FileIO {
    public final static File FILE_NAME = new File(Environment.getExternalStorageDirectory().toString() + "/content");
    public class OOData {
        public String sLine;
        public void createLastData(){}; //
        public String getLastData (){
           try {
               FileInputStream inputStream = new FileInputStream(FILE_NAME);
               BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
               try {
                   sLine=br.readLine();
               } catch (IOException e) {
                   e.printStackTrace();
               }
               return sLine;
           } catch (FileNotFoundException e){
               // createLastData : File
           }
            return sLine;
        }

        public void setLastData(ByteBuffer inputBytes) {

        }
    };
}
