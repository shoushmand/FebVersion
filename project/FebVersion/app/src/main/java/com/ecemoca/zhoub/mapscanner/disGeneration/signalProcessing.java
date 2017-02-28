package com.ecemoca.zhoub.mapscanner.disGeneration;

import android.util.Log;

import java.util.concurrent.BlockingDeque;

/**
 * Created by zhoub on 11/19/2016.
 * Input: queue of sound recording
 * Output: distance measurement candidates into another queue for distance-object association
 */
public class signalProcessing extends Thread {
    private int bSize;
    private int sPeriod;
    private int sRate;
    private int[] begins;
    private short[] chirp;
    private short[] recording;
    private float[] recFloat;
    private float[][] distances;
    private float[] distanceFinal;
    private float minRange = 0.5f;
    private float maxRange = 2f;
    private static float[] coeffA = {1f,-1.0306f,1.8495f,-0.832f,0.655f};
    private static float[] coeffB = {0.0184f,0f,-0.0368f,0f,0.0184f};

    public signalProcessing(int mbSize,int msPeriod, int msRate, short[] mChirp){     // short[] mRecording should be queue
        this.bSize = mbSize;
        this.sPeriod = msPeriod;
        this.sRate = msRate;
        this.chirp = mChirp;
        Log.d("signal processing", "constructor");
     //   this.recording = mRecording;




    }
    public void run() {
    }
    public float[] sprocess(short[] recording){

            // Step 1: Bandpass filter
            recFloat = bandPassFilter(recording, coeffA, coeffB);
            // Step 2: Cross-correlation
            recFloat = crossCorrelate(recFloat, chirp);
            // Step 3: Smoothing
            smooth(recFloat, 5);
            // Step 4: Find beginning points
            begins = findBeginning(recFloat, 6000, sPeriod);
            // Step 5: For each begin location, get distance with maximum power intensity
            distanceFinal = distanceGeneration(begins, recFloat, minRange, maxRange, sRate);
            Log.d("distanceFinal", distanceFinal.length + "");
        return distanceFinal;
    }

    private float[] bandPassFilter(short[] x, float[] a, float[] b){
        float[] y = new float[x.length];
        y[0] = b[0]*x[0];
        for(int i=1;i<a.length;i++) {
            y[i] = b[0] * x[i];
            for (int j = 1; j <= i; j++) {
                y[i] += b[j] * x[i-j] - a[j] * y[i-j];
            }
        }
        for(int i=a.length;i<x.length-1;i++) {
            y[i] = b[0] * x[i];
            for (int j = 1; j < a.length; j++) {
                y[i] += b[j] * x[i-j] - a[j] * y[i-j];
            }
        }
        y[y.length-1] = 0;
        return y;
    }

    private float[] crossCorrelate(float[] f, short[] g) {
        float[] res = new float[f.length];
        //res has to be of the size fSize-gSize+1 > 0, returns the max of the cross-correlation
        for (int T = 0; T < f.length-g.length; T++) {
            res[T] = 0;
            for (int t = 0; t < g.length; t++) {
                res[T] += f[t+T]*g[t];
            }
        }
        return res;
    }

    private void smooth(float[] buffer, int amount) {
        //Log.d("sonar", "smooth()");
        float[] temp = buffer.clone();
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = 0;
            for (int j = -2*amount+i; j <= 2*amount+i; j++)
                if (j >= 0 && j < buffer.length)
                    buffer[i] += Math.abs(temp[j])*Math.exp(-Math.pow((j-i)/amount, 2)/2.f);
        }
    }

    private int[] findBeginning(float[] rec, int limit, int period) {
        float max = 0.f;
        int idx = 0;
        for (int T = 0; T < rec.length-1; T++) {
            if (Math.abs(rec[T]) > max) {
                max = Math.abs(rec[T]);
                idx = T;
            }
        }
        float max2 = 0;
        int idx2 = 0;
        for (int T = 0; T < limit; T++) {
            if (Math.abs(rec[T]) > max2) {
                max2 = Math.abs(rec[T]);
                idx2 = T;
            }
        }
        int shift = idx%period;
        int idxs[] = new int[rec.length/period-2];
        for(int i=1;i<rec.length/period-1;i++){
            idxs[i-1] = shift + i*period;
        }

        Log.d(">>>>>>>>starting point ",idxs[0]+" idx2: "+idx2+" last starting point: "+idxs[idxs.length-1] + " total measurements: "+idxs.length+" shift: "+shift);
        return idxs;
    }

    private float[] distanceGeneration(int[] begin,float[] rec,float min, float max, int rate){
        int minPoints = (int) (2*min/346*rate);
        int maxPoints = (int) (2*max/346*rate);
        float[] dis = new float[begin.length];
        for(int i=0;i<begin.length;i++){
            float m=0f;
            int idx = 0;
            for(int j = begin[i]+minPoints;j<begin[i]+maxPoints;j++){
                if(begin[i]+maxPoints<rec.length){
                    if(rec[j]>m){
                        m = rec[j];
                        idx = j;
                    }
                }
            }
            dis[i] = 173.0f*(idx-begin[i])/ ((float) rate);
        }
        return dis;
    }

    public float[] getFinalDistance() {
        return distanceFinal;
    }

//    public float[][] getDistances(){
//        return distances;
//    }

//    private static int[] shortToInt(short[] s) {
//        int[] ints = new int[s.length];
//        for(int i=0; i < s.length-1; i++) {
//           ints[i] = Integer.getInteger(Short.toString(s[i]));
//        }
//        return ints;
//
//    private static short[] intToShorts(Integer[] ints) {
//        short[] shorts = new short[ints.length];
//        int max=0;
//        for(int i=0;i<ints.length;i++){
//            if(ints[i]>max)
//                max = ints[i];
//        }
//        if(max<32767){
//            for(int i=0; i < ints.length-1; i++) {
//                shorts[i] = Short.valueOf(ints[i].toString());
//            }
//        }else{
//            for(int i=0; i < ints.length-1; i++) {
//                Integer norm = ints[i]*32700/max;
//                shorts[i] = Short.valueOf(norm.toString());
//            }
//        }
//        return shorts;
//    }



//    private void averagePeriod(short[] rec, int recSize, int zero, float[] pB, int sPeriod) {
//        //Log.d("sonar", "averagePeriod()");
//        for (int i = 0; i < sPeriod; i++) {
//            pB[i] = 0.f;
//            for (int j = 0; j < chirpRepeat; j++)
//                pB[i] += rec[zero+i+j*sPeriod];
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
