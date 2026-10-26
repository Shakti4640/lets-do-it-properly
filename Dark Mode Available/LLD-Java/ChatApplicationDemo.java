/*
 * Chat Application (WhatsApp-like) - Low Level Design
 * ---------------------------------------------------
 * 1:1 and group messaging using the Mediator pattern: a ChatServer
 * mediates message delivery between users, with delivery status tracking.
 *
 * Key classes:
 *   - User
 *   - Message (status: SENT/DELIVERED/READ)
 *   - ChatRoom / GroupChat
 *   - ChatServer (mediator)
 *
 * Patterns used: Mediator (server), Observer-ish delivery, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatApplicationDemo {
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        User alice = server.register("alice");
        User bob = server.register("bob");
        User carol = server.register("carol");

        System.out.println("=== Chat Application Demo ===");
        // 1:1
        server.sendDirect("alice", "bob", "Hey Bob!");
        server.sendDirect("bob", "alice", "Hi Alice!");

        // Group
        GroupChat group = server.createGroup("Friends", "alice");
        group.addMember(bob);
        group.addMember(carol);
        server.sendGroup("alice", "Friends", "Welcome to the group!");

        System.out.println("--- Bob's inbox ---");
        bob.printInbox();
        System.out.println("--- Carol's inbox ---");
        carol.printInbox();
    }
}

enum MessageStatus { SENT, DELIVERED, READ }

class Message {
    final String from; final String to; final String text;
    MessageStatus status = MessageStatus.SENT;
    Message(String from, String to, String text) { this.from = from; this.to = to; this.text = text; }
    public String toString() { return from + ": " + text + " [" + status + "]"; }
}

class User {
    final String id;
    private final List<Message> inbox = new ArrayList<Message>();
    User(String id) { this.id = id; }
    void receive(Message m) { m.status = MessageStatus.DELIVERED; inbox.add(m); }
    void printInbox() { for (Message m : inbox) System.out.println("  " + m); }
}

class GroupChat {
    final String name; final String admin;
    final List<User> members = new ArrayList<User>();
    GroupChat(String name, String admin) { this.name = name; this.admin = admin; }
    void addMember(User u) { members.add(u); }
}

class ChatServer {
    private final Map<String, User> users = new HashMap<String, User>();
    private final Map<String, GroupChat> groups = new HashMap<String, GroupChat>();

    User register(String id) {
        User u = new User(id);
        users.put(id, u);
        System.out.println("Registered " + id);
        return u;
    }

    void sendDirect(String fromId, String toId, String text) {
        User to = users.get(toId);
        if (to == null) { System.out.println("No such user: " + toId); return; }
        Message m = new Message(fromId, toId, text);
        to.receive(m);
        System.out.println(fromId + " -> " + toId + ": " + text);
    }

    GroupChat createGroup(String name, String adminId) {
        GroupChat g = new GroupChat(name, adminId);
        g.addMember(users.get(adminId));
        groups.put(name, g);
        System.out.println("Group '" + name + "' created by " + adminId);
        return g;
    }

    void sendGroup(String fromId, String groupName, String text) {
        GroupChat g = groups.get(groupName);
        if (g == null) { System.out.println("No such group"); return; }
        System.out.println(fromId + " -> [" + groupName + "]: " + text);
        for (User u : g.members) {
            if (!u.id.equals(fromId)) u.receive(new Message(fromId, groupName, text));
        }
    }
}
