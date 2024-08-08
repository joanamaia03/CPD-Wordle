import java.io.*;
import java.net.Socket;


public class Player {
    private String token;
    private Socket socket;
    private String name;
    private int score;
    private int level;
    private String address;
    private int port;

    public Player(Socket socket, String name, Integer score) {
        this.socket = socket;
        this.name = name;
        this.score = score;

        setLevel(score);
    }

    public Socket getSocket(){return socket;}
    public String getName() {
        return name;
    }
    public int getScore(){return score;}
    public void incrementScore(){
        this.score++;
        setLevel(this.score);
    }

    private void setLevel(Integer score){
        this.level = 1 + (score/10);
    }

    public int getLevel(){
        return level;
    }

    public void enterQueueFirstTime() throws IOException {
        try {
             Socket socket = new Socket(address, port);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
             output.println("You're in the queue");
             token = input.readLine();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void returnToQueue() throws IOException {
        if (token == null) {
            throw new IllegalStateException("Enter the queue to receive a token");
        }

        try{
            Socket socket = new Socket(address, port);
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            output.println("Returned to the queue");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try{
            Socket socket = new Socket("localhost", 6666);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            //output.println("Welcome to WORDLE!");
            //output.println("Please authenticate to play:");
            String response;
            while((response = input.readLine()) != null){
                System.out.println(response);
                if(response.contains("Enter username: ")){
                    String username = console.readLine();
                    output.println(username);
                } else if (response.contains("Enter password: ")) {
                    String password = console.readLine();
                    output.println(password);
                } else if(response.contains("Make your guess: ")){
                    String guess = console.readLine();
                    output.println(guess);
                }
                else if(response.contains("User does not exist. Would you like to register? (y/n)")){
                    String guess = console.readLine();
                    output.println(guess);
                }
                else if (response.contains("Password must be between 4 and 16 characters. Please enter a new password:")) {
                    String password = console.readLine();
                    output.println(password);
                }

            }
            socket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}



