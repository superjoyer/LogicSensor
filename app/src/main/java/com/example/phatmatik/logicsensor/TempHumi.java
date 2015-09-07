package com.example.phatmatik.logicsensor;



import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.Socket;

public class TempHumi extends Applic {
    public TextView textTemp;
    public TextView textHumi;
    private Button sensorA_button;
       @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.temp_humi);

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



           textTemp = (TextView)findViewById(R.id.textTemp);
           textHumi = (TextView)findViewById(R.id.textHumi);

        timeView = (TextView) findViewById(R.id.timeView);
        timeView.setText(getTime());


        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
            }
        });



        sensorA_button = (Button)findViewById(R.id.sensorA_button);
           handler.post(new Runnable() {
               @Override
               public void run() {




                   sensorA_button.setOnClickListener(new View.OnClickListener() {

                       @Override
                       public void onClick(View v) {
                           callSensorA(temp, humi);
                       }
                   });

               }
           });











}
    public void callSensorA(float temp,float humi){
        AlertDialog.Builder alert = new AlertDialog.Builder(TempHumi.this);
        li = LayoutInflater.from(TempHumi.this);
        promptsView = li.inflate(R.layout.sensor_dialog, null);

        String title = "Uncoming Data (Sensor A)";

        alert.setTitle(title);
        alert.setIcon(R.drawable.internet8294);

        alert.setView(promptsView);

      TextView textViewTemp = (TextView) promptsView.findViewById(R.id.textTemp);
      TextView textViewHumi = (TextView) promptsView.findViewById(R.id.textHumi);

        textViewTemp.setText(temp + " °C\n");
        textViewHumi.setText((int)humi + " %\n");

        alert.setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                dialog.cancel();

            }
        });
        alert.show();
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


                        textTemp.setText(temp + " °C\n");
                        textHumi.setText((int)humi + " %\n");

                        
                        sensorA_button.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {


                        callSensorA(temp, humi);


                    }
                });
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

            default:
                return super.onOptionsItemSelected(item);
        }
    }



    protected void onPause() {
        super.onPause();

        disconnectFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();

        connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
    }


}
