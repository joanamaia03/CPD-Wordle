import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Game{
    private static int gameID;

    public Game(int gameID) {
        this.gameID = gameID;
    }

    public int getGameID() {
        return gameID;
    }
    // Create a lock
    private static final ReentrantLock lock = new ReentrantLock();

    public static List<Player> wordle(List<Player> number_players, String word, int gameID)  {

        final String BG_GREEN = "\u001b[42m";
        final String BG_YELLOW = "\u001b[43m";
        final String RESET = "\u001b[0m";

        System.out.println("Game Started");

        Map<Player, Integer> attemptsPerPlayer = new HashMap<>();

        for (Player player : number_players) {
            attemptsPerPlayer.put(player, 0);
        }

        //int attempts = 0;
        boolean winner = false;
        Set<Player> winningPlayers = new HashSet<>();

        // Notify players of the game ID
        for (Player player : number_players) {
            try {
                Socket socket = player.getSocket();
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("Game ID: " + gameID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //6 guesses per player
        while(attemptsPerPlayer.values().stream().mapToInt(Integer::intValue).sum() < number_players.size() * 6 && !winner) {
            for(Player player: number_players) {
                if (attemptsPerPlayer.get(player) > 6) {
                    break;
                }

                try {
                    Socket socket = player.getSocket();
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String guess;
                    do {
                        out.println("Make your guess: ");
                        guess = in.readLine().toUpperCase();
                        if (guess.length() != 5) { out.println("Please write a 5 letter word!");}

                    } while (guess.length() != 5);

                    // Increment attempts for the player
                    lock.lock();
                    try {
                        attemptsPerPlayer.put(player, attemptsPerPlayer.get(player) + 1);
                    }
                    finally {
                        lock.unlock();
                    }

                    StringBuilder result = new StringBuilder();

                    for (int j = 0; j < 5; j++) {
                        //letter matches
                        if (guess.charAt(j) == word.charAt(j)) {
                            result.append(BG_GREEN).append(guess.charAt(j)).append(RESET);
                        }
                        //letter on the wrong place
                        else if (word.contains(Character.toString(guess.charAt(j)))) {
                            result.append(BG_YELLOW).append(guess.charAt(j)).append(RESET);
                        }
                        //letter doesn't exist
                        else {
                            result.append(guess.charAt(j));
                        }
                    }
                    out.println("Guess: " + result);
                    out.println("Attempts left: " + (6 - attemptsPerPlayer.get(player)));
                    if (guess.equals(word)) {
                        lock.lock();
                        try{
                            winningPlayers.add(player);
                        }
                        finally {
                            lock.unlock();
                        }

                        player.incrementScore();
                        winner = true;
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        // Determine the message to send
        String endMessage = null;
        if (winningPlayers.size() > 1) {
            endMessage = "IT'S A TIE!";
        } else if (winningPlayers.size() == 1) {
            endMessage = "YOU WON!";

        }

        for (Player player: number_players){
            try{
                Socket socket = player.getSocket();
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("Game over. Your score: " + player.getScore());
                if (winningPlayers.contains(player)) {
                    out.println(endMessage);
                }
                socket.close();
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }
    return number_players;
    }
}




