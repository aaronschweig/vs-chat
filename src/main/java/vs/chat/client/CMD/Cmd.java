package vs.chat.client.CMD;

import vs.chat.client.ClientApiImpl;
import vs.chat.client.exceptions.LoginException;
import vs.chat.entities.Chat;
import vs.chat.entities.Message;
import vs.chat.entities.User;

import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Cmd {

    private ClientApiImpl api;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");
    private UUID chatOpen = null;

    public Cmd(ClientApiImpl api) {
        this.api = api;
        this.startCommandLineClient();
    }

    private void startCommandLineClient() {
        this.login();
        System.out.println();
        this.printHelp();
        while (true) {
            try {
                System.out.print("> ");
                String userInput = this.api.getUserIn().readLine();

                switch (userInput) {
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
                        System.out.println("Command not found! -> /help");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void onCreateChat(Chat chat) {
        System.out.println("Created chat '" + chat.getName() + "'");
        System.out.print("> ");
    }

    private void onGetChatMessages(Set<Message> messages) {
        for (Message message: messages) {
            System.out.println(this.api.getUsernameFromId(message.getOrigin()) + ": " + dateFormat.format(message.getReceiveTime()) + " -> " + message.getContent());
        }
    }

    private void onMessage(Message message) {
        if (this.chatOpen != null) {
            if (this.chatOpen.equals(message.getTarget())) {
                System.out.println(this.api.getUsernameFromId(message.getOrigin()) + ": " + dateFormat.format(message.getReceiveTime()) + " -> " + message.getContent());
            }
        } else {
            System.out.println("1 Neue Nachricht von " + this.api.getUsernameFromId(message.getOrigin()));
            System.out.print("> ");
        }
    }

    private void login() {
        try {
            while (true) {
                System.out.print("Username: ");
                String username = this.api.getUserIn().readLine();

                System.out.print("Password: ");
                String password = this.api.getUserIn().readLine();

                try {
                    this.api.login(username, password);
                    break;
                } catch (LoginException e) {
                    System.out.println("Username or password incorrect!");
                }
            }

            System.out.println("Logged in as '" + this.api.getUsernameFromId(this.api.getUserId()) + "'");

            this.api.startPacketListener(this::onCreateChat, this::onGetChatMessages, this::onMessage);

        } catch (IOException e) {
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
        System.out.println("/exit -- exit application\n");
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

    private void onTimeout() {
        System.out.println("Chat konnte nicht erstellt werden!");
    }

    private void createChat() {
        try {
            System.out.print("Chatname: ");
            String chatname = this.api.getUserIn().readLine();

            int amountChatUsers;

            List<UUID> users = new ArrayList<>();

            do {
                System.out.print("Number of users to add (at least 1): ");
                String userIn = this.api.getUserIn().readLine();
                amountChatUsers = Integer.parseInt(userIn);
            } while (amountChatUsers < 1);

            for (int i = 0; i < amountChatUsers; i++) {

                User user;

                do {
                    System.out.print((i + 1) + ". User (username): ");
                    String username = this.api.getUserIn().readLine();

                    user = this.api.getContacts().stream().filter(c -> c.getUsername().equals(username)).findAny().orElse(null);

                    if (user == null) {
                        System.out.println("User not found!");
                    }

                } while (user == null);

                users.add(user.getId());
            }

            this.api.exchangeKeys(chatname, users, this::onTimeout);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openChat() {
        try {
            Chat chat;

            do {
                System.out.print("Chatname: ");
                String chatname = this.api.getUserIn().readLine();
                chat = this.api.getChats().stream().filter(c -> c.getName().equals(chatname)).findAny().orElse(null);

                if (chat == null) {
                    System.out.println("Chat not found!");
                }
            } while (chat == null);

            this.chatOpen = chat.getId();

            BigInteger chatKey = this.api.loadKey(chat.getId());
            System.out.println("Chat id von " + chat.getName() + " ist " + chatKey);

            System.out.println("Opened chat '" + chat.getName() + "' (type /quit to exit chat window)\n");

            this.api.getChatMessages(chat.getId());

            while (true) {
                String message = this.api.getUserIn().readLine();

                if (message.equals("/quit")) {
                    break;
                }

                this.api.sendMessage(message, chat.getId());
            }

            this.chatOpen = null;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
