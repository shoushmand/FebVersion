package com.ecemoca.zhoub.mapscanner.tracking;

import android.content.Context;
import android.widget.Button;

import com.ecemoca.zhoub.mapscanner.R;

import java.util.ArrayList;

/**
 * MapScanner: Indoor Map Construction using Acoustics
 * Created by zhoub on 2016/11/21.
 */
public class inertialTracking {
    private ArrayList<Double> trace;
    private ArrayList<Double> wallCam;
    private ArrayList<Double> wallMic;

    public inertialTracking(ArrayList<Double> mTrace,ArrayList<Double> wallCam,ArrayList<Double> wallMic) {
        this.trace = (ArrayList<Double>) mTrace.clone();
        this.wallCam = wallCam;
        this.wallMic = wallMic;
    }

    // Loop closure calibration
    public ArrayList<Double> loopClosure(){
        if(trace.size()>4){
            double delta_x = trace.get(trace.size()-2)/trace.size();
            double delta_y = trace.get(trace.size()-1)/trace.size();

            for(int i=2;i<trace.size()-1;i++){
                trace.set(i,trace.get(i) - i * delta_x);
                wallCam.set(i,wallCam.get(i) - i * delta_x);
                wallMic.set(i,wallMic.get(i) - i * delta_x);
                i++;
                trace.set(i,trace.get(i) - i*delta_y);
                wallCam.set(i,wallCam.get(i) - i * delta_y);
                wallMic.set(i,wallMic.get(i) - i * delta_y);
            }
        }
        return trace;
    }

}
