/*
 * Music Streaming (Spotify-like) - Low Level Design
 * --------------------------------------------------------------
 * Builds playlists from a song library and plays them through a queue that
 * supports next/previous, three repeat modes (OFF / ALL / ONE), and a
 * seeded shuffle so the demo is fully deterministic. The player reports the
 * current "now playing" track at each step.
 *
 * Key classes / abstractions:
 *   - Song / Playlist : library items and ordered collections.
 *   - RepeatMode      : OFF / ALL / ONE playback behavior.
 *   - Player          : queue + cursor, next/prev/shuffle/repeat.
 *
 * Patterns used:
 *   - Strategy (RepeatMode drives advance behavior)
 *   - Iterator-like cursor over the play queue
 */

import java.util.*;

public class MusicStreamingSpotifyDemo {
    public static void main(String[] args) {
        Song s1 = new Song("Bohemian Rhapsody", "Queen", 354);
        Song s2 = new Song("Imagine", "John Lennon", 183);
        Song s3 = new Song("Hotel California", "Eagles", 391);
        Song s4 = new Song("Billie Jean", "Michael Jackson", 294);
        Song s5 = new Song("Smells Like Teen Spirit", "Nirvana", 301);

        Playlist playlist = new Playlist("Classics");
        playlist.add(s1); playlist.add(s2); playlist.add(s3); playlist.add(s4); playlist.add(s5);

        Player player = new Player();
        player.load(playlist);

        System.out.println("=== Sequential playback ===");
        player.play();
        player.next();
        player.next();
        player.previous();

        System.out.println("\n=== Repeat ONE ===");
        player.setRepeatMode(RepeatMode.ONE);
        player.next(); // stays on same track
        player.next();

        System.out.println("\n=== Repeat ALL (wrap at end) ===");
        player.setRepeatMode(RepeatMode.ALL);
        player.jumpToLast();
        player.next(); // wraps to first

        System.out.println("\n=== Seeded shuffle (deterministic) ===");
        player.setRepeatMode(RepeatMode.OFF);
        player.shuffle(42L);
        player.play();
        player.next();
        player.next();
        player.next();
        player.next();
        player.next(); // end of queue
    }
}

class Song {
    private final String title;
    private final String artist;
    private final int seconds;

    public Song(String title, String artist, int seconds) {
        this.title = title;
        this.artist = artist;
        this.seconds = seconds;
    }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }

    @Override
    public String toString() {
        return "\"" + title + "\" by " + artist + " (" + seconds + "s)";
    }
}

class Playlist {
    private final String name;
    private final List<Song> songs = new ArrayList<>();

    public Playlist(String name) { this.name = name; }
    public void add(Song song) { songs.add(song); }
    public List<Song> getSongs() { return songs; }
    public String getName() { return name; }
}

enum RepeatMode { OFF, ALL, ONE }

class Player {
    private List<Song> queue = new ArrayList<>();
    private int cursor = -1;
    private RepeatMode repeatMode = RepeatMode.OFF;

    public void load(Playlist playlist) {
        this.queue = new ArrayList<>(playlist.getSongs());
        this.cursor = -1;
        System.out.println("Loaded playlist '" + playlist.getName() + "' with " + queue.size() + " songs");
    }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        System.out.println("Repeat mode = " + mode);
    }

    public void play() {
        if (queue.isEmpty()) { System.out.println("  Queue empty"); return; }
        if (cursor < 0) cursor = 0;
        announce();
    }

    public void next() {
        if (queue.isEmpty()) { System.out.println("  Queue empty"); return; }
        switch (repeatMode) {
            case ONE:
                // stay on current track
                break;
            case ALL:
                cursor = (cursor + 1) % queue.size();
                break;
            case OFF:
            default:
                if (cursor + 1 >= queue.size()) {
                    System.out.println("  End of queue");
                    return;
                }
                cursor++;
                break;
        }
        announce();
    }

    public void previous() {
        if (queue.isEmpty()) { System.out.println("  Queue empty"); return; }
        if (cursor <= 0) {
            cursor = (repeatMode == RepeatMode.ALL) ? queue.size() - 1 : 0;
        } else {
            cursor--;
        }
        announce();
    }

    public void jumpToLast() {
        if (!queue.isEmpty()) cursor = queue.size() - 1;
    }

    public void shuffle(long seed) {
        Collections.shuffle(queue, new Random(seed));
        cursor = -1;
        System.out.println("Shuffled queue (seed " + seed + "):");
        for (Song s : queue) System.out.println("  - " + s);
    }

    private void announce() {
        System.out.println("  Now playing [" + (cursor + 1) + "/" + queue.size() + "]: " + queue.get(cursor));
    }
}
