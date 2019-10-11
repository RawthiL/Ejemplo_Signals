package com.atleastitworks.ejemplo_signals;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;



import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity {

    // Defino los buffers, potencia de 2 para mas placer y por la FFT
    private int POW_FREC_SHOW = 10;
    private int POW_TIME_SHOW = 8;
    private int POW_FFT_BUFFER = 16;

    private int BUFFER_SIZE_SHOW_FREQ = (int) Math.pow(2,POW_FREC_SHOW);
    private int BUFFER_SIZE_SHOW_TIME = (int) Math.pow(2,POW_TIME_SHOW);
    private int BUFFER_SIZE = (int) Math.pow(2,POW_FFT_BUFFER);


    // Creamos la clase para hacer la FFT
    // ver:  https://github.com/wendykierp/JTransforms
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.wendykierp:JTransforms:3.1'
    private DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);
    // Este es el buffer de entrada a la FFT, que quiere doubles...
    double[] buffer_double = new double[BUFFER_SIZE];


    // Declaramos la clase para grabar audio
    private AudioRecord ar = null;
    private int SAMPLE_RATE = 44100; // en Hz
    // Buffer donde sale el valor crudo del microfono
    short[] buffer = new short[BUFFER_SIZE];

    // Con este flag avisamos que hay data nueva a la FFT, es un semaforo mal hecho
    boolean buffer_ready = false;

    // Con esto activo o desactivo el funcionamiento del programa (todo)
    boolean stopped = false;






    // Aca abajo van las declaraciones de ploteo....
    // ver: https://github.com/PhilJay/MPAndroidChart
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.PhilJay:MPAndroidChart:v2.2.4'

    // Cada cuanto refrescamos el plot, lo calculamos en funcion del tamaño de los buffers
    // y la frecuencia de muestreo
    private int PLOTS_REFRESH_MS;
    // Esta variable la uso para recorrer la salida del microfono
    private int buffer_counter = 0;


    //  Creamos las clases del grafico de tiempo
    private LineChart grafico_tiempo;
    private ArrayList<Entry> LineEntry_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);
    private ArrayList<String> labels_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);
    LineDataSet dataSet_tiempo;
    LineData data_tiempo;

    // Creamos las clases del grafico de FFT
    private LineChart grafico_frecuencia;
    private ArrayList<Entry> LineEntry_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    private ArrayList<String> labels_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    LineDataSet dataSet_frec;
    LineData data_frec;


    // Estas funciones de aca abajo salen de la documentación de Android, es un metodo
    // que pide permisos de microfono

    // Flag del pedido
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Pedimos permiso para grabar audio RECORD_AUDIO
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






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bloqueamos la pantalla siempre en modo retrato
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Buscamos las implementaciones en el activity_main.xml de los dos graficos
        grafico_tiempo = findViewById(R.id.line_chart_tiempo);
        grafico_frecuencia = findViewById(R.id.line_chart_frecuencia);

        // Pedimos permiso para grabar audio
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        // Llenamos los buffers de señal a plotear con nada...
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
        // Cargamos los datos en la clase que grafica
        dataSet_frec = new LineDataSet(LineEntry_frecuencia, "Frecuencia");
        data_frec = new LineData(labels_frecuencia, dataSet_frec);

        dataSet_tiempo = new LineDataSet(LineEntry_tiempo, "Tiempo");
        data_tiempo = new LineData(labels_tiempo, dataSet_tiempo);


        // Calculamos el tiempo de refresco de display para mostrar toda la señal antes que
        // se termine de grabar. No es en tiempo real, por lo que se ve mal a veces...
        PLOTS_REFRESH_MS = (int) ((((float)BUFFER_SIZE/(float)SAMPLE_RATE) / (float) BUFFER_SIZE_SHOW_TIME)*1000.0);


        // Seteamos los datos iniciales en los graficos
        grafico_tiempo.setData(data_tiempo);
        grafico_frecuencia.setData(data_frec);


        // Configuramos los ejes de los graficos (Esto es cosmetico mas que nada)
        XAxis xl = grafico_tiempo.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = grafico_tiempo.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue((float) Math.pow(2,10));
        leftAxis.setAxisMinValue((float) -Math.pow(2,10));
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = grafico_tiempo.getAxisRight();
        rightAxis.setEnabled(false);

        grafico_tiempo.setDescription("Señal temporal");



        XAxis xl_f = grafico_frecuencia.getXAxis();
        xl_f.setTextColor(Color.WHITE);
        xl_f.setDrawGridLines(true);
        xl_f.setAvoidFirstLastClipping(true);
        xl_f.setSpaceBetweenLabels(5);
        xl_f.setEnabled(true);

        YAxis leftAxis_f = grafico_frecuencia.getAxisLeft();
        leftAxis_f.setTextColor(Color.BLACK);
        leftAxis_f.setDrawGridLines(true);

        YAxis rightAxis_f = grafico_frecuencia.getAxisRight();
        rightAxis_f.setEnabled(false);

        grafico_frecuencia.setDescription("FFT");





        // Como tiene que funcionar en paralelo, necesitamos un par de threads

        // Este thread espera que el grabador de audio termine y hace la FFT. Solo mira el flag,
        // si esta en flase vuelve a dormir y si es true hace FFT.
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

        // Este thread va a estar siempre grabando audio
        new Thread(new Runnable() {
            @Override
            public void run() {
               getTime();
            }
        }).start();

        // Con este thread vamos a refrescar los graficos con la nueva informacion
        new Thread(new Runnable() {

            @Override
            public void run() {

                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);

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






    // Este método hace la FFT
    private void calcFFT()
    {
        // Solo si hay nuevos datos en el buffer...
        if (buffer_ready)
        {
            // Pasamos a double como quiere la clase FFT
            for (int i = 0; i < BUFFER_SIZE; i++)
            {
                buffer_double[i] = buffer[i];
            }

            // HAcemos la FFT. La salida va a estar en el mismo buffer. Solo saca la parte
            // real (izquierda) de la FFT, intercalando la salida real y la imaginaria.
            fft.realForward(buffer_double);


            // Terminamos de procesar el buffer, reseteamos el flag
            buffer_ready = false;

            // obtenemos el modulo y mostramos en el grafico de FFT
            int buffer_mod_count = 0;
            for (int i = 0; i < BUFFER_SIZE_SHOW_FREQ; i++)
            {
                // calculamos el modulo
                double aux_mod = sqrt(buffer_double[buffer_mod_count]*buffer_double[buffer_mod_count] + buffer_double[buffer_mod_count+1]*buffer_double[buffer_mod_count+1]);

                // Adelantamos el index del buffer con un paso grande, submuestreando la salida real
                // asi no colgamos el grafico con muchos puntos.
                buffer_mod_count += 2^(POW_FFT_BUFFER-POW_FREC_SHOW);

                // Borramos el dato
                dataSet_frec.removeFirst();
                // Agregamos un nuevo
                dataSet_frec.addEntry(new Entry((float) aux_mod, i));
            }

            // Actualizamos el dataset
            data_frec.removeDataSet(0);
            data_frec.addDataSet(dataSet_frec);
            grafico_frecuencia.setData(data_frec);

            // Le avisamos que cambio y que lo actualice
            data_frec.notifyDataChanged();
            dataSet_frec.notifyDataSetChanged();
            grafico_frecuencia.invalidate();
        }




    }


    private void getTime()
    {

        // Seteamos la prioridad
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        AudioRecord recorder = null;

        // intentamos crear el grqabador de audio y grabar...
        try {

            // Creamos el grabador
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);

            // Empezamos a grabar
            recorder.startRecording();

            // Mientras no me digan que pare...
            while(!stopped) {

                // Leo las muestras de audio
                recorder.read(buffer,0,BUFFER_SIZE);

                // Si llego aca es que hay nueva info, seteo el flag para la FFT
                buffer_ready = true;

                // Reinicio el indice del plot de señal temporal
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

        // Obtengo los datos actuales del grafico
        LineData data = grafico_tiempo.getData();

        if (data != null) {
            // Obtengo el dataset actual
            ILineDataSet set = data.getDataSetByIndex(0);

            // Agrego una entrada
            data.addXValue(String.valueOf(data.getXValCount() + 1));
            data.addEntry(new Entry((float) buffer[buffer_counter], set.getEntryCount()), 0);

            // Le avisamos al grefico que cambio el dataset
            grafico_tiempo.notifyDataSetChanged();

            // Limitamos el numero de muestras a mostrar
            grafico_tiempo.setVisibleXRangeMaximum(BUFFER_SIZE_SHOW_TIME);

            // nos movemos al final
            grafico_tiempo.moveViewToX(data.getXValCount() - BUFFER_SIZE_SHOW_TIME + 1);

            // Actualizamos el indice de ploteo de buffer
            buffer_counter++;

        }

    }

}
