package com.ecemoca.zhoub.mapscanner.visualization;

/**
 * Created by zhoub on 11/13/2016.
 */


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Align;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public class graphPlot extends View {
    private float maxRange;
    private ArrayList<Double> trace;
    private ArrayList<Double> wallCam;
    private ArrayList<Double> wallMic;

    public graphPlot(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public void setGraph(float maxRange, ArrayList<Double> trace, ArrayList<Double> wallCam,ArrayList<Double> wallMic) {
        this.maxRange = maxRange;
        this.trace = trace;
        this.wallCam = wallCam;
        this.wallMic = wallMic;
        this.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(20.f);
        paint.setTextAlign(Align.CENTER);

        // Get the size of canvas
        int w = this.getWidth();

        // Draw background
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(new Rect(0, 0, w -1, w -1), paint);

        // Draw x label grids
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(1);
        canvas.drawLine(0, 0, w - 1, 0, paint);
        canvas.drawLine(0, w /4, w - 1, w /4, paint);
        canvas.drawLine(0, w /2, w - 1, w /2, paint);
        canvas.drawLine(0, 3* w /4, w - 1,  3* w /4, paint);
        canvas.drawLine(0, w -1, w - 1,  w -1, paint);

        canvas.drawLine(0, 0, 0, w - 1, paint);
        canvas.drawLine(w/4, 0, w/4, w - 1, paint);
        canvas.drawLine(w/2, 0, w/2, w - 1, paint);
        canvas.drawLine(3*w/4, 0, 3*w/4, w - 1, paint);
        canvas.drawLine(w - 1, 0, w - 1, w - 1, paint);

        // Plot dynamic points
        paint.setStrokeWidth(5);
        if(trace!=null){
            for(int i=0;i<trace.size()-1;i++){
                float x = Float.valueOf(String.valueOf(trace.get(i)))/maxRange;
                float xCam = Float.valueOf(String.valueOf(wallCam.get(i)))/maxRange;
                float xMic = Float.valueOf(String.valueOf(wallMic.get(i)))/maxRange;
                float y = Float.valueOf(String.valueOf(trace.get(++i)))/maxRange;
                float yCam = Float.valueOf(String.valueOf(wallCam.get(i)))/maxRange;
                float yMic = Float.valueOf(String.valueOf(wallMic.get(i)))/maxRange;
                i = i+2*(trace.size()/w);    // Dynamic skip points to reduce computing intensity
                paint.setColor(Color.GRAY);
                canvas.drawPoint(Math.round(xCam*w+w/2), Math.round(yCam* w + w /2), paint);
                canvas.drawPoint(Math.round(xMic*w+w/2), Math.round(yMic* w + w /2), paint);
                paint.setColor(Color.RED);
                canvas.drawPoint(Math.round(x*w+w/2), Math.round(y* w + w /2), paint);
            }
        }
    }
}

