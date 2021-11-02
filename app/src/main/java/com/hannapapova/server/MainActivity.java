package com.hannapapova.server;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ServerSocket serverSocket;
    private Socket tempClientSocket;
    Thread serverThread = null;
    public static final int SERVER_PORT = 3003;
    private EditText etUserName, etPassword, etUserInfo;
    private UserDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Server");
        etUserName = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etUserInfo = findViewById(R.id.et_userinfo);

        database = UserDatabase.getInstance(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.start_server) {
            this.serverThread = new Thread(new ServerThread());
            this.serverThread.start();
            return;
        }

        if (view.getId() == R.id.btn_add_user) {
            // Add user to DB
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
                new Thread(() -> {
                    PrintWriter out = null;
                    try {
                        out = new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(tempClientSocket.getOutputStream())),
                                true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    out.println(message);
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
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
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
            }
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    if (null == read || "Disconnect".contentEquals(read)) {
                        Thread.interrupted();
                        break;
                    }

                    String operationWord = read.substring(0, read.indexOf(" "));

                    if (operationWord.equals("auth")) {
                        String userName = read.substring(read.indexOf('{') + 1, read.indexOf('}'));
                        String password = read.substring(read.lastIndexOf('{') + 1, read.lastIndexOf('}'));
                        if (database.userDao().findNameUser(userName) != null) {
                            UserEntity user = database.userDao().getUser(userName, hashPassword(password));
                            if (user != null) {
                                sendMessage("auth {" + user.getUserId() + "}{" + user.getUserInfo() + "}");
                            } else {
                                sendMessage("password error");
                            }
                        }
                        else{
                            sendMessage("login error");
                        }
                    }
                    if (operationWord.equals("edit")) {
                        String userName = read.substring(read.indexOf('{') + 1, read.indexOf('}'));
                        String editInfo = read.substring(read.lastIndexOf('{') + 1, read.lastIndexOf('}'));
                        try {
                            database.userDao().editUser(userName, editInfo);
                            sendMessage("edit {" + editInfo + "}");
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }
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
}