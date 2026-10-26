/*
 * Traffic Light System - Low Level Design
 * ---------------------------------------
 * Controls signals at an intersection. Each signal cycles through
 * GREEN -> YELLOW -> RED using the State pattern; a controller advances
 * the active direction in round-robin.
 *
 * Patterns used: State (light transitions), Mediator-ish controller.
 */

import java.util.ArrayList;
import java.util.List;

public class TrafficLightSystemDemo {
    public static void main(String[] args) {
        IntersectionController controller = new IntersectionController();
        controller.addSignal(new TrafficSignal("NORTH"));
        controller.addSignal(new TrafficSignal("EAST"));
        controller.addSignal(new TrafficSignal("SOUTH"));
        controller.addSignal(new TrafficSignal("WEST"));

        System.out.println("=== Traffic Light Demo ===");
        for (int i = 0; i < 6; i++) {
            System.out.println("-- tick " + i + " --");
            controller.tick();
        }
    }
}

enum LightColor { RED, GREEN, YELLOW }

interface SignalState {
    void next(TrafficSignal signal);
    LightColor color();
}

class GreenState implements SignalState {
    public void next(TrafficSignal s) { s.setState(new YellowState()); }
    public LightColor color() { return LightColor.GREEN; }
}
class YellowState implements SignalState {
    public void next(TrafficSignal s) { s.setState(new RedState()); }
    public LightColor color() { return LightColor.YELLOW; }
}
class RedState implements SignalState {
    public void next(TrafficSignal s) { s.setState(new GreenState()); }
    public LightColor color() { return LightColor.RED; }
}

class TrafficSignal {
    private final String direction;
    private SignalState state = new RedState();
    TrafficSignal(String direction) { this.direction = direction; }
    String getDirection() { return direction; }
    void setState(SignalState s) { this.state = s; }
    LightColor getColor() { return state.color(); }
    void advance() { state.next(this); }
    void forceGreen() { state = new GreenState(); }
    void forceRed() { state = new RedState(); }
}

class IntersectionController {
    private final List<TrafficSignal> signals = new ArrayList<TrafficSignal>();
    private int activeIndex = 0;

    void addSignal(TrafficSignal s) { signals.add(s); }

    void tick() {
        if (signals.isEmpty()) return;
        // Active signal advances its light; when it turns RED, hand over.
        TrafficSignal active = signals.get(activeIndex);
        if (active.getColor() == LightColor.RED) {
            active.forceGreen();
        } else {
            active.advance();
        }
        if (active.getColor() == LightColor.RED) {
            activeIndex = (activeIndex + 1) % signals.size();
            signals.get(activeIndex).forceGreen();
        }
        for (TrafficSignal s : signals) {
            System.out.println(s.getDirection() + ": " + s.getColor());
        }
    }
}
