package com.atleastitworks.ejemplo_signals;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.CircularArray;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;


import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity {


    private int BUFFER_SIZE_SHOW_FREQ = (int) Math.pow(2,10);
    private int BUFFER_SIZE_SHOW_TIME = (int) Math.pow(2,8);
    private int BUFFER_SIZE = (int) Math.pow(2,16);




    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    //////////////////////////////////

    private AudioRecord ar = null;
    private int minSize;
    private int SAMPLE_RATE = 44100;

    short[] buffer = new short[BUFFER_SIZE];
    private int buffer_counter = 0;

    boolean stopped = false;
    double[] buffer_double = new double[BUFFER_SIZE];

    /////////////////////////////


    boolean buffer_ready = false;

    private int PLOTS_REFRESH_MS = 25;





    private LineChart grafico_tiempo;
    private LineChart grafico_frecuencia;

    private ArrayList<Entry> LineEntry_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);
    private ArrayList<String> labels_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);

    private ArrayList<Entry> LineEntry_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    private ArrayList<String> labels_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);


    LineDataSet dataSet_frec;
    LineData data_frec;
    LineDataSet dataSet_tiempo;
    LineData data_tiempo;

    private double[] buffer_frec = new double[BUFFER_SIZE_SHOW_FREQ];

    private int count_frec = 0;
    private int count_tiempo = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        grafico_tiempo = findViewById(R.id.line_chart_tiempo);
        grafico_frecuencia = findViewById(R.id.line_chart_frecuencia);


        ///////////////////////////

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        ///////////////////////////


//        minSize= AudioRecord.getMinBufferSize(BUFFER_SIZE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
//        ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
//        ar.startRecording();


        for(int i=0;i<BUFFER_SIZE_SHOW_TIME;i++)
        {
            LineEntry_tiempo.add(new Entry(0.0f, i));
            labels_tiempo.add(String.valueOf(i));
        }

        for(int i=0;i<BUFFER_SIZE_SHOW_FREQ;i++)
        {
            LineEntry_frecuencia.add(new Entry(0.0f, i));
            labels_frecuencia.add(String.valueOf(i));
        }
//


        dataSet_frec = new LineDataSet(LineEntry_frecuencia, "Frecuencia");
        data_frec = new LineData(labels_frecuencia, dataSet_frec);

        dataSet_tiempo = new LineDataSet(LineEntry_tiempo, "Frecuencia");
        data_tiempo = new LineData(labels_tiempo, dataSet_tiempo);


        PLOTS_REFRESH_MS = (int) ((((float)BUFFER_SIZE/(float)SAMPLE_RATE) / (float) BUFFER_SIZE_SHOW_TIME)*1000.0);



//        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);

//
//        chart.setDescription("");    // Hide the description
//        chart.getAxisLeft().setDrawLabels(false);
//        chart.getAxisRight().setDrawLabels(false);
//        chart.getXAxis().setDrawLabels(false);

//        chart.getLegend().setEnabled(false);   // Hide the legend
//
//        // enable / disable grid background
//        grafico_tiempo.setDrawGridBackground(true);
//
//        // enable touch gestures
//        grafico_tiempo.setTouchEnabled(true);
//
//        // enable scaling and dragging
//        grafico_tiempo.setDragEnabled(true);
//        grafico_tiempo.setScaleEnabled(true);

//        float minXRange = 0;
//        float maxXRange = 10;
//        grafico_tiempo.setVisibleXRange(minXRange, maxXRange);


        grafico_tiempo.getAxisLeft().setAxisMaxValue(1024.0f);
        grafico_tiempo.getAxisLeft().setAxisMinValue(-1024.0f);

//        chart.setDescription("lalala");



        new Thread(new Runnable() {

            @Override
            public void run() {
//                for(int i = 0; i < 500; i++) {
                while (true) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            calcFFT();
                        }
                    });

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();



        new Thread(new Runnable() {
            @Override
            public void run() {
               getTime();
            }
        }).start();

//        new Thread(new Runnable() {
//
//            @Override
//            public void run() {
////                for(int i = 0; i < 500; i++) {
//                while (true) {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            getTime();
//                        }
//                    });
//
//                    try {
//                        Thread.sleep(50);
//                    } catch (InterruptedException e) {
//                    }
//                }
//            }
//        }).start();


        new Thread(new Runnable() {

            @Override
            public void run() {
//                for(int i = 0; i < 500; i++) {
                while (true) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mostrar_signals();
                        }
                    });

                    try {
                        Thread.sleep(PLOTS_REFRESH_MS);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();

    }

    private void calcFFT()
    {

        if (buffer_ready)
        {
            // Here's the Fast Fourier Transform from JTransforms
            DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);

            // Pasamos a double
            for (int i = 0; i < BUFFER_SIZE; i++)
            {
                buffer_double[i] = buffer[i];
            }

            // Will store FFT in 'samplesD'
            fft.realForward(buffer_double);

            buffer_ready = false;


            // obtenemos el modulo y mostramos
            int buffer_mod_count = 0;
            for (int i = 0; i < BUFFER_SIZE_SHOW_FREQ; i++)
            {
                // calculamos el modulo
                double aux_mod = sqrt(buffer_double[buffer_mod_count]*buffer_double[buffer_mod_count] + buffer_double[buffer_mod_count+1]*buffer_double[buffer_mod_count+1]);
                buffer_mod_count += 16;

                dataSet_frec.removeFirst();
                dataSet_frec.addEntry(new Entry((float) aux_mod, i));
            }


            data_frec.removeDataSet(0);
            data_frec.addDataSet(dataSet_frec);
//        data_frec = new LineData(labels_frecuencia, dataSet_frec);

            grafico_frecuencia.setData(data_frec);

            data_frec.notifyDataChanged(); // let the data know a dataSet changed
            dataSet_frec.notifyDataSetChanged();

//        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);



            grafico_frecuencia.invalidate();
        }




    }


    private void getTime()
    {

//        ar.read(buffer, 0, minSize);
//        ar.read(buffer, 0, BUFFER_SIZE);

//        short[] buf_aux = new short[1];
//        ar.read(buf_aux, 0, 1);
//        buffer[buffer_counter] = buf_aux[0];
//        buffer_counter++;
//
//        if (buffer_counter >= BUFFER_SIZE)
//            buffer_counter = 0;


        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        AudioRecord recorder = null;
        short[][]   buffers  = new short[256][160];
        int         ix       = 0;

        try { // ... initialise

            int N = AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);

            recorder.startRecording();

            // ... loop

            while(!stopped) {
                //short[] buffer = buffers[ix++ % buffers.length];

                N = recorder.read(buffer,0,BUFFER_SIZE);


                //process is what you will do with the data...not defined here
                //process(buffer);
                buffer_ready = true;


                buffer_counter = 0;
            }
        } catch(Throwable x) {
            //Log.w(TAG,"Error reading voice audio",x);
        } finally {
            //close();
        }
    }



    private void mostrar_signals()
    {
        // Tiempo
//        for(int i=0;i<BUFFER_SIZE_SHOW_TIME;i++)
//        {
//            LineEntry_tiempo.set(i, new Entry((float) buffer[buffer_counter+i], i));
//            labels_tiempo.set(i, String.valueOf(i));
//        }
//        buffer_counter++;
//
//        LineDataSet dataSet_timepo = new LineDataSet(LineEntry_tiempo, "Tiempo");
//
//        LineData data_timepo = new LineData(labels_tiempo, dataSet_timepo);
//
//        grafico_tiempo.setData(data_timepo);
//
//        grafico_tiempo.invalidate();


        dataSet_tiempo.removeFirst();
        dataSet_tiempo.addEntry(new Entry((float) buffer[buffer_counter], count_tiempo));

        data_tiempo.removeDataSet(0);
        data_tiempo.addDataSet(dataSet_tiempo);

        buffer_counter+= (int) (PLOTS_REFRESH_MS/1000.0)*SAMPLE_RATE;


        data_tiempo.notifyDataChanged(); // let the data know a dataSet changed
        dataSet_tiempo.notifyDataSetChanged();

//        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        grafico_tiempo.setData(data_tiempo);


        grafico_tiempo.invalidate();

        count_tiempo++;
        buffer_counter++;
        if (count_tiempo >= BUFFER_SIZE_SHOW_TIME)
            count_tiempo = 0;


        // Frecuencia
//        double min = -10.0;
//        double max = 10.0;
//        double random = min + Math.random() * (max - min);

//
//        int Limit_buff = BUFFER_SIZE_SHOW_FREQ;
//        if (count_frec < BUFFER_SIZE_SHOW_FREQ)
//        {
//            buffer_frec[count_frec] = random;
//        }
//        else
//        {
//            for(int i=1;i<Limit_buff;i++)
//            {
//                buffer_frec[i-1] = buffer_frec[i];
//            }
//            buffer_frec[Limit_buff-1] = random;
//
//        }

//        ArrayList<Entry> LineEntry_aux = new ArrayList<>(BUFFER_SIZE);
//        ArrayList<String> labels_aux = new ArrayList<>(BUFFER_SIZE);
//        for(int i=0;i<Limit_buff;i++)
//        {
//            LineEntry_aux.add( new Entry((float) buffer_frec[i], i));
//            labels_aux.add( String.valueOf(i));
//        }

//        for(int i=0;i<Limit_buff;i++)
//        {
//            LineEntry_frecuencia.set(i,  new Entry((float) buffer_frec[i], i));
//            labels_frecuencia.set(i, String.valueOf(i));
//
//
//        }


//        LineDataSet dataSet_frec;
//        LineData data_frec;

//        dataSet_frec.removeFirst();
//        dataSet_frec.addEntry(new Entry((float) random, count_frec));
//
//        data_frec.removeDataSet(0);
//        data_frec.addDataSet(dataSet_frec);
////        data_frec = new LineData(labels_frecuencia, dataSet_frec);
//
//
//        data_frec.notifyDataChanged(); // let the data know a dataSet changed
//        dataSet_frec.notifyDataSetChanged();
//
////        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);
//        grafico_frecuencia.setData(data_frec);
//
//
//        grafico_frecuencia.invalidate();
//
//        count_frec++;
//        if (count_frec>=BUFFER_SIZE_SHOW_FREQ)
//            count_frec = 0;
    }



}
