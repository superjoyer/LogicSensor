package com.example.phatmatik.logicsensor;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class MainActivity extends Applic {

    public TextView txtDebug;
    public Button btnCmdLedOn;
    public Button btnCmdLedOff;
    protected ArrayList<String> sendData = new ArrayList<String>();
    Intent uncomingIntent;


    public void debug(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                txtDebug.append(msg + "\n");
            }

        });
        System.out.println(msg);
    }

    public void setDataFile(final String msg) { //2 or 1
        String[] r = divide(msg);
        try {
            if (r[0].equals("ledon") || r[0].equals("ping") || r[0].equals("ledoff")) {
                humi = (int) Float.parseFloat(r[1]);
                temp = Float.parseFloat(r[2]);

            } else {
                humi = (int) Float.parseFloat(r[0]);
                temp = Float.parseFloat(r[1]);

            }
        } catch (Exception e) {
            e.getMessage();
        }
        handler.post(new Runnable() {
            @Override
            public void run() {

                if (!messageAlert(temp, humi, getMonth()).isEmpty()) {
                    sendData.add(String.valueOf(messageAlert(temp, humi, getMonth())));
                    mediaPlayer.start();
                }
     
            }
        });

    }

    public void goToFileData(View view) {//Go To New Activity File

        uncomingIntent.putExtra("uncoming", sendData);

        startActivity(uncomingIntent);

    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewSeasonInfo = (TextView) findViewById(R.id.textViewseasonInfo);
        if (getMonth() > 3 || getMonth() <= 9) {
            textViewSeasonInfo.setText("Summer");

        } else {
            textViewSeasonInfo.setText("Winter");

        }
        textViewSeasonInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getBaseContext(), getMomentTime(), Toast.LENGTH_LONG).show();
                return;
            }
        });


        uncomingIntent = new Intent(this, FileResourses.class);

        txtStatus = (TextView) findViewById(R.id.txt_status);
        txtDebug = (TextView) findViewById(R.id.txt_debug);
        txtDebug.setMovementMethod(new ScrollingMovementMethod());
        buttonFile = (Button) findViewById(R.id.buttonFile);

        timeView = (TextView) findViewById(R.id.timeView);
        timeView.setText(getTime());

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {

                connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);


            }
        });
        btnCmdLedOn = (Button) findViewById(R.id.btn_cmd_ledon);

        btnCmdLedOn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {


                sendCommand(CMD_LEDON, new CommandListener() {

                    @Override
                    public void onCommandExecuted(final String cmd,
                                                  final String reply) {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Command: " + cmd + "\n" + "Reply: "
                                                + "ledon", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }
                });
            }
        });

        btnCmdLedOff = (Button) findViewById(R.id.btn_cmd_ledoff);
        btnCmdLedOff.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendCommand(CMD_LEDOFF, new CommandListener() {

                    @Override
                    public void onCommandExecuted(final String cmd,
                                                  final String reply) {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Command: " + cmd + "\n" + "Reply: "
                                                + "ledoff", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }
                });
            }
        });

    }

    private void connectToServer(final String serverHost, final int serverPort) {
        // Все сетевые операции нужно делать в фоновом потоке, чтобы не
        // блокировать интерфейс
        new Thread() {
            @Override
            public void run() {
                try {
                    debug(getTime() + " Connecting to server: " + serverHost + ":"
                            + serverPort + "...");
                    setConnectionStatus(ConnectionStatus.CONNECTING);

                    socket = new Socket(serverHost, serverPort);

                    // Подключились к серверу:
                    // Установим таймаут для чтения ответа на команды -
                    // сервер должет прислать ответ за 5 секунд, иначе он будет
                    // считаться отключенным (в нашем случае это позволит
                    // предотвратить вероятные зависания на блокирующем read,
                    // когда например  сервер отключился до того, как прислал
                    // ответ и сокет не распрознал это как разрыв связи с
                    // выбросом IOException)

                    socket.setSoTimeout(SERVER_SO_TIMEOUT);

                    // Получаем доступ к потокам ввода/вывода сокета для общения
                    // с сервером (роботом)
                    serverOut = socket.getOutputStream();
                    serverIn = socket.getInputStream();

                    debug(getTime() + " Connected");
                    connectionInfo = socket.getInetAddress().getHostName()
                            + ":" + socket.getPort();
                    setConnectionStatus(ConnectionStatus.CONNECTED);

                    // Подключились к серверу, теперь можно отправлять команды
                    startServerOutputWriter();
                } catch (final Exception e) {
                    socket = null;
                    serverOut = null;
                    serverIn = null;

                    debug(getTime() + " Error connecting to server: " + e.getMessage());
                    setConnectionStatus(ConnectionStatus.ERROR);
                    connectionErrorMessage = e.getMessage();

                    e.printStackTrace();
                }
            }
        }.start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
       
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
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
                            debug(getTime() + " Write: " + execCommand);
                            serverOut.write((execCommand).getBytes());
                            serverOut.flush();

                            // и сразу прочитать ответ
                            final byte[] readBuffer = new byte[256];
                            final int readSize = serverIn.read(readBuffer);

                            if (readSize != -1) {
                                final String reply = new String(readBuffer, 0,
                                        readSize);
                                debug(getTime() + " Read: " + "num bytes=" + readSize
                                        + ", value=" + reply);

                                setDataFile(reply);
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
                    debug(getTime() + " Connection error: " + e.getMessage());
                    e.printStackTrace();
                }
                debug(getTime() + " Server output writer thread finish");
                disconnectFromServer();
            }
        }.start();
    }

    public void setConnectionStatus(final ConnectionStatus status) {
        this.connectionStatus = status;
        handler.post(new Runnable() {
            @Override
            public void run() {
                updateViews();
            }
        });
    }

    public void updateViews() {

        switch (connectionStatus) {
            case DISCONNECTED:
                txtStatus.setText(R.string.status_disconnected);
                txtStatus.setTextColor(Color.RED);
                btnConnect.setVisibility(View.VISIBLE);
                btnConnect.setEnabled(true);

                break;
            case CONNECTED:
                txtStatus.setText(getString(R.string.status_connected) + ": "
                        + connectionInfo);
                txtStatus.setTextColor(Color.GREEN);
                btnConnect.setVisibility(View.GONE);
                btnConnect.setEnabled(false);

                break;
            case CONNECTING:
                txtStatus.setText(R.string.status_connecting);
                txtStatus.setTextColor(Color.YELLOW);
                btnConnect.setVisibility(View.VISIBLE);
                btnConnect.setEnabled(false);

                break;
            case ERROR:
                txtStatus.setText(getString(R.string.status_error) + ": "
                        + connectionErrorMessage);
                txtStatus.setTextColor(Color.RED);
                btnConnect.setVisibility(View.VISIBLE);
                btnConnect.setEnabled(true);

                break;
            default:
                break;
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
