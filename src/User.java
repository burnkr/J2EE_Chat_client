public class User {
    private String login;
    private transient String password;
    private String status;
    private transient String room;

    public User(String login, String password) {
        this.login = login;
        this.password = password;
        this.status = "offline";
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status;
    }

    public String getRoom() {
        return room;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}
