package com.d0klabs.cryptowalt.data;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public interface FileIO {
    public final static File FILE_NAME = new File(Environment.getExternalStorageDirectory().toString() + "/content");
    public class OOData {
        public static String sLine;
        public void setLastData(String bBase64encaps) throws IOException {
            FILE_NAME.setWritable(true);
            try (FileOutputStream outputStream = new FileOutputStream(FILE_NAME)) {
                outputStream.write(bBase64encaps.getBytes());
                outputStream.close();
                FILE_NAME.setReadOnly();
            }catch (FileNotFoundException e){
                getLastData();

            }
        };
        public static String getLastData() throws IOException {
           try {
               FileInputStream inputStream = new FileInputStream(FILE_NAME);
               BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
               try {
                   sLine=String.valueOf (br.readLine().toString());
                   br.close();
               } catch (IOException e) {
                   e.printStackTrace();
               }
               return sLine;
           } catch (FileNotFoundException e){
               FILE_NAME.createNewFile();
           }
            return sLine;
        }

        public void setLastData(ByteBuffer inputBytes) {

        }
    };
}
