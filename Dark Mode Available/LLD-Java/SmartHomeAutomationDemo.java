/*
 * Smart Home Automation - Low Level Design
 * ----------------------------------------
 * Devices (light, thermostat, lock) implement a common Device interface and
 * notify observers on state changes. Scenes/routines are ordered Commands that
 * drive devices. Rules map a trigger event to a routine so the home reacts
 * automatically.
 *
 * Key classes / abstractions:
 *   - Device interface (+ Light, Thermostat, DoorLock)
 *   - DeviceObserver (state-change logging)
 *   - DeviceCommand (Command) + Scene (routine)
 *   - Rule (trigger -> scene), SmartHomeHub (facade)
 *
 * Patterns used: Command (DeviceCommand/Scene), Observer (DeviceObserver), Facade (SmartHomeHub).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartHomeAutomationDemo {
    public static void main(String[] args) {
        System.out.println("=== Smart Home Automation Demo ===");

        DeviceObserver logger = (device, change) ->
                System.out.println("   [event] " + device + " -> " + change);

        Light livingLight = new Light("living-room-light");
        Thermostat thermostat = new Thermostat("hallway-thermostat");
        DoorLock frontDoor = new DoorLock("front-door");
        for (Device d : new Device[]{livingLight, thermostat, frontDoor}) d.addObserver(logger);

        SmartHomeHub hub = new SmartHomeHub();
        hub.register(livingLight);
        hub.register(thermostat);
        hub.register(frontDoor);

        // Define a "Good Night" scene as an ordered list of commands.
        Scene goodNight = new Scene("Good Night");
        goodNight.add(new SetLightCommand(livingLight, false));
        goodNight.add(new SetTempCommand(thermostat, 18));
        goodNight.add(new LockCommand(frontDoor, true));

        // Define an "Arrive Home" scene.
        Scene arriveHome = new Scene("Arrive Home");
        arriveHome.add(new LockCommand(frontDoor, false));
        arriveHome.add(new SetLightCommand(livingLight, true));
        arriveHome.add(new SetTempCommand(thermostat, 23));

        hub.addRule(new Rule("DOOR_APPROACH", arriveHome));
        hub.addRule(new Rule("BEDTIME", goodNight));

        System.out.println("\n-- Trigger: DOOR_APPROACH --");
        hub.fire("DOOR_APPROACH");

        System.out.println("\n-- Trigger: BEDTIME --");
        hub.fire("BEDTIME");

        System.out.println("\n-- Final device states --");
        for (Device d : hub.devices()) System.out.println("  " + d.status());
    }
}

interface DeviceObserver {
    void onStateChange(String device, String change);
}

interface Device {
    String id();
    String status();
    void addObserver(DeviceObserver observer);
    void onStateChange(String device, String change); // helper to notify
}

abstract class AbstractDevice implements Device {
    private final String id;
    private final List<DeviceObserver> observers = new ArrayList<DeviceObserver>();
    AbstractDevice(String id) { this.id = id; }
    public String id() { return id; }
    public void addObserver(DeviceObserver observer) { observers.add(observer); }
    public void onStateChange(String device, String change) {
        for (DeviceObserver o : observers) o.onStateChange(device, change);
    }
    protected void notifyChange(String change) { onStateChange(id, change); }
    public String toString() { return id; }
}

class Light extends AbstractDevice {
    private boolean on = false;
    Light(String id) { super(id); }
    void setOn(boolean on) { this.on = on; notifyChange(on ? "ON" : "OFF"); }
    public String status() { return id() + ": " + (on ? "ON" : "OFF"); }
}

class Thermostat extends AbstractDevice {
    private int celsius = 21;
    Thermostat(String id) { super(id); }
    void setTemperature(int c) { this.celsius = c; notifyChange(c + "C"); }
    public String status() { return id() + ": " + celsius + "C"; }
}

class DoorLock extends AbstractDevice {
    private boolean locked = true;
    DoorLock(String id) { super(id); }
    void setLocked(boolean locked) { this.locked = locked; notifyChange(locked ? "LOCKED" : "UNLOCKED"); }
    public String status() { return id() + ": " + (locked ? "LOCKED" : "UNLOCKED"); }
}

interface DeviceCommand {
    void execute();
}

class SetLightCommand implements DeviceCommand {
    private final Light light; private final boolean on;
    SetLightCommand(Light light, boolean on) { this.light = light; this.on = on; }
    public void execute() { light.setOn(on); }
}

class SetTempCommand implements DeviceCommand {
    private final Thermostat thermostat; private final int celsius;
    SetTempCommand(Thermostat thermostat, int celsius) { this.thermostat = thermostat; this.celsius = celsius; }
    public void execute() { thermostat.setTemperature(celsius); }
}

class LockCommand implements DeviceCommand {
    private final DoorLock lock; private final boolean locked;
    LockCommand(DoorLock lock, boolean locked) { this.lock = lock; this.locked = locked; }
    public void execute() { lock.setLocked(locked); }
}

class Scene {
    final String name;
    private final List<DeviceCommand> commands = new ArrayList<DeviceCommand>();
    Scene(String name) { this.name = name; }
    void add(DeviceCommand command) { commands.add(command); }
    void run() {
        System.out.println(" running scene '" + name + "'");
        for (DeviceCommand c : commands) c.execute();
    }
}

class Rule {
    final String trigger;
    final Scene scene;
    Rule(String trigger, Scene scene) { this.trigger = trigger; this.scene = scene; }
}

class SmartHomeHub {
    private final Map<String, Device> registry = new HashMap<String, Device>();
    private final List<Device> ordered = new ArrayList<Device>();
    private final Map<String, Rule> rules = new HashMap<String, Rule>();

    void register(Device device) { registry.put(device.id(), device); ordered.add(device); }
    void addRule(Rule rule) { rules.put(rule.trigger, rule); }

    void fire(String trigger) {
        Rule rule = rules.get(trigger);
        if (rule == null) { System.out.println(" no rule for trigger " + trigger); return; }
        rule.scene.run();
    }

    List<Device> devices() { return ordered; }
}
