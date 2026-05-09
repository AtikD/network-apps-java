package dev.atikdd.marksman;

import com.google.gson.Gson;
import dev.atikdd.marksman.net.GameSnapshot;
import dev.atikdd.marksman.net.Msg;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class GameClient {

    public interface OnSnapshot   { void accept(GameSnapshot snap); }
    public interface OnError      { void accept(String message); }
    public interface OnDisconnect { void run(); }

    public OnSnapshot   onSnapshot;
    public OnError      onError;
    public OnDisconnect onDisconnect;

    private final DataOutputStream dos;
    private final DataInputStream  dis;
    private final Gson gson = new Gson();
    private final Thread readThread;

    public GameClient(InetAddress host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        dos = new DataOutputStream(socket.getOutputStream());
        dis = new DataInputStream(socket.getInputStream());
        readThread = new Thread(this::readLoop);
        readThread.setDaemon(true);
        readThread.start();
    }

    private void readLoop() {
        try {
            while (true) {
                String str = dis.readUTF();
                Msg msg = gson.fromJson(str, Msg.class);
                switch (msg.type) {
                    case SNAPSHOT -> { if (onSnapshot != null) onSnapshot.accept(msg.snapshot); }
                    case ERROR    -> { if (onError    != null) onError.accept(msg.error); }
                }
            }
        } catch (IOException e) {
            if (onDisconnect != null) onDisconnect.run();
        }
    }

    public void send(Msg msg) {
        try {
            synchronized (dos) {
                dos.writeUTF(gson.toJson(msg));
                dos.flush();
            }
        } catch (IOException e) {
            if (onDisconnect != null) onDisconnect.run();
        }
    }
}
