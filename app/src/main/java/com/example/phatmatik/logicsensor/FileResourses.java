package com.example.phatmatik.logicsensor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;


public class FileResourses extends Applic {
    private int nr = 0;
    boolean change;
    ArrayList <String> fn;
    ArrayAdapter <String> adapter;


    protected ArrayAdapter<String> mAdapter;
    Context contextItemSelect = FileResourses.this;
    final String LOG_TAG = "myLogs";

    private final android.os.Handler mHandler = new android.os.Handler();

    private ListView mListView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filedata);

        // создаём новый объект mediaPlayer
        Intent intent = getIntent();
        fn=intent.getStringArrayListExtra("uncoming");
        mListView = (ListView)findViewById(R.id.listView);

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

               int  size = fn.size();
        for(int i = 0;i < fn.size();i++) {
            if(i > 0) {
                if (fn.get(i) == fn.get(i - 1)) {
                    fn.remove(i);

                }
            }
            if (i == size - 1) {
                fn.remove(i);

            }

        }

        txtStatus = (TextView) findViewById(R.id.txt_status);
        mAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_checked,fn);

        mListView.setAdapter(mAdapter);


        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);



        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            private int nr = 0;

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode mode,
                                                  int position, long id, boolean checked) {
                if (checked) {

                    nr++;

                } else {
                    nr--;
                }
                mode.setTitle(nr + " selected");
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
                nr = 0;
                mode.getMenuInflater().inflate(R.menu.context_menu, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {

                switch(item.getItemId()) {
                    case R.id.delete_item:

                        nr = 0;

                        dialogDelete(mode);
                        nr = 0;
                        return (true);

                    case R.id.delete_All:

                        new Thread(new Runnable() {

                            @Override
                            public void run() {

                                for (int i = 0; i < mListView.getCount(); i++) {
                                    final int pos = i;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {

                                            mListView.setItemChecked(pos, true);
                                        }
                                    });
                                }
                            }
                        }).start();
                        nr = 0;
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }
            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
                mAdapter.notifyDataSetChanged();
            }
        });

        timeView = (TextView) findViewById(R.id.timeView);
        timeView.setText(getTime());


        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                connectToServer(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
            }
        });
        restoreListViewLines();
    }
    private boolean deleteChoiceValues(){
        SparseBooleanArray checked= mListView.getCheckedItemPositions();
        ArrayList<Integer> positions=new ArrayList<Integer>();
        for (int i=0;i<checked.size();i++) {
            if (checked.valueAt(i)) {
                positions.add(checked.keyAt(i));
            }
        }

        Collections.sort(positions, Collections.reverseOrder());

        for (int position : positions) {
            mAdapter.remove(mAdapter.getItem(position));
        }
        mListView.clearChoices();
        return(true);
    }


    private boolean dialogDelete(final android.view.ActionMode mode){

        AlertDialog.Builder alert = new AlertDialog.Builder(contextItemSelect);
        String title = "Delete ?";
        String message = "Delete select items?";

        alert.setMessage(message);
        alert.setTitle(title);
        alert.setIcon(R.drawable.warning);

        alert.setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                deleteChoiceValues();
                nr = 0;
                mode.finish();
                Toast.makeText(context, "Selected items are deleted", Toast.LENGTH_LONG).show();
                change = true;
            }
        });


        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                dialog.cancel();
                change = false;

            }
        });

        alert.show();

        return change;

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

    public void setDataFile(final String msg) { //2 or 1
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


                if (!messageAlert(temp,humi,getMonth()).isEmpty()) {
                    mAdapter.add(String.valueOf(messageAlert(temp,humi,getMonth())));
                    mediaPlayer.start();
                }

            }
        });

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
            case R.id.recommend:
                onDialogRecommends();
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

    protected void saveListViewLines(){
        FileOutputStream fos;
        try{
            fos = openFileOutput("listview-lines.txt", Context.MODE_PRIVATE);
        }catch(FileNotFoundException fnf){
            fnf.printStackTrace();
            return;
        }
        int cnt = mAdapter.getCount();
        String item;

        for(int i = 0; i < cnt;++i){
            try{
                item = mAdapter.getItem(i) + "\n";
                fos.write(item.getBytes("UTF-8"));

            }catch(IOException io){
                io.printStackTrace();
                return;
            }
        }
        try{
            fos.close();
        }catch(IOException exept){
            exept.printStackTrace();
        }


    }

    protected void restoreListViewLines(){
        FileInputStream fis;
        try{
            fis = openFileInput("listview-lines.txt");

        }catch(FileNotFoundException fnf){
            fnf.printStackTrace();
            return;
        }
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader br = new BufferedReader(isr);
        String line;
        try {
            while (true) {
                line = br.readLine();
                if (line == null) {
                    break;
                }
                // и каждую успешно считанную строчку добавляем
                // в список через адаптер
                mAdapter.add(line);
            }
        } catch (IOException e) {}

    }
    @Override
    protected void onStop() {
        super.onStop();
        //Save
        saveListViewLines();
    }

}


