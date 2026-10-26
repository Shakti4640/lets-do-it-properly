/*
 * Multiplayer Game Lobby - Low Level Design
 * -----------------------------------------
 * Players create or join a lobby, toggle ready state, and once the lobby is
 * full the matchmaker splits them into two skill-balanced teams (greedy by
 * descending rating). The game starts only when the lobby is full and all
 * players are ready.
 *
 * Key classes / abstractions:
 *   - Player, Lobby (+ LobbyState enum)
 *   - Team, Match
 *   - Matchmaker (balanced team split strategy)
 *   - LobbyManager (facade)
 *
 * Patterns used: State (LobbyState), Strategy (Matchmaker), Facade (LobbyManager).
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiplayerGameLobbyDemo {
    public static void main(String[] args) {
        System.out.println("=== Multiplayer Game Lobby Demo ===");
        LobbyManager manager = new LobbyManager(new BalancedMatchmaker());

        Lobby lobby = manager.createLobby("L1", 4); // 4 players => 2v2
        System.out.println("Created lobby " + lobby.id + " capacity=" + lobby.capacity + " state=" + lobby.state);

        manager.join("L1", new Player("alice", 1500));
        manager.join("L1", new Player("bob", 1200));
        manager.join("L1", new Player("carol", 1800));
        manager.join("L1", new Player("dave", 1000));
        System.out.println("Players joined: " + lobby.playerNames());

        try {
            manager.join("L1", new Player("erin", 1100));
        } catch (IllegalStateException e) {
            System.out.println("erin join failed: " + e.getMessage());
        }

        System.out.println("\n-- Ready up --");
        manager.ready("L1", "alice");
        manager.ready("L1", "bob");
        manager.ready("L1", "carol");
        System.out.println("All ready? " + lobby.allReady() + " -> cannot start yet");
        System.out.println("start attempt: " + safeStart(manager, "L1"));

        manager.ready("L1", "dave");
        System.out.println("\n-- All ready, start match --");
        Match match = manager.start("L1");
        System.out.println("Lobby state: " + lobby.state);
        printTeam(match.teamA);
        printTeam(match.teamB);
        System.out.println("Rating diff between teams: " + Math.abs(match.teamA.totalRating() - match.teamB.totalRating()));
    }

    static String safeStart(LobbyManager m, String id) {
        try { m.start(id); return "started"; }
        catch (IllegalStateException e) { return "blocked (" + e.getMessage() + ")"; }
    }

    static void printTeam(Team t) {
        System.out.println("  " + t.name + " (total=" + t.totalRating() + "): " + t.memberSummary());
    }
}

enum LobbyState { WAITING, FULL, IN_GAME }

class Player {
    final String name;
    final int rating;
    boolean ready = false;
    Player(String name, int rating) { this.name = name; this.rating = rating; }
}

class Lobby {
    final String id;
    final int capacity;
    LobbyState state = LobbyState.WAITING;
    final List<Player> players = new ArrayList<Player>();
    Lobby(String id, int capacity) { this.id = id; this.capacity = capacity; }

    boolean isFull() { return players.size() == capacity; }
    boolean allReady() {
        if (!isFull()) return false;
        for (Player p : players) if (!p.ready) return false;
        return true;
    }
    Player find(String name) {
        for (Player p : players) if (p.name.equals(name)) return p;
        return null;
    }
    List<String> playerNames() {
        List<String> names = new ArrayList<String>();
        for (Player p : players) names.add(p.name);
        return names;
    }
}

class Team {
    final String name;
    final List<Player> members = new ArrayList<Player>();
    Team(String name) { this.name = name; }
    int totalRating() {
        int sum = 0;
        for (Player p : members) sum += p.rating;
        return sum;
    }
    String memberSummary() {
        List<String> parts = new ArrayList<String>();
        for (Player p : members) parts.add(p.name + "(" + p.rating + ")");
        return parts.toString();
    }
}

class Match {
    final Team teamA;
    final Team teamB;
    Match(Team teamA, Team teamB) { this.teamA = teamA; this.teamB = teamB; }
}

interface Matchmaker {
    Match makeTeams(List<Player> players);
}

// Greedy balancing: sort by rating desc, assign each player to the lower-total team.
class BalancedMatchmaker implements Matchmaker {
    public Match makeTeams(List<Player> players) {
        List<Player> sorted = new ArrayList<Player>(players);
        Collections.sort(sorted, new Comparator<Player>() {
            public int compare(Player a, Player b) { return b.rating - a.rating; }
        });
        Team a = new Team("Team-A");
        Team b = new Team("Team-B");
        for (Player p : sorted) {
            if (a.totalRating() <= b.totalRating()) a.members.add(p);
            else b.members.add(p);
        }
        return new Match(a, b);
    }
}

class LobbyManager {
    private final Matchmaker matchmaker;
    private final Map<String, Lobby> lobbies = new HashMap<String, Lobby>();
    LobbyManager(Matchmaker matchmaker) { this.matchmaker = matchmaker; }

    Lobby createLobby(String id, int capacity) {
        Lobby lobby = new Lobby(id, capacity);
        lobbies.put(id, lobby);
        return lobby;
    }

    void join(String lobbyId, Player player) {
        Lobby lobby = require(lobbyId);
        if (lobby.isFull()) throw new IllegalStateException("lobby is full");
        lobby.players.add(player);
        if (lobby.isFull()) lobby.state = LobbyState.FULL;
    }

    void ready(String lobbyId, String playerName) {
        Player p = require(lobbyId).find(playerName);
        if (p == null) throw new IllegalArgumentException("player not in lobby: " + playerName);
        p.ready = true;
    }

    Match start(String lobbyId) {
        Lobby lobby = require(lobbyId);
        if (!lobby.isFull()) throw new IllegalStateException("lobby not full");
        if (!lobby.allReady()) throw new IllegalStateException("not all players ready");
        lobby.state = LobbyState.IN_GAME;
        return matchmaker.makeTeams(lobby.players);
    }

    private Lobby require(String lobbyId) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) throw new IllegalArgumentException("No such lobby: " + lobbyId);
        return lobby;
    }
}
