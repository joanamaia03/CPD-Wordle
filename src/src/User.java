public class User {
    private final String username;
    private final String password;
    private int score;

    public User(String username, String password, Integer score) {
        this.username = username;
        this.password = password;
        this.score = score;
    }

    public String getUsername() {
        return username;
    }
    public boolean validatePassword(String password) {
        return this.password.equals(password);
    }
    public String getPassword() { return password;}

    public int getScore(){return score;}
    public void setScore(Integer score){this.score = score;}

    @Override
    public String toString() {
        return username + "," + password + "," + score;
    }
}
