import java.io.*;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


import static java.lang.Integer.parseInt;

public class Server {

    private final int port;
    private ExecutorService executorService = Executors.newCachedThreadPool(); // Thread pool for game instances
    private AtomicInteger activeGameCount = new AtomicInteger(0); // Counter to keep track of active game instances
    private final long TIMEOUT = 5000;
    private ServerSocket serverSocket;
    private final Scanner console;
    private final Random random;
    public static ArrayList<String> words;
    private List<Player> players;
    private final HashMap<User,Player> user_player_map;
    private final HashMap<String,Player> token_player_map; //matches tokens to players
    private final HashMap<String,String> user_token_map; //matches tokens to users who have a place in the queue
    private Queue<String> queue; //queue of tokens waiting to play
    final List<User> users;
    private final List<User> authenticatedUsers;
    final String BG_GREEN = "\u001b[42m";
    final String BG_YELLOW = "\u001b[43m";
    final String RESET = "\u001b[0m";
    private int playMode; // 1 for simple, 2 for ranked
    private ScheduledExecutorService scheduler;


    private int nextGameID = 1; // Counter to generate unique game IDs

    // Create a lock
    private final ReentrantLock lock = new ReentrantLock();

    public Server(int port) throws Exception {
        this.port = port;
        console = new Scanner(System.in);
        random = new Random();
        users = new ArrayList<>();
        players = new ArrayList<>();
        authenticatedUsers = new ArrayList<>();
        user_player_map = new HashMap<>();
        queue = new LinkedList<>();
        token_player_map = new HashMap<>();
        user_token_map = new HashMap<>();
        scheduler = Executors.newScheduledThreadPool(1);

        listWords("../resources/words.txt");
        getUsersFromFile("../resources/users.txt");

        start();
    }

    class GameThread implements Runnable {
        private List<Player> players;
        private int gameID;

        public GameThread(List<Player> players, int gameID) {
            this.players = players;
            this.gameID = gameID;
        }

        @Override
        public void run() {
            updateScores();

            startGame(players, gameID);
            updateScores();

            //if game is finished, decrement active game count
            activeGameCount.decrementAndGet();

        }
}


    public void start() throws Exception {
        this.serverSocket = ServerSocketChannel.open().socket();
        serverSocket.bind(new InetSocketAddress(this.port));
        System.out.println("Server is on port " + this.port);

        updateScores();
        setPlayMode();

        //System.out.println(BG_GREEN + "Creating New Lobby" + RESET);

        // Getting lobby size from server manager
        System.out.println("\"Enter\" for Lobby Size (2 or 3) or \"q\" to close server");
        String lobbySizeInput = console.nextLine();

        // Checking if code is a quit event
        if (lobbySizeInput.equals("q")) {
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter Lobby Size: ");
        String lobbySizeStr = scanner.nextLine();

        try{
            if(parseInt(lobbySizeStr) < 2 || parseInt(lobbySizeStr) > 3){
                System.out.println(BG_YELLOW + "Invalid Lobby Size" + RESET);
            }
        }
        catch(NumberFormatException e){
            System.out.println(BG_YELLOW + "Invalid Input" + RESET);
        }

        //Ask server how many game instances it can handle
        System.out.println("Enter the maximum number of lobbies running concurrently: ");
        int maxLobbies = scanner.nextInt();


        int lobbySize = parseInt(lobbySizeStr);

        scheduler.scheduleAtFixedRate(() -> gameStart(lobbySize, maxLobbies), 0, 10, TimeUnit.MILLISECONDS);

        // Infinite loop to accept new client connections
        while (true) {


            // Accept a new client connection
            Socket socket = serverSocket.accept();
            System.out.println("Waiting for client...");

            // Handle the client connection in a new thread
            new Thread(() -> {
                try {
                    // Authenticate the user
                    Player player = authenticateUser(socket);

                    // If the user is authenticated, add them to the list of players
                    if (player != null) {
                        lock.lock();
                        try {
                            players.add(player);

                        } finally {
                            lock.unlock();
                        }
                        System.out.println(player.getName() + " Joined");
                        if (playMode == 2) {
                            // Sort players by score
                            lock.lock();
                            try {
                                players.sort(Comparator.comparingInt(Player::getLevel).reversed());
                                //sort queue with players tokens
                                queue.clear();
                                for (Player p : players) {
                                    for (String token : token_player_map.keySet()) {
                                        if (token_player_map.get(token).getName().equals(p.getName())) {
                                            queue.add(token);
                                        }
                                    }
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                        gameStart(lobbySize, maxLobbies);
                    } else {
                        System.out.println("Failed to authenticate user");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();


        }
    }


    public synchronized void gameStart(int lobbySize, int maxLobbies){
        updateScores();
        {
            while (queue.size() >= lobbySize && activeGameCount.get() < maxLobbies) {
                int gameID;
                gameID = nextGameID++;
                List<Player> matchedPlayers = new ArrayList<>();

                //get lobbysize number of players from players list
                for (int i = 0; i < lobbySize; i++) {

                    //get token from queue
                    String token = queue.poll();
                    //remove token players.getFirst() from map

                    Player matchedPlayer = token_player_map.get(token);
                    matchedPlayers.add(matchedPlayer);
                    //remove head from queue
                    queue.remove(token);

                }
                GameThread gameThread = new GameThread(matchedPlayers, gameID);
                activeGameCount.incrementAndGet();
                executorService.execute(gameThread);
            }
        }}
    public void setPlayMode() {
        while (true){
            System.out.println("Choose a playing mode:");
            System.out.println("1. Simple");
            System.out.println("2. Ranked");

            System.out.println(" ");
            System.out.print("Choice: ");
            String input = console.nextLine();

            switch (input) {
                case "1":
                    playMode = 1;
                    System.out.println("Playing mode set to Simple.");

                    return;
                case "2":
                    playMode = 2;
                    System.out.println("Playing mode set to Ranked.");
                    return;
                default:
                    System.out.println("Invalid input. Please enter a number between 1 and 2.");
            }
        }
    }

/*
    private void matchPlayers(List<Player> players, int lobbySize) {

        if (lobbySize == 2) {

            Player player1 = players.get(0);
            Player player2 = players.get(1);
            List<Player> matchedPlayers = new ArrayList<>();
            lock.lock();
            try {
                matchedPlayers.add(player1);
                matchedPlayers.add(player2);
            }
            finally {
                lock.unlock();
            }
            startGame(matchedPlayers);
        }
        else {

            //group players in list of lobby size
            List<List<Player>> matchedPlayers = new ArrayList<>();
            for (int i = 0; i < players.size(); i += lobbySize) {
                lock.lock();
                try{
                    matchedPlayers.add(players.subList(i, Math.min(i + lobbySize, players.size())));
                }
                finally {
                    lock.unlock();
                }
            }
            for (List<Player> matchedPlayer : matchedPlayers) {
                startGame(matchedPlayer);
            }
        }
    }
*/
    // Method to get a random word out of the listWords
    public String getWord() {
        return words.get(random.nextInt(words.size())).toUpperCase();
    }

    // Starting the game with the given number of players
    public void startGame(List<Player> numPlayers, int gameID) {
        players = Game.wordle(numPlayers, getWord(),gameID);
    }


    //lists the words that are on the words.txt in an Array List
    public static void listWords(String s) {
        try {
            words = new ArrayList<>(Files.readAllLines(Paths.get(s)));
            System.out.println("Words loaded successfully");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //if user exists and password is correct, return true

    public boolean validateUser(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                if (user.validatePassword(password)) {
                    if (!authenticatedUsers.contains(user)) {
                        lock.lock();
                        try{
                            authenticatedUsers.add(user);
                        }
                        finally {
                            lock.unlock();
                        }

                    }
                    return true;
                }
                else {
                    System.out.println("Incorrect password");
                    return false;
                }
            }
        }
        return false;
    }

    //registers a new user
    public Player registerUser(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        writer.println("User Registration");
        writer.println("Enter username: ");

        //input username
        String username = in.readLine();
        while (true) {
            boolean unique = true;
            for (User user : users) {
                if (user.getUsername().equals(username)) {
                    unique = false;
                    break;
                }
            }
            if (unique) {
                break;
            } else {
               writer.println("Username already exists. Please enter a new username:");
               username = in.readLine();
            }
        }

        writer.println("Enter password: ");
        //input password
        String password = in.readLine();
        //check if password has length between 4 and 16
        while (password.length() < 4 || password.length() > 16) {
            writer.println("Password must be between 4 and 16 characters. Please enter a new password:");
            password = in.readLine();
        }
        User user = new User(username, password, 0);
        Player player = new Player(socket, username, 0);
        lock.lock();
        try {
            authenticatedUsers.add(user);
            users.add(user);
        }
        finally {
            lock.unlock();
        }
        enterQueueFirstTime(player);
        //add user to database file
        try {
            BufferedWriter write = new BufferedWriter(new FileWriter("../resources/users.txt", true));
            write.write("\n"+ username + "," + password + ",0");

            writer.println("User registered and authenticated successfully!");
            writer.println("The game will start shortly. We are matching you with other players.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return player;
    }

    public void getUsersFromFile(String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();

            while (line != null) {
                String[] parts = line.split(",");
                lock.lock();
                try {
                    users.add(new User(parts[0], parts[1], parseInt(parts[2])));
                }
                finally {
                    lock.unlock();
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Player authenticateUser(Socket socket) throws IOException {
        //Socket socket = serverSocket.accept();
        //Socket userSocket = new Socket();
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        Player player = null;

        // This is now waiting for input from the client
        writer.println("Enter username: ");
        String username = in.readLine();

        while (isAuthenticated(username)) { //guarantee that users are not authenticated twice
            writer.println("User already authenticated. Please enter a different username.");
            writer.println("Enter username: ");
            username = in.readLine();

        }
        if(checkUserExists(username)){
            writer.println("Enter password: ");
            String password = in.readLine();
            int tries = 1;
            while(!validateUser(username, password) && tries < 3){ //3 attempts to enter correct password
                writer.println("Incorrect password. Please try again. (Attempts left: " + (3-tries) +   ")");
                writer.println("Enter password: ");
                tries++;
                password = in.readLine();
            }
            if(tries == 3){
                writer.println("Authentication failed! You have exceeded the number of attempts and will be disconnected.");
                socket.close();
                return null;
            }

            else{ //user, password correct, authenticate user

                for (User user : users) {
                    if (user.getUsername().equals(username)) {
                        if(isActive(username)) { //if the player had already connected, connect them again to the game
                            //retrieve token and player
                            //check if username exists in user_token_map and get the player
                            for (String token : user_token_map.keySet()) {
                                if (user_token_map.get(token).equals(username)) {
                                    player = token_player_map.get(token); //token in queue and player in token_player_map
                                }
                            }
                        }
                        else{
                            player = new Player(socket, username, user.getScore());
                            //create a new token for the player
                            enterQueueFirstTime(player);
                        }

                        //user_player_map.put(user, player);

                        writer.println("User authenticated successfully!");
                        writer.println("The game will start shortly. We are matching you with other players.");
                        return player;
                    }
                }
            }
        }
        else{
            writer.println("User does not exist. Would you like to register? (y/n)");
            String response = in.readLine();
            if(response.equalsIgnoreCase("y")){
                return registerUser(socket);
            }
            else{
                writer.println("You must be a registered user to play the game.");
                //TODO:check if recursion works
                authenticateUser(socket);
                return null;
            }
        }
        return null;
    }

    public boolean checkUserExists(String username) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void updateScores() {
        //iterate through the hashmap and update the scores of the users
        for (User user : users) {
            for (Player player : players) {
                if (user.getUsername().equals(player.getName())) {
                    user.setScore(player.getScore());
                }
            }

        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("../resources/users.txt"))) {
            for (User user : users) {
                writer.write(user.getUsername() + "," + user.getPassword() + "," + user.getScore());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void enterQueueFirstTime(Player player) throws IOException {
        String token = new tokenGenerator().nextToken();
        user_token_map.put(token, player.getName());
        token_player_map.put(token, player);
        lock.lock();
        try{
            queue.add(token);
        }
        finally {
            lock.unlock();
        }
    }

    /*
    public synchronized boolean returnToQueue(Player player) {
        //get the player from the token and add them to the queue
        if(token_player_map.containsValue(player)) {
            //queue.add(player);
            return true;
        }
        return false;
    }*/

   //check if user is authenticated
    public boolean isAuthenticated(String username) {
        for (User user : authenticatedUsers) {
            if (user.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    //check if player object exists/is active in server
    public boolean isActive(String username) {
        for (Player player : players) {
            if (player.getName().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public void disconnectPlayer(Player player) {
        lock.lock();
        try {
            players.remove(player);
        }
        finally {
            lock.unlock();
        }
        //remove instance of user/player from user_to_player hashmap
        for (User user : user_player_map.keySet()) {
            if (user_player_map.get(user).equals(player)) {
                lock.lock();
                try {
                    user_player_map.remove(user);
                }
                finally {
                    lock.unlock();
                }
                logoutUser(user);
                break;
            }
        }
    }

    //attempt of avoiding slow clients
    /*
    public void timeout(Socket socket) throws IOException {
        long previousTime = System.currentTimeMillis();
        long time;
        Selector selector = Selector.open();
        int readyChannels = selector.select(TIMEOUT);
        if (readyChannels == 0) {
            time = System.currentTimeMillis() - previousTime;
            if(time > TIMEOUT) {
                System.out.println("Runtime error! Disconnecting user...");
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }
        else {
            time = System.currentTimeMillis();
            selector.selectedKeys().clear();
        }
    }
*/
    //logout user, no longer authenticated
    private void logoutUser(User user) {
        lock.lock();
        try {
            authenticatedUsers.remove(user);
        }
        finally {
            lock.unlock();
        }
    }

    // Token Generator
    public static class tokenGenerator {
        private SecureRandom random = new SecureRandom();

        public String nextToken() {
            return new BigInteger(130, random).toString(32);
        }
    }
    public static void main(String[] args) throws Exception {
        Server server = null;
        try {
            server = new Server(6666);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        server.start();
    }
}
