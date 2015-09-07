package com.example.phatmatik.logicsensor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class Applic extends ActionBarActivity {
    public interface CommandListener {
        void onCommandExecuted(final String cmd, final String reply);
    }

    public enum ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    public NumberPicker np, np2, np3, np4;
    public TextView textViewMaxTemp, textViewMinTemp, textView11, textView12;
    protected DatabaseHelper dbHelper;

    public static final String CMD_LEDON = "ledon\n";
    public static final String CMD_LEDOFF = "ledoff\n";
    public static final String CMD_PING = "ping\n";
    final String SAVED_TEXT = "saved_text";
    String savedText;

    public Button buttonFile;
    protected SharedPreferences sPref;



    String result_str = "";
    protected TextView textViewSeasonInfo;
    float humi;
    float temp;
    public static String DEFAULT_SERVER_HOST = " ";//"192.168.1.93"
    public static int DEFAULT_SERVER_PORT = 0;//23

    public static int MaxHumigetter = 0 ;
    public static int MinHumigetter  = 0;

    public static int MaxTempergetter = 0;
    public static int MinTempergetter  = 0;



    public static final int SERVER_SO_TIMEOUT = 5000;

    /**
     * Максимальное время неактивности пользователя, если пользователь не
     * отправлял команды на сервер роботу 5 секунд, приложение само отправит
     * команду ping, чтобы держать подключение открытым.
     */
    public static final long MAX_IDLE_TIMEOUT = 5000;

    public TextView txtStatus;
    public Button btnConnect;
    public Timer timer;

    public final Handler handler = new Handler();
    public TextView timeView;

    public Socket socket;
    public OutputStream serverOut;
    public InputStream serverIn;

    public ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    public String connectionInfo;
    public String connectionErrorMessage;

    /**
     * "Очередь" команд для выполнения на сервере, состоящая из одного элемента.
     */
    public String nextCommand;
    public CommandListener nextCommandListener;

    Context context = this;
    MediaPlayer mediaPlayer;


    View promptsView;
    LayoutInflater li;

    protected String removeLastChar(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return s.substring(0, s.length()-1);
    }

    /**
     Проверка на входные праметры и вывод сообщеннй
     */


    protected String messageAlert(float temp, float himi, int month) {

        int result[] = {
                Math.abs((int) ((temp) + 40)),//до  -40 градусов
                (int) humi - MaxHumigetter,//влажность выше 60% //maxHumi == 60%
                (int) humi - MinHumigetter, //влажнось ниже 15%
                Math.abs((int) temp) - MinTempergetter,//ниже 20 С
                (int) temp - MaxTempergetter};//выше 40 С

        String line[] = {getMomentTime() + " Температура ниже допустимой на " + result[0] + ".Входная температура " + temp + "C",
                getMomentTime() + " Превышена влажность на " + result[1] + ".Текущая влажность воздуха " + humi + "%",
                getMomentTime() + " Влажность ниже допустимой на " + result[2] + ".Текущая влажность воздуха " + humi + "%",
                getMomentTime() + " Температура ниже допустимой на " + result[3] + ".Текущая температура " + temp + "C",
                getMomentTime() + " Температура выше допустимой на " + result[4] + ".Текущая температура " + temp + "C"};

        if (month >= 10 || month <= 3) {// октябрь - март
            //зима -40 Min Температура
            if (temp < MinTempergetter) {//MinTempergetter
                result_str = line[0];
           

            }
            //Влажность предел не выше 60 %
            if (humi > MaxHumigetter) { //MaxHumigetter
                result_str = line[1];
              
            }
            //Влажность предел не ниже 15 %
            if (humi < MinHumigetter) {//MinHumigetter
                result_str = line[2];
             
            }

        }


        if (month > 3 || month <= 9) {//март - сентябрь
            //лето
            //Мin температура не ниже 20
            if (temp < MinTempergetter ) {//MinTempergetter
                result_str = line[3];
               
            }
            //Max температура не выше 40
            if (temp > MaxTempergetter) {//MaxTempergetter
                result_str = line[4];
       
            }
            //Влажность предел не выше 60 %
            if (humi > MaxHumigetter) { //MaxHumigetter
                result_str = line[1];
           
            }
            //Влажность предел не ниже 15 %
            if (humi < MinHumigetter) {//MinHumigetter
                result_str = line[2];
            
            }
    
        }
        return result_str;
    }

    /**
     получаем текущее время
     */

    protected String getTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        return String.format("%02d:%02d:%02d", hour, minute, second); // ЧЧ:ММ:СС - формат времени
    }

    /**
     Получаем номер текущего месяца
     */


    protected int getMonth() {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("MM");
        String DateToStr = format.format(date);
        int monthInt = Integer.parseInt(DateToStr);
        return monthInt;
    }

    /**
     Получаем текущую дату и время
     */

    protected String getMomentTime() {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd '/' HH:mm:ss");
        String DateToStr = format.format(date);
        return DateToStr;
    }

    /**
     Осуществляем переход на другие Activity
     */


    public void onTimeHumi(View view) {
        Intent intentTemp = new Intent(this, TempHumi.class);
        startActivity(intentTemp);
    }

    public void onTemperatureChart(View view) {
        Intent Temp = new Intent(this, GraphicActivity.class);
        startActivity(Temp);
    }

    public void onHumidityChart(View view) {
        Intent Humi = new Intent(this, GraphicActivityH.class);
        startActivity(Humi);
    }

    /**
     Вызываем диалоговое окно для установки настроек температуры и влажности
     */
    public void onSettings() {
        class MainFragmentDialog extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {

                LayoutInflater inflater = getActivity().getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_settings, null, false);


                np = (NumberPicker) view.findViewById(R.id.numberPicker);
                np2 = (NumberPicker) view.findViewById(R.id.numberPicker2);
                np3 = (NumberPicker) view.findViewById(R.id.numberPicker3);
                np4 = (NumberPicker) view.findViewById(R.id.numberPicker4);

                textViewMaxTemp = (TextView) view.findViewById(R.id.textViewMaxTemp);
                if(textViewMaxTemp.getText() == null){
                    textViewMaxTemp.setText("0");
                }else {
                    textViewMaxTemp = loadText(textViewMaxTemp);
                }

                textViewMinTemp = (TextView) view.findViewById(R.id.textViewMinTemp);
                if(textViewMinTemp.getText() == null) {
                    textViewMinTemp.setText("0");
                }else {
                    textViewMinTemp = loadText3(textViewMinTemp);
                }
                textView11 = (TextView) view.findViewById(R.id.textView11);
                if(textView11.getText() == null) {
                    textView11.setText("0");
                }else {
                    textView11 = loadTextMaxProcent(textView11);
                }
                textView12 = (TextView) view.findViewById(R.id.textView12);
                if(textView12.getText() == null) {
                    textView12.setText("0");
                }else {
                    textView12 = loadTextMinProcent(textView12);
                }


                try {
                    String[] stringArray = new String[50];
                    int n = 1;

                    for (int i = 0; i < 50; i++) {
                        stringArray[i] = Integer.toString(n);
                        n++;

                    }
                    np.setMaxValue(stringArray.length - 1);
                    np.setMinValue(0);
                    np.setWrapSelectorWheel(false);
                    np.setDisplayedValues(stringArray);


                    np.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                        @Override
                        public void onValueChange(NumberPicker picker, int
                                oldVal, int newVal) {

                            textViewMaxTemp.setText(String.valueOf(oldVal) + "°");
                            textViewMaxTemp.setText(String.valueOf(newVal) + "°");
                            MaxTempergetter = Integer.parseInt(removeLastChar(textViewMaxTemp.getText().toString()));
                            saveText(textViewMaxTemp);


                        }

                    });


                    //---------------------------------------------

                    np2.setMaxValue(stringArray.length - 1);
                    np2.setMinValue(0);
                    np2.setWrapSelectorWheel(false);
                    np2.setDisplayedValues(stringArray);


                    np2.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                        @Override
                        public void onValueChange(NumberPicker picker, int
                                oldVal, int newVal) {

                            textViewMinTemp.setText(String.valueOf(oldVal)+"°");
                            textViewMinTemp.setText(String.valueOf(newVal) + "°");
                            MinTempergetter = Integer.parseInt(removeLastChar(textViewMinTemp.getText().toString()));
                            saveText3(textViewMinTemp);


                        }
                    });
                } catch (Exception exept) {
                    exept.printStackTrace();
                }
                try {
                    //-----------------------------

                    String[] stringHumidity = new String[100];
                    int k = 1;

                    for (int j = 0; j < 100; j++) {
                        stringHumidity[j] = Integer.toString(k);
                        k++;

                    }


                    np3.setMaxValue(stringHumidity.length - 1);
                    np3.setMinValue(0);
                    np3.setWrapSelectorWheel(false);
                    np3.setDisplayedValues(stringHumidity);


                    np3.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                        @Override
                        public void onValueChange(NumberPicker picker, int
                                oldVal, int newVal) {

                            textView11.setText(String.valueOf(oldVal) + "%");
                            textView11.setText(String.valueOf(newVal) + "%");
                            MaxHumigetter = Integer.parseInt(removeLastChar(textView11.getText().toString()));
                            saveTextMaxProcent(textView11);

                        }
                    });
                    //--------------------------------------

                    np4.setMaxValue(stringHumidity.length - 1);
                    np4.setMinValue(0);
                    np4.setWrapSelectorWheel(false);
                    np4.setDisplayedValues(stringHumidity);


                    np4.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                        @Override
                        public void onValueChange(NumberPicker picker, int
                                oldVal, int newVal) {
                            textView12.setText(String.valueOf(oldVal) + "%");
                            textView12.setText(String.valueOf(newVal) + "%");
                            MinHumigetter = Integer.parseInt(removeLastChar(textView12.getText().toString()));



                            saveTextMinProcent(textView12);

                        }
                    });
                } catch (Exception e) {
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Settings");
                builder.setIcon(R.drawable.package_settings_6481);
                builder.setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

               
                        MaxTempergetter = Integer.parseInt(removeLastChar(textViewMaxTemp.getText().toString()));
                        MinTempergetter = Integer.parseInt(removeLastChar(textViewMinTemp.getText().toString()));
                        MinHumigetter = Integer.parseInt(removeLastChar(textView12.getText().toString()));
                        MaxHumigetter = Integer.parseInt(removeLastChar(textView11.getText().toString()));

                        Toast.makeText(getBaseContext(), getMomentTime()+ "\nCurrent data stored.Settings saved!", Toast.LENGTH_SHORT).show();
                        return;

                    }
                }).setNegativeButton("Cancel", null);
                builder.setView(view);
                return builder.create();

            }
        }

        MainFragmentDialog dialog = new MainFragmentDialog();

        dialog.show(getFragmentManager(), "span_setting_dialog");
    }
    //---------------------------------------

    /**
     Сохраняем и загружаем значения в диалоговом окне настроек
     */

    public void saveText(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString(SAVED_TEXT, textView.getText().toString());
        ed.commit();
    }

    public TextView loadText(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        savedText = sPref.getString(SAVED_TEXT, "");
        textView.setText(savedText);
        return textView;
    }

    public TextView loadText3(TextView textView) {
        SharedPreferences sPref3 = getPreferences(MODE_PRIVATE);
        String savedText3 = sPref3.getString("simple_text", "");
        textView.setText(savedText3);
        return textView;
    }

    public void saveText3(TextView textView) {
        SharedPreferences Pref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = Pref.edit();
        ed.putString("simple_text", textView.getText().toString());
        ed.commit();
    }



    public TextView loadTextMaxProcent(TextView textView) {
        SharedPreferences sPref3 = getPreferences(MODE_PRIVATE);
        String savedText3 = sPref3.getString("simple_text_2", "");
        textView.setText(savedText3);
        return textView;
    }

    public void saveTextMaxProcent(TextView textView) {
        SharedPreferences Pref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = Pref.edit();
        ed.putString("simple_text_2", textView.getText().toString());
        ed.commit();
    }

    public TextView loadTextMinProcent(TextView textView) {
        SharedPreferences sPref3 = getPreferences(MODE_PRIVATE);
        String savedText3 = sPref3.getString("simple_text_3", "");
        textView.setText(savedText3);
        return textView;
    }

    public void saveTextMinProcent(TextView textView) {
        SharedPreferences Pref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = Pref.edit();
        ed.putString("simple_text_3", textView.getText().toString());
        ed.commit();
    }

    public void onDialogRecommends() {

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        li = LayoutInflater.from(context);
        promptsView = li.inflate(R.layout.dialog_recommend, null);

        String title = "Recommendations";
        String message = "Here, the recommended values for the winter and summer seasons residence bees.";

        alert.setMessage(message);
        alert.setTitle(title);
        alert.setIcon(R.drawable.agt_update_recommended_6941);

        alert.setView(promptsView);


        alert.setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {


            }
        });
        alert.show();
    }
    /**
     //--------------------------------------------------
     */



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.notification);
        Drawable drawable = getResources().getDrawable(R.drawable.abc_cab_background_top_mtrl_alpha);
        drawable.setAlpha(0);
        getSupportActionBar().setBackgroundDrawable(drawable);
    }

    public static String[] divide(String s) {
        ArrayList<String> tmp = new ArrayList<String>();
        int i = 0;
        boolean f = false;

        for (int j = 0; j < s.length(); j++) {
            if (s.charAt(j) == '\n') {
                if (j > i) {
                    tmp.add(s.substring(i, j));
                }
                i = j + 1;
            }
        }
        if (i < s.length()) {
            tmp.add(s.substring(i));
        }
        return tmp.toArray(new String[tmp.size()]);
    }

    /**
     * Отключиться от сервера - закрыть все потоки и сокет, обнулить переменные.
     */
    public void disconnectFromServer() {
        try {
            if (serverIn != null) {
                serverIn.close();
            }
            if (serverOut != null) {
                serverOut.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            serverIn = null;
            serverOut = null;
            socket = null;

            // очистить "очередь" команд
            nextCommand = null;
            nextCommandListener = null;

            // debug("Disconnected");
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }
    }

    public boolean sendCommand(final String cmd, final CommandListener cmdListener) {
        if (nextCommand == null) {
            nextCommand = cmd;
            this.nextCommandListener = cmdListener;
            return true;
        } else {
            return false;
        }
    }

    /**
     Отображение статуса соединения
     */

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

    /**
     Диалоговое окно для ввода IP и Port
     */
    public void onDialogInputIP() {

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        li = LayoutInflater.from(context);
        promptsView = li.inflate(R.layout.dialog, null);

        String title = "Connection settings";
        String message = "Please insert the IP-Address and Port number to connect the server.";

        alert.setMessage(message);
        alert.setTitle(title);
        alert.setIcon(R.drawable.internet8294);

        alert.setView(promptsView);

        final EditText editTextIp = (EditText) promptsView.findViewById(R.id.editTextIpInput);
        loadTextForIP(editTextIp);
        final EditText portNumber = (EditText) promptsView.findViewById(R.id.editTextPortInput);
        loadTextForPort(portNumber);



        alert.setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                if (portNumber.getText().length() == 0 && editTextIp.getText().length() == 0) {
                    Toast.makeText(context, "IP and Port number are NULL. \n Repeat your insert!", Toast.LENGTH_LONG).show();
                    return;
                } else if (portNumber.getText().length() == 0) {
                    Toast.makeText(context, "Port number is NULL. \n Please insert Port ...", Toast.LENGTH_LONG).show();
                    return;
                } else if (editTextIp.getText().length() == 0) {
                    Toast.makeText(context, "IP Address is NULL. \n Please insert IP ...", Toast.LENGTH_LONG).show();
                    return;
                }
                DEFAULT_SERVER_HOST = String.valueOf(editTextIp.getText()).trim();
                saveTextForIP(editTextIp);
                DEFAULT_SERVER_PORT = Integer.parseInt(portNumber.getText().toString());
                saveTextForPort(portNumber);
            }
        });


        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });

        alert.show();
    }


    protected void onStop() {
        super.onStop();
        timer.cancel();
        timer.purge();
        timer = null;
    }

    @Override
    protected void onStart() {
        super.onStart();

        timer = new Timer("DigitalClock");
        Calendar calendar = Calendar.getInstance();
        // Обновляем textbox
        final Runnable updateTask = new Runnable() {
            public void run() {
                timeView.setText(getTime()); // выводим текущее время
            }
        };

        // организуем регулярное обновление каждую секунду
        int msec = 999 - calendar.get(Calendar.MILLISECOND);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(updateTask);
            }
        }, msec, 1000);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.connect_item:
                onDialogInputIP();
                return true;
            case R.id.recommend:
                onDialogRecommends();
                return true;
            case R.id.database:
                onSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

 

    public void saveTextForIP(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString("ip_content", textView.getText().toString());
        ed.commit();
    }

    public TextView loadTextForIP(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        savedText = sPref.getString("ip_content", "");
        textView.setText(savedText);
        return textView;
    }

    public void saveTextForPort(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString("port_content", textView.getText().toString());
        ed.commit();
    }

    public TextView loadTextForPort(TextView textView) {
        sPref = getPreferences(MODE_PRIVATE);
        savedText = sPref.getString("port_content", "");
        textView.setText(savedText);
        return textView;
    }

}
