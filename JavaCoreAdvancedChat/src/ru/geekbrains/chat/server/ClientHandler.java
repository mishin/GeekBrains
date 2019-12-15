package ru.geekbrains.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler {
    List<String> blackList;
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nick;

    public ClientHandler( Server server, Socket socket ) {
        try {
            this.socket = socket;
            this.server = server;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.blackList = new ArrayList<>();
            new Thread(() -> {
                try {
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/auth")) { // /auth login72 pass72
                            String[] tokens = str.split(" ");
                            String newNick = WorkWithDbService.getNickByLoginAndPass(tokens[1], tokens[2]);
                            if (newNick != null) {
                                if (!server.isNickBusy(newNick)) {
                                    sendMsg("/authok");
                                    nick = newNick;
                                    server.subscribe(this);
                                    blackList = WorkWithDbService.getBlackList(nick);
                                    break;
                                } else {
                                    sendMsg("Учетная запись уже используется");
                                }
                            } else {
                                sendMsg("Неверный логин/пароль");
                            }
                        }
                    }
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                out.writeUTF("/serverclosed");
                                break;
                            }
                            if (str.startsWith("/w ")) { // /w nick3 lsdfhldf sdkfjhsdf wkerhwr
                                String[] tokens = str.split(" ", 3);
                                String m = str.substring(tokens[1].length() + 4);
                                server.sendPersonalMsg(this, tokens[1], tokens[2]);
                            }
                            if (str.startsWith("/block ")) { // /blacklist nick3
                                String[] tokens = str.split(" ");
                                String blackListNick = tokens[1];
                                if (blackListNick != null) {
                                    if (server.isNickBusy(blackListNick)) {
                                        boolean alreadyBlocked = false;
                                        for (String s : blackList) {
                                            if (s.equals(blackListNick)) {
                                                sendMsg("Пользовател " + blackListNick + " уже присутствует в черном " +
                                                        "списке");
                                                alreadyBlocked = true;
                                                break;
                                            }
                                        }
                                        if (!alreadyBlocked) {
                                            WorkWithDbService.addUserToBlackListForSpecificUser(nick,
                                                    blackListNick);
                                            blackList.add(blackListNick);
                                            sendMsg("Вы добавили пользователя " + blackListNick + " в черный список");
                                        }
                                    } else {
                                        sendMsg("Вы не можете добавить " + blackListNick + ", т.к. такого ника нет в " +
                                                "чате.");
                                    }
                                }
                            }
                            if (str.equals("/getblacklist")) { // /blacklist nick3
                                if (blackList.isEmpty()) sendMsg("У вас нет черного списка");
                                else
                                    sendMsg("У вас в черном списке следующие пользователи: " + WorkWithDbService.getBlacklist(nick));
                            }
                            if (str.startsWith("/unblock ")) { // /blacklist nick3
                                String[] tokens = str.split(" ");
                                String blackListNick = tokens[1];
                                if (blackListNick != null && !blackList.isEmpty()) {
                                    boolean isBlocked = false;
                                    for (int i = 0; i < blackList.size(); i++) {
                                        if (blackList.get(i).equals(blackListNick)) {
                                            WorkWithDbService.removeUserToBlackListForSpecificUser(nick,
                                                    blackListNick);
                                            blackList.remove(blackListNick);
                                            sendMsg("Вы удалили из чернорго списка пользователя под ником: " + blackListNick);
                                            isBlocked = true;
                                            break;
                                        }
                                    }
                                    if (!isBlocked) {
                                        sendMsg("Пользователя с ником " + blackListNick + " нет в черном списке");
                                    }
                                }
                            }
                        } else {
                            server.broadcastMsg(this, nick + ": " + str);
                        }
                        System.out.println("Client: " + str);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    server.unsubscribe(this);
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getNick() {
        return nick;
    }

    public boolean checkBlackList( String nick ) {
        return blackList.contains(nick);
    }

    public void sendMsg( String msg ) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
