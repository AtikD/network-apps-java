package dev.atikdd.marksman.server;

import com.google.gson.Gson;
import dev.atikdd.marksman.net.Msg;

import java.io.*;
import java.net.Socket;

class PlayerSession {
    String name;
    int score;
    int shots;
    boolean ready;
    int index;
    boolean observer;

    double playerY = 167;

    double arrowX;
    double arrowY;
    boolean arrowActive;

    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;
    private final GameServer server;
    private final Gson gson = new Gson();
    private Thread readThread;

    PlayerSession(Socket socket, GameServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.index = -1;
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.dis = new DataInputStream(socket.getInputStream());
        readThread = new Thread(this::readLoop);
        readThread.setDaemon(true);
        readThread.start();
    }

    private void readLoop() {
        try {
            while (true) {
                String str = dis.readUTF();
                Msg msg = gson.fromJson(str, Msg.class);
                server.onMessage(this, msg);
            }
        } catch (IOException e) {
            server.onDisconnect(this);
        }
    }

    void send(Msg msg) {
        try {
            synchronized (dos) {
                dos.writeUTF(gson.toJson(msg));
                dos.flush();
            }
        } catch (IOException e) {
            server.onDisconnect(this);
        }
    }

    void close() {
        try { socket.close(); } catch (IOException ignored) {}
        arrowActive = false;
        if (readThread != null) readThread.interrupt();
    }
}
