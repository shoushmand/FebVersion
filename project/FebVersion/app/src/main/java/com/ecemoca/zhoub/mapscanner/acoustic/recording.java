package com.ecemoca.zhoub.mapscanner.acoustic;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ecemoca.zhoub.mapscanner.disGeneration.signalProcessing;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by sahar on 12/2/2016.
 */
public class recording extends Thread {
    private AudioRecord audioRecord;
    private short[] recording;
    private short[] recordingMic;
    private short[] recordingCam;
    int sRate;
    int brSize;
    int bsize;
    int sPeriod;
    short[] chirp;
    private boolean record = true;
    BlockingDeque<short[]> buf = new LinkedBlockingDeque<>();
    BlockingDeque<float[]> distance = new LinkedBlockingDeque<>();
    private Handler mHandler;
    private signalProcessing pro ;
    private final Runnable process = new Runnable() {//This  is continuously executed
        public void run() {

            try {
                distance.put(pro.sprocess(buf.take()));//wait if full, wait if empty
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d("process", "process");
            mHandler.removeCallbacks(process);

        }
    };
    private int brSizeInc; //Recording true buffer sized for higher minBufferSize demands
    public recording(int brSizeInc, int sRate, int brSize, int bsize,int sPeriod, short[] chirp){
        this.sRate = sRate;
        this.brSize = brSize;
        this.bsize = bsize;
        this.sPeriod = sPeriod;
        this.chirp = chirp;
        this.brSizeInc = brSizeInc;
        recording = new short[brSizeInc];
        recordingMic = new short[brSizeInc/2];
        recordingCam = new short[brSizeInc/2];
        new Thread(new Runnable() {
            @Override
            public void run() {

                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }).start();
        pro = new signalProcessing(bsize, sPeriod, sRate, chirp);

    }
    public BlockingDeque<float[]> getDistance(){
        return distance;
    }
    public void run() {


        int source;
        audioRecord= null;
        try {
            source = MediaRecorder.AudioSource.MIC;
            //   ar= new AudioRecord(source, sRate, AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_PCM_16BIT, 2*brSizeInc);
            // Stereo recording
            audioRecord = new AudioRecord(source,sRate, AudioFormat.CHANNEL_IN_STEREO,android.media.AudioFormat.ENCODING_PCM_16BIT,brSizeInc);//2*brSizeInc?
        } catch (Exception e) {
            Log.d("error:", "audio record initialize failed!");
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            record = true;
            audioRecord.startRecording();


            while (audioRecord.read(recording, 0, brSizeInc) > 0 && record) {
                Log.d("audio", recording.length + "");
                for (int i = 0; i < brSizeInc / 2 - 2; i = i + 2) {////??????????????????????
                    recordingCam[i] = recording[2 * i];
                    recordingCam[i + 1] = recording[2 * i + 1];
                    recordingMic[i] = recording[2 * i + 2];
                    recordingMic[i + 1] = recording[2 * i + 3];
                }
                Log.d("audio", recordingCam.length + "");
                Log.d("audio", recordingMic.length+ "");
                try {
                    buf.put(recordingCam);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d("buffer", buf.size() + "");
                try {
                    buf.put(recordingMic);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d("buffer", buf.size() + "");
                mHandler.post(process);
                mHandler.post(process);
            }







                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.i("status", "Stopped Recording");
        } else {
            Log.i("status", "Could not initialize AudioRecord");

        }
    }
    public void stopRecording() {
        Log.d("stop", buf.size() + "");
        record = false;
    }


}
