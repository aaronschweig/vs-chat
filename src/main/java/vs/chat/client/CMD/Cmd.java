package vs.chat.client.CMD;

import vs.chat.client.ClientApiImpl;
import vs.chat.entities.Chat;
import vs.chat.entities.Message;
import vs.chat.entities.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Cmd {

    private ClientApiImpl api;
    private BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));
    private boolean listening = false;

    public Cmd(ClientApiImpl api) {
        this.api = api;

        this.startCommandLineClient();

        // user: troykessler
        // password: Passwort123
        // userId: bb413062-c471-45cd-8258-369d769d8317

        // user: troy
        // password: Passwort123
        // userId: 5980d889-2ba4-446f-b082-10fa7c605c9c

    }

    private void startCommandLineClient() {
        this.printHelp();
        while (true) {
            try {
                System.out.print("> ");
                String userInput = this.userIn.readLine();

                if (userInput.equals("/login") && this.api.getUserId() != null) {
                    System.out.println("Already logged in!");
                    continue;
                } else if (this.api.getUserId() == null) {
                    if (!userInput.equals("/login") && !userInput.equals("/help") && !userInput.equals("/exit")) {
                        System.out.println("You need to login!");
                        continue;
                    }
                }

                switch (userInput) {
                    case "/login":
                        this.login();
                        break;
                    case "/chats":
                        this.listChats();
                        break;
                    case "/contacts":
                        this.listContacts();
                        break;
                    case "/createchat":
                        this.createChat();
                        break;
                    case "/openchat":
                        this.openChat();
                        break;
                    case "/help":
                        this.printHelp();
                        break;
                    case "/exit":
                        this.api.exit();
                        break;
                    default:
                        System.out.println("Command not found! Type /help for available commands");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void login() {
        try {
            System.out.print("Username: ");
            String username = this.userIn.readLine();

            // passwort mit console.readPassword() einlesen
            System.out.print("Password: ");
            String password = this.userIn.readLine();

            this.api.login(username, password);

            System.out.println("\nLogged in as '" + this.api.getUsernameFromId(this.api.getUserId()) + "'");

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("/help -- print available commands");
        System.out.println("/login -- login with username and password");
        System.out.println("/chats -- list available chats");
        System.out.println("/contacts -- list contacts");
        System.out.println("/createchat -- create new chat with other contacts");
        System.out.println("/openchat -- open chat to send messages");
        System.out.println("/exit -- exit application");
    }

    private void listChats() {
        Set<Chat> chats = this.api.getChats();

        System.out.println("---------");

        if (chats != null) {
            if (chats.size() > 0) {
                for (Chat chat: chats) {
                    Set<User> chatContacts = this.api.getContacts()
                            .stream()
                            .filter(c -> chat.getUsers().contains(c.getId()))
                            .collect(Collectors.toSet());

                    String users = chatContacts.stream().map(User::getUsername).reduce("", String::concat);
                    System.out.println("Chatname: " + chat.getName() + " (Users: " + users + ")");
                }
            } else {
                System.out.println("0 Chats");
            }
        } else {
            System.out.println("No chats available!");
        }

        System.out.println("---------");
    }

    private void listContacts() {
        Set<User> contacts = this.api.getContacts();

        System.out.println("---------");

        if (contacts != null) {
            if (contacts.size() > 0) {
                for (User contact: contacts) {
                    System.out.println("Kontakt: " + contact.getUsername());
                }
            } else {
                System.out.println("0 Contacts");
            }
        } else {
            System.out.println("No contacts available!");
        }

        System.out.println("---------");
    }

    private void createChat() {
        try {
            System.out.print("Chatname: ");
            String chatname = this.userIn.readLine();

            int amountChatUsers;

            List<UUID> users = new ArrayList<>();

            do {
                System.out.print("Amount of Chat Users (at least 1): ");
                String userIn = this.userIn.readLine();
                amountChatUsers = Integer.parseInt(userIn);
            } while (amountChatUsers < 1);

            for (int i = 0; i < amountChatUsers; i++) {

                User user;

                do {
                    System.out.print((i + 1) + ". User (username): ");
                    String username = this.userIn.readLine();

                    user = this.api.getContacts().stream().filter(c -> c.getUsername().equals(username)).findAny().orElse(null);

                    if (user == null) {
                        System.out.println("User not found!");
                    }

                } while (user == null);

                users.add(user.getId());
            }

            UUID[] userIds = new UUID[users.size()];
            userIds = users.toArray(userIds);

            Chat createdChat = this.api.createChat(chatname, userIds);
            System.out.println("Created Chat '" + createdChat.getName() + "'");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void openChat() {
        try {

            Thread messageListener = this.messageListener();
            Chat chat;

            do {
                System.out.print("Chatname: ");
                String chatname = this.userIn.readLine();
                chat = this.api.getChats().stream().filter(c -> c.getName().equals(chatname)).findAny().orElse(null);

                if (chat == null) {
                    System.out.println("Chat not found!");
                }
            } while (chat == null);

            Set<Message> chatMessages = this.api.getChatMessages(chat.getId());

            System.out.println("Opened chat '" + chat.getName() + "' (type /quit to exit chat window)\n");

            for (Message message: chatMessages) {
                System.out.println(this.api.getUsernameFromId(message.getOrigin()) + " -> " + message.getContent());
            }

            this.listening = true;
            messageListener.start();

            while (true) {
                String message = this.userIn.readLine();

                if (message.equals("/quit")) {
                    System.out.println("\nExited chat window");
                    break;
                }

                this.api.sendMessage(message, chat.getId());
            }

            // stop message listener
            this.listening = false;

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Thread messageListener() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (listening) {
                    try {
                        // System.out.println("listening for messages");
                        Message message = api.waitForNewMessages();
                        if (message != null) {
                            System.out.println(api.getUsernameFromId(message.getOrigin()) + " -> " + message.getContent());
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

}
