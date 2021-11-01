package com.hannapapova.server;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ServerSocket serverSocket;
    private Socket tempClientSocket;
    Thread serverThread = null;
    public static final int SERVER_PORT = 3003;
    private LinearLayout msgList;
    private Handler handler;
    private int greenColor;
    private EditText edMessage, etUserName, etPassword, etUserInfo;
    private UserDatabase database;
    private Button bSelectAll, bEdit, bSelectOne;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Server");
        greenColor = ContextCompat.getColor(this, R.color.green);
        handler = new Handler();
        msgList = findViewById(R.id.msgList);
        edMessage = findViewById(R.id.edMessage);
        etUserName = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etUserInfo = findViewById(R.id.et_userinfo);

        bSelectAll = findViewById(R.id.btn_select_all);
        bEdit = findViewById(R.id.btn_edit);
        bSelectOne = findViewById(R.id.btn_select_one);

        database = UserDatabase.getInstance(this);

        //Select all users
        bSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<UserEntity> allUsers = database.userDao().getAllUsers();
                for (UserEntity user : allUsers) {
                    logUser(user);
                }
            }
        });

        //Edit user info; use etUserName to set userId & etUserInfo to set userInfo
        /*bEdit.setOnClickListener(v -> {
            int id = Integer.parseInt(etUserName.getText().toString());
            String info = etUserInfo.getText().toString().trim();
            database.userDao().editUser(id, info);
        });*/

        //Select user by name and password (fake authorization)
        bSelectOne.setOnClickListener(v -> {
            String name = etUserName.getText().toString();
            String password = etPassword.getText().toString();
            UserEntity user = database.userDao().getUser(name, password);
            if(user == null){
                Log.d("WORK WITH ROOM", "Wrong username or password!");
            }else{
                logUser(user);
            }
        });
    }

    public TextView textView(String message, int color) {
        if (null == message || message.trim().isEmpty()) {
            message = "<Empty Message>";
        }
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setText(message + " [" + getTime() + "]");
        tv.setTextSize(20);
        tv.setPadding(0, 5, 0, 0);
        return tv;
    }

    public void showMessage(final String message, final int color) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                msgList.addView(textView(message, color));
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.start_server) {
            msgList.removeAllViews();
            showMessage("Server Started.", Color.BLACK);
            this.serverThread = new Thread(new ServerThread());
            this.serverThread.start();
            return;
        }
        if (view.getId() == R.id.send_data) {
            String msg = edMessage.getText().toString().trim();
            showMessage("Server : " + msg, Color.BLUE);
            sendMessage(msg);
        }
        if (view.getId() == R.id.btn_add_user) {
            // Add user to DB
            Toast.makeText(this, "Add user", Toast.LENGTH_SHORT).show();
            String userName = etUserName.getText().toString().trim();
            String userPassword = etPassword.getText().toString().trim();
            String userInfo = etUserInfo.getText().toString().trim();

            UserEntity user = new UserEntity();
            user.setUserName(userName);
            /**
             * Хеширование и сохранение пароля в бд.
             */
            try {
                user.setUserPassword(hashPassword(userPassword));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            user.setUserInfo(userInfo);

            database.userDao().insertUser(user);

            Toast.makeText(this, "User " + userName + " inserted to DB", Toast.LENGTH_SHORT).show();
        }
    }



    private void sendMessage(final String message) {
        try {
            if (null != tempClientSocket) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PrintWriter out = null;
                        try {
                            out = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(tempClientSocket.getOutputStream())),
                                    true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        out.println(message);
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
//                findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Starting Server : " + e.getMessage(), Color.RED);
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessage("Error Communicating to Client :" + e.getMessage(), Color.RED);
                    }
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            tempClientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Connecting to Client!!", Color.RED);
            }
            showMessage("Connected to Client!!", greenColor);
        }

        public void run() {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    if (null == read || "Disconnect".contentEquals(read)) {
                        Thread.interrupted();
                        read = "Client Disconnected";
                        showMessage("Client : " + read, greenColor);
                        break;
                    }
                    showMessage("Client : " + read, greenColor);

                    String operationWord = read.substring(0, read.indexOf(" "));

                    showMessage(operationWord, greenColor);

                    if (operationWord.equals("auth")){
                        String userName = read.substring(read.indexOf('{') + 1, read.indexOf('}'));
                        String password = read.substring(read.lastIndexOf('{') + 1, read.lastIndexOf('}'));
                        UserEntity user = database.userDao().getUser(userName, hashPassword(password));
                        if(user != null) {
                            sendMessage("Find user: " + user.getUserName() + " " + user.getUserInfo());
                        }
                        else{
                            sendMessage("Wrong password");
                        }
                    }
                    if (operationWord.equals("edit")){
                        String userName = read.substring(read.indexOf('{') + 1, read.indexOf('}'));
                        String editInfo = read.substring(read.lastIndexOf('{') + 1, read.lastIndexOf('}'));
                        try {
                            database.userDao().editUser(userName, editInfo);
                            sendMessage("Info Saved");
                        }catch (Exception exception){
                            sendMessage(exception.getMessage());
                        }

                    }

                } catch (IOException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != serverThread) {
            sendMessage("Disconnect");
            serverThread.interrupt();
            serverThread = null;
        }
    }

    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return new BigInteger(1, md.digest(password.getBytes())).toString(16);
    }

    private void logUser(UserEntity user) {

        Log.d("WORK WITH ROOM", "ID: " + user.getUserId() + ", name: " + user.getUserName() + ", password: " + user.getUserPassword() + ", userInfo: " + user.getUserInfo());
    }
}