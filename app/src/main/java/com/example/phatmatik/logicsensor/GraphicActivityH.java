package com.example.phatmatik.logicsensor;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.CustomLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import java.io.IOException;
import java.net.Socket;


public class GraphicActivityH extends Applic {

    private final Handler mHandler = new Handler();

    private Runnable mTimer2;
    private GraphView graphView;

    private GraphViewSeries exampleSeries2;
    //
    private double graph2LastXValue = 5d;
    public static TextView textViewHumi;

      @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.graphic_activity_h);


          textViewSeasonInfo = (TextView)findViewById(R.id.textViewseasonInfo);
          if(getMonth() > 3 || getMonth()<=9) {
              textViewSeasonInfo.setText("Summer");

          }else{
              textViewSeasonInfo.setText("Winter");

          }
          textViewSeasonInfo.setOnClickListener(new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                  Toast.makeText(getBaseContext(), getMomentTime(), Toast.LENGTH_LONG).show();
                  return;
              }
          });

        txtStatus = (TextView) findViewById(R.id.txt_status);
        textViewHumi = (TextView)findViewById(R.id.textHumi);

        timeView = (TextView) findViewById(R.id.timeView);
        timeView.setText(getTime());


        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
            }
        });

        exampleSeries2 = new GraphViewSeries("Sensor A", new GraphViewSeries.GraphViewSeriesStyle(Color.BLUE, 3),new GraphViewData[] {
                new GraphViewData(1, 1.0d)
                , new GraphViewData(2, 2.0d)
                , new GraphViewData(2.5,2.5d)
                , new GraphViewData(3, 3.0d)
                , new GraphViewData(4, 4.0d)
                , new GraphViewData(5, 5.0d)


        });

        graphView = new LineGraphView(this, "Humidity");
        ((LineGraphView) graphView).setDrawBackground(true);


        //graphView.setTitle("Temp");
        graphView.setShowLegend(true);
        graphView.setLegendAlign(GraphView.LegendAlign.MIDDLE);
        graphView.setLegendWidth(190);

        ((LineGraphView) graphView).setDrawDataPoints(true);
        ((LineGraphView) graphView).setDataPointsRadius(7f);


        graphView.addSeries(exampleSeries2);
        graphView.setViewPort(3, 10);
        graphView.setScalable(true);

        graphView.getGraphViewStyle().setHorizontalLabelsColor(Color.WHITE);
        graphView.getGraphViewStyle().setVerticalLabelsColor(Color.WHITE);
        graphView.getGraphViewStyle().setGridColor(Color.parseColor("#ff214a56"));


          graphView.setCustomLabelFormatter(new CustomLabelFormatter() {
              @Override
              public String formatLabel(double humi, boolean isValueX) {
                  // TODO Auto-generated method stub
                  if (isValueX) {
                      return (getTime());
                  }
                  return ""+(float)humi;
              }
          });








        LinearLayout layout = (LinearLayout) findViewById(R.id.graph2);
        layout.addView(graphView);

    }


    public void getDataSequecesHumi(final String msg) { //2 or 1
        String[] r = divide(msg);
        try {
            if (r[0].equals("ledon") || r[0].equals("ping") || r[0].equals("ledoff")) {
                humi = (int)Float.parseFloat(r[1]);
                temp = Float.parseFloat(r[2]);

            } else {
                humi = (int)Float.parseFloat(r[0]);
                temp = Float.parseFloat(r[1]);

            }
        }catch (Exception e){e.getMessage();}
        handler.post(new Runnable() {
            @Override
            public void run() {


                textViewHumi.setText(humi + " %\n");


            }
        });
        System.out.println(msg);
    }



    private void connectToServer(final String serverHost, final int serverPort) {
        // Все сетевые операции нужно делать в фоновом потоке, чтобы не
        // блокировать интерфейс
        new Thread() {
            @Override
            public void run() {
                try {

                    setConnectionStatus(ConnectionStatus.CONNECTING);

                    socket = new Socket(serverHost, serverPort);


                    socket.setSoTimeout(SERVER_SO_TIMEOUT);
                    serverOut = socket.getOutputStream();
                    serverIn = socket.getInputStream();


                    connectionInfo = socket.getInetAddress().getHostName()
                            + ":" + socket.getPort();
                    setConnectionStatus(ConnectionStatus.CONNECTED);

                    // Подключились к серверу, теперь можно отправлять команды
                    startServerOutputWriter();
                } catch (final Exception e) {
                    socket = null;
                    serverOut = null;
                    serverIn = null;

                    //debug("Error connecting to server: " + e.getMessage());
                    setConnectionStatus(ConnectionStatus.ERROR);
                    connectionErrorMessage = e.getMessage();

                    e.printStackTrace();
                }
            }
        }.start();
    }


    private void startServerOutputWriter() {
        new Thread() {
            @Override
            public void run() {
                try {
                    long lastCmdTime = System.currentTimeMillis();
                    while (true) {

                        String execCommand;
                        if (nextCommand != null) {
                            execCommand = nextCommand;
                        } else if (System.currentTimeMillis() - lastCmdTime > MAX_IDLE_TIMEOUT) {
                            execCommand = CMD_PING;
                        } else {
                            execCommand = null;
                        }

                        if (execCommand != null) {

                            // отправить команду на сервер
                            // debug("Write: " + execCommand);
                            serverOut.write((execCommand).getBytes());
                            serverOut.flush();

                            // и сразу прочитать ответ
                            final byte[] readBuffer = new byte[256];
                            final int readSize = serverIn.read(readBuffer);

                            if (readSize != -1) {
                                final String reply = new String(readBuffer, 0,
                                        readSize);


                                getDataSequecesHumi(reply);
                                if (nextCommandListener != null) {
                                    nextCommandListener.onCommandExecuted(
                                            execCommand, reply);
                                }
                            } else {
                                throw new IOException("End of stream");
                            }

                            // очистим "очередь" - можно добавлять следующую
                            // команду.
                            nextCommand = null;
                            nextCommandListener = null;

                            lastCmdTime = System.currentTimeMillis();
                        } else {
                            // на всякий случай - не будем напрягать систему
                            // холостыми циклами
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                } catch (final Exception e) {

                    e.printStackTrace();
                }

                disconnectFromServer();
            }
        }.start();
    }
    protected void onPause() {

        mHandler.removeCallbacks(mTimer2);
        super.onPause();

        disconnectFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimer2 = new Runnable() {
            @Override
            public void run() {

                graph2LastXValue += 1d;


                exampleSeries2.appendData(new GraphViewData(graph2LastXValue, humi), true, 10);
                mHandler.postDelayed(this, 2000);
            }
        };
        mHandler.postDelayed(mTimer2, 100);

        connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sensors, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        switch (id) {
            case R.id.connect_item:
                onDialogInputIP();
                return true;
            case R.id.recommend:
                onDialogRecommends();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
