package com.ecemoca.zhoub.mapscanner.acoustic;

/**
 * Created by sahar on 12/1/2016.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;

import com.ecemoca.zhoub.mapscanner.disGeneration.signalProcessing;

/**
 * MapScanner: Indoor Map Construction using Acoustics
 * Created by zhoub on 2016/11/22.
 *
 *  A new thread for sound emitting and recording.
 *  Output: recorded sound to a queue (both for Cam and Mic)
 *  Create new thread here, don't touch sonicPing
 */

public class sonic {
    private AudioTrack at;

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


    public sonic() {
        this(40, 40, 8000, 7000, 100, 10);
    }

    public sonic(int msChirpLength, int msChirpPause, int HzCarrierFreq, int HzBandwidth, int msAddRecordLength, int nChirpRepeat) {
        //Log.d("sonar", "sonicPing() (constructor)");
        sRate = getMaxRate();
        chirpLength = msChirpLength;
        chirpPause = msChirpPause;
        carrierFreq = HzCarrierFreq;
        chirpRepeat = nChirpRepeat;
        bandwidth = HzBandwidth;
        bSize = sRate * chirpLength / 1000;
        addRecordLength = msAddRecordLength;
        brSize = 2 * sRate * (addRecordLength + chirpRepeat * (chirpLength + chirpPause)) / 1000;   // Total length in ms
        brSizeInc = Math.max(brSize, AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT));
        bResSize = sRate * (chirpPause) / 1000;
        sPeriod = sRate * (chirpLength + chirpPause) / 1000;
        bsSize = chirpRepeat * sPeriod;
        distFactor = 340 / (float) sRate / 2.f;
        // Log.d("recording length","brSize = " + brSize + ", brSizeInc = " + brSizeInc + ", minBufferSize = " + AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT));
        chirp = new short[bSize];      // one chirp length+pause
        chirp_sequence = new short[bsSize];    // N chirp length+pause
        buildChirp(chirp, chirp_sequence);

        at = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * bsSize, AudioTrack.MODE_STATIC);

        // write chirp or chirp_sequence to audio track
        if (at.write(chirp_sequence, 0, bsSize) < bsSize) {
            error = -3;
            return;
        }

    }

    public void ping() {


        if (!first) {
            //Log.d("sonar", "Not the first call, reloading audio data");
            at.reloadStaticData();
        } else {
            //Log.d("sonar", "First call, not reloading audio data");
            first = false;
        }


        at.play();

        // Monitor playback to find when done
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (at.getPlaybackHeadPosition() < bsSize / 2 );


        //Log.d("sonar", "Stopping audio track");
        at.stop();


    }
    public void stop(){
        at.stop();
    }
    public int getBrSizeInc(){
        return brSizeInc;
    }
    public int getbSize(){
        return bSize;
    }
    public short[] getChirp(){
        return chirp;
    }
    public int getsPeriod(){
        return sPeriod;
    }
    public int getsRate(){
        return sRate;
    }
    public int getBrSize(){
        return brSize;
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
}