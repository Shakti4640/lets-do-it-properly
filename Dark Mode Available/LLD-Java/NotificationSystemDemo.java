/*
 * Notification System - Low Level Design
 * --------------------------------------
 * Sends notifications across multiple channels (Email, SMS, Push) using
 * the Observer pattern for subscribers and Strategy/Factory for channels.
 *
 * Key classes:
 *   - Channel (+ Email/SMS/Push)         : Strategy for delivery
 *   - NotificationChannelFactory          : Factory
 *   - User (channel preferences)
 *   - NotificationService (facade)
 *
 * Patterns used: Strategy, Factory, Facade.
 */

import java.util.ArrayList;
import java.util.List;

public class NotificationSystemDemo {
    public static void main(String[] args) {
        NotificationService service = new NotificationService();
        User alice = new User("Alice", "alice@x.com", "9990001111");
        alice.addChannel(ChannelType.EMAIL);
        alice.addChannel(ChannelType.PUSH);

        User bob = new User("Bob", "bob@x.com", "8880002222");
        bob.addChannel(ChannelType.SMS);

        System.out.println("=== Notification System Demo ===");
        service.notify(alice, "Your order has shipped!");
        service.notify(bob, "OTP: 123456");
    }
}

enum ChannelType { EMAIL, SMS, PUSH }

interface Channel {
    void send(User user, String message);
}

class EmailChannel implements Channel {
    public void send(User user, String message) {
        System.out.println("EMAIL -> " + user.getEmail() + ": " + message);
    }
}
class SmsChannel implements Channel {
    public void send(User user, String message) {
        System.out.println("SMS   -> " + user.getPhone() + ": " + message);
    }
}
class PushChannel implements Channel {
    public void send(User user, String message) {
        System.out.println("PUSH  -> " + user.getName() + "'s device: " + message);
    }
}

class NotificationChannelFactory {
    static Channel create(ChannelType type) {
        switch (type) {
            case EMAIL: return new EmailChannel();
            case SMS:   return new SmsChannel();
            case PUSH:  return new PushChannel();
            default:    throw new IllegalArgumentException("Unknown channel");
        }
    }
}

class User {
    private final String name; private final String email; private final String phone;
    private final List<ChannelType> channels = new ArrayList<ChannelType>();
    User(String name, String email, String phone) { this.name = name; this.email = email; this.phone = phone; }
    String getName() { return name; }
    String getEmail() { return email; }
    String getPhone() { return phone; }
    void addChannel(ChannelType c) { channels.add(c); }
    List<ChannelType> getChannels() { return channels; }
}

class NotificationService {
    void notify(User user, String message) {
        System.out.println("Notifying " + user.getName() + " on " + user.getChannels());
        for (ChannelType type : user.getChannels()) {
            Channel channel = NotificationChannelFactory.create(type);
            channel.send(user, message);
        }
    }
}
