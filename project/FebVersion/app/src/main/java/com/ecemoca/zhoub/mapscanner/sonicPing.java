package com.ecemoca.zhoub.mapscanner;

/**
 * Created by zhoub on 11/15/2016.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.ecemoca.zhoub.mapscanner.disGeneration.signalProcessing;

class sonicPing {
    private AudioTrack at;
    private AudioRecord ar;
    private static int sType = AudioManager.STREAM_MUSIC;
    private static int sRate = AudioTrack.getNativeOutputSampleRate(sType);
    private static int[] possibleRates = {48000, 44100, 22050, 11025, 8000};
    private static int chirpLength; //milli seconds
    private static int chirpPause; //milli seconds
    private static int chirpRepeat;
    private static int carrierFreq; //Hz
    private static int bandwidth; //Hz
    private static int bSize; //Chirp, G
    private static int bResSize; //Result
    private static int bsSize; //Chirp sequence
    private static int sPeriod; //Chirp sequence period (in shorts)
    private static int addRecordLength; //milli seconds
    private static int brSize; //Recording used buffer size
    private static int brSizeInc; //Recording true buffer sized for higher minBufferSize demands
    private short[] chirp;
    private short[] chirp_sequence;
    private short[] recording;
    private short[] recordingMic;
    private short[] recordingCam;
    private float distFactor = 1.f;
    private float[] distanceCam;
    private float[] distanceMic;
    private boolean first = true;
    private int error = 0;
    private signalProcessing signalCam;
    private signalProcessing signalMic;

    public sonicPing() {
        this(40, 40, 8000, 7000, 100, 10);
    }

    public sonicPing(int msChirpLength, int msChirpPause, int HzCarrierFreq, int HzBandwidth, int msAddRecordLength, int nChirpRepeat) {
        //Log.d("sonar", "sonicPing() (constructor)");
        sRate = getMaxRate();
        chirpLength = msChirpLength;
        chirpPause = msChirpPause;
        carrierFreq = HzCarrierFreq;
        chirpRepeat = nChirpRepeat;
        bandwidth = HzBandwidth;
        bSize =  sRate * chirpLength / 1000;
        addRecordLength = msAddRecordLength;
        brSize =  2*sRate * (addRecordLength+chirpRepeat*(chirpLength+chirpPause)) / 1000;   // Total length in ms
        brSizeInc = Math.max(brSize, AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT));
        bResSize =  sRate * (chirpPause) / 1000;
        sPeriod = sRate * (chirpLength+chirpPause) / 1000;
        bsSize =  chirpRepeat * sPeriod;
        distFactor = 340/(float)sRate/2.f;
        // Log.d("recording length","brSize = " + brSize + ", brSizeInc = " + brSizeInc + ", minBufferSize = " + AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT));
        chirp = new short[bSize];      // one chirp length+pause
        chirp_sequence = new short[bsSize];    // N chirp length+pause
        buildChirp(chirp, chirp_sequence);

        at = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2*bsSize, AudioTrack.MODE_STATIC);

        // write chirp or chirp_sequence to audio track
        if ( at.write(chirp_sequence, 0, bsSize)< bsSize) {
            error = -3;
            return;
        }

        recording = new short[brSizeInc];
        recordingMic = new short[brSizeInc/2];
        recordingCam = new short[brSizeInc/2];
    }

    public void ping() {
        int recRes = 0;
        int source;
        ar= null;
        try {
            source = MediaRecorder.AudioSource.MIC;
            //   ar= new AudioRecord(source, sRate, AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_PCM_16BIT, 2*brSizeInc);
            // Stereo recording
            ar = new AudioRecord(source,sRate, AudioFormat.CHANNEL_IN_STEREO,android.media.AudioFormat.ENCODING_PCM_16BIT,brSizeInc);//2*brSizeInc?
        } catch (Exception e) {
            Log.d("error:","audio record initialize failed!");
        }

        if (!first) {
            //Log.d("sonar", "Not the first call, reloading audio data");
            at.reloadStaticData();
        } else {
            //Log.d("sonar", "First call, not reloading audio data");
            first = false;
        }

        //Log.d("sonar", "Starting recording");
        try {
            ar.startRecording();
        } catch (Exception e) {
            Log.d(">>>>>>>>>>>","Recording start failed!");
        }

        // Wait for 20ms to get recording ready
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        at.play();

        // Monitor playback to find when done
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }while(at.getPlaybackHeadPosition()<bsSize/2);

        //Log.d("sonar", "Reading recording buffer");
        int tempRes = 1;
        while (tempRes > 0 && recRes < brSize) {
            tempRes = ar.read(recording, recRes, brSizeInc-recRes);
            Log.d("brSize",">>>>>"+brSize);
            recRes += tempRes;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        //Log.d("sonar", "Stopping recording");
        ar.stop();

        //Log.d("sonar", "Releasing AudioRecord");
        ar.release();
        ar= null;

        //Log.d("sonar", "Stopping audio track");
        at.stop();

        // Separate sound channels
    /*  for(int i=0;i<brSizeInc/2-2;i=i+2){
            recordingCam[i] = recording[2*i];
            recordingMic[i+1] = recording[2*i+1];
            recordingCam[i] = recording[2*i+2];
            recordingMic[i+1] = recording[2*i+3];
        }*/
        //link:http://stackoverflow.com/questions/20594750/split-two-channels-of-audiorecord-of-channel-in-stereo
        for(int i=0;i<brSizeInc/2-2;i=i+2){////??????????????????????
            recordingCam[i] = recording[2*i];
            recordingCam[i + 1] = recording[2*i+1];
            recordingMic[i] = recording[2*i+2];
            recordingMic[i + 1] = recording[2*i+3];
        }

      //  signalCam = new signalProcessing(bSize,sPeriod,sRate,chirp,recordingCam);
      //  signalMic = new signalProcessing(bSize,sPeriod,sRate,chirp,recordingMic);


        distanceCam = signalCam.getFinalDistance();
        distanceMic = signalMic.getFinalDistance();
        // Scale the sound intensity
//        int max=0;
//        for(int i=1;i<recordingCam.length;i++){
//            if(recordingCam[i]>max)
//                max = recordingCam[i];
//        }
//        Log.d("recordingCam","max: "+max);
//        int maxl=0;
//        for(int i=1;i<recordingMic.length;i++){
//            if(recordingMic[i]>maxl)
//                maxl = recordingMic[i];
//        }
//        Log.d("recordingMic","maxl: "+maxl);
//            int scale = maxl/max;
//
//        for(int i = 1; i<recordingCam.length; i++){
//            recordingCam[i] = (short) (scale*recordingCam[i]);
//        }

        // Play back the recorded sound
//        AudioTrack atr = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc, AudioTrack.MODE_STATIC);
//        atr.write(recordingCam,0,brSizeInc/2);
//        atr.play();
//        atr.stop();
//        AudioTrack atr1 = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc, AudioTrack.MODE_STATIC);
//        atr1.write(recordingMic,0,brSizeInc/2);
//        atr1.play();
//        atr1.stop();

//        if (recordingMic!=null) {
////        AudioTrack atr = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc, AudioTrack.MODE_STATIC);
////        atr.write(recordingCam,0,brSizeInc/2);
////        atr.play();
////        atr.stop();
////        AudioTrack atr1 = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc, AudioTrack.MODE_STATIC);
////        atr1.write(recordingMic,0,brSizeInc/2);
////        atr1.play();
////        atr1.stop();
//
//            // Bandpass filter
////            recordingMic = bandPassFilter(recordingMic,coeffAL,coeffBL);
//
//
//            //Log.d("sonar", "Finding first echo and averaging periods");
//            // periodBuffer is the period from emit signal and echo segments
//            // sPeriod is the total length of sampling points
////            averagePeriod(recordingMic, brSize/2, findBeginning(recordingMic, chirp, recordingMic.length/2, bSize), periodBuffer, sPeriod);
////
////            // Log.d("sonar", "Calculating cross-correlation");
////            // result is the cross-correlation results, which has bResSize count of points
////            crossCorrelate(periodBuffer, chirp, result, sPeriod, bSize, bResSize);
////
////            //Log.d("sonar", "Applying Gaussian filter");
////            // use 5 points for gaussian filter
////            smooth(result, bResSize, 5);
////
////            //Log.d("sonar", "Normalizing result");
////            normalize(result, bResSize, 5, 2 * chirpLength * sRate / 1000);
//
//            // Get distance with highest intensity
//            return maxIntensityDis(lastDistance);
//        }
//        //Log.d("sonar", "Ping is done, returning result.");
//        //Log.d("sonar", "---PING() ENDS HERE---");
//        else
//            Log.d(">>>>>>>>>>>","Recording is null!");

    }

    public float[] getDistanceCam(){
        return distanceCam;
    }

    public float[] getDistanceMic(){
        return distanceMic;
    }
    private boolean checkRate(int rate) {
        //Log.d("sonar", "checkRate()");
        int record = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
        return (record) >= 0;
    }

    private int getMaxRate() {
        //Log.d("sonar", "getMaxRate()");
        int rate = AudioTrack.getNativeOutputSampleRate(sType);
        if (checkRate(rate))
            return rate;
        for (int possibleRate : possibleRates) {
            rate = possibleRate;
            if (checkRate(rate))
                return rate;
        }
        return -1;
    }

    private void buildChirp(short[] buffer, short[] chirp_sequence) {
        //Log.d("sonar", "buildChirp()");
        for (int i = 0; i < bSize; i++) {
            //create a sine with sweeping frequency: sin(2 Pi f(t) * t)
            buffer[i] = (short)((0.5*(1-Math.cos(2*Math.PI*i/bSize)))  * (short)( Short.MAX_VALUE * Math.sin(2*Math.PI*(carrierFreq + bandwidth*i/bSize)*i/sRate) ) );
        }
        for (int i = 0; i < bsSize; i++) {
            if ((i % sPeriod) < bSize)
                chirp_sequence[i] = buffer[i%sPeriod];
            else
                chirp_sequence[i] = 0;
        }
    }

//    private void buildChirp(short[] buffer, short[] chirp_sequence) {
//        //Log.d("sonar", "buildChirp()");
//        int len0 = 44*3;
//        for (int i = 0; i < len0; i++) {
//            buffer[i] = (short)((0.5*(1-Math.cos(2*Math.PI*i/len0))) *(Short.MAX_VALUE * Math.sin(2 * Math.PI * (carrierFreq + bandwidth * i /len0) * i / sRate)));
//        }
//        int len = 44*1;
//        for (int i = 0; i < len; i++) {
//            buffer[bSize-len+i-100] = (short)((0.5*(1-Math.cos(2*Math.PI*i/len))) *(Short.MAX_VALUE * Math.sin(2 * Math.PI * (8000 + 2000 * i /len) * i / sRate)));
//        }
//
//        for (int i = 0; i < bsSize; i++) {
//            if ((i % sPeriod) < bSize)
//                chirp_sequence[i] = buffer[i%sPeriod];
//            else
//                chirp_sequence[i] = 0;
//        }
//    }
//    private short[] bandPassFilter(short[] xs, float[] a, float[] b){
//        float[] x = shortToFloats(xs);
//        Float[] y = new Float[x.length];
//        y[0] = b[0]*x[0];
//        for(int i=1;i<a.length;i++) {
//            y[i] = b[0] * x[i];
//            for (int j = 1; j <= i; j++) {
//                y[i] += b[j] * x[i-j] - a[j] * y[i-j];
//            }
//        }
//        for(int i=a.length;i<x.length-1;i++) {
//            y[i] = b[0] * x[i];
//            for (int j = 1; j < a.length; j++) {
//                y[i] += b[j] * x[i-j] - a[j] * y[i-j];
//            }
//        }
//        y[y.length-1] = 0f;
//        return floatToShorts(y);
//    }
//
//    private static float[] shortToFloats(short[] s) {
//        float[] floats = new float[s.length];
//        for(int i=0; i < s.length-1; i++) {
//            floats[i] = Float.valueOf(Short.toString(s[i]));
//        }
//        return floats;
//    }
//
//    private static short[] floatToShorts(Float[] f) {
//        short[] shorts = new short[f.length];
//        for(int i=0; i < f.length-1; i++) {
//            shorts[i] = f[i].shortValue();
//        }
//        return shorts;
//    }
//
//    private void crossCorrelate(float[] f, short[] g, float[] res, int fSize, int gSize, int resSize) {
//        //res has to be of the size fSize-gSize+1 > 0, returns the max of the cross-correlation
//        for (int T = 0; T < resSize; T++) {
//            res[T] = 0.f;
//            if (T < resSize/3) {
//                for (int t = 0; t < gSize; t++) {
//                    res[T] += f[t+T]*g[t];
//                }
//            }
//        }
//    }
//
//    private void averagePeriod(short[] rec, int recSize, int zero, float[] pB, int sPeriod) {
//        //Log.d("sonar", "averagePeriod()");
//        for (int i = 0; i < sPeriod; i++) {
//            pB[i] = 0.f;
//            for (int j = 0; j < chirpRepeat; j++)
//                pB[i] += rec[zero+i+j*sPeriod];
//        }
//    }
//
//    private int findBeginning(short[] rec, short[] chirp, int limit, int bSize) {
//        //Log.d("sonar", "findBeginning()");
//        float max = 0.f;
//        float temp;
//        int i = 0;
//        for (int T = 0; T < limit; T++) {
//            temp = 0.f;
//            for (int t = 0; t < bSize; t++) {
//                temp += rec[t+T]*chirp[t];
//            }
//            if (Math.abs(temp) > max) {
//                max = Math.abs(temp);
//                i = T;
//            }
//        }
//        Log.d(">>>>>>>>>>>>>","Recording samples: "+rec.length);
//        Log.d(">>>>>>>>>>>>>","Starting Point: "+i);
//        return i;
//    }
//
//    private void smooth(float[] buffer, int size, int amount) {
//        //Log.d("sonar", "smooth()");
//        float[] temp = buffer.clone();
//        for (int i = 0; i < size/3; i++) {
//            buffer[i] = 0;
//            for (int j = -2*amount+i; j <= 2*amount+i; j++)
//                if (j >= 0 && j < size)
//                    buffer[i] += Math.abs(temp[j])*Math.exp(-Math.pow((j-i)/amount, 2)/2.f);
//        }
//    }
//
//    private void normalize(float[] buffer, int size, int amount, int offset) {
//        //Log.d("sonar", "normalize()");
//        int i, j;
//        for (i = 0; i < 5; i++) {
//            lastDistance[i][0] = 0.f;
//            lastDistance[i][1] = 0.f;
//        }
//        boolean localMax;
//        for (i = offset; i < size/3; i++) {
//            localMax = true;
//            for (j = -bSize; j <= bSize; j++)
//                if (buffer[j+i] > buffer[i])
//                    localMax = false;
//            if (localMax) { //The neighboring peaks are smaller - otherwise skip this point
//                j = 5;
//                while (j > 0 && lastDistance[j-1][1] < buffer[i]) {
//                    if (j < 5) {
//                        lastDistance[j][0] = lastDistance[j-1][0];
//                        lastDistance[j][1] = lastDistance[j-1][1];
//                    }
//                    j--;
//                }
//                if (j < 5) {
//                    lastDistance[j][0] = i*distFactor;
//                    lastDistance[j][1] = buffer[i];
//                }
//            }
//        }
//
////Normalize me later, when displayed
//
////		for (i = 0; i < size/3; i++)
////			buffer[i] /= lastDistance[0][1];
//
////		for (i = 4; i >= 0; i--)
////			lastDistance[i][1] /= lastDistance[0][1];
//    }
//
//    private float maxIntensityDis(float[][] distance){
//        float maxBuf=distance[0][1];
//        int idx = 0;
//        for(int i=1;i<5;i++){
//            if(maxBuf>distance[i][1]){
//                idx = i;
//            }
//        }
//        return distance[idx][0];
//    }
}
