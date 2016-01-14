import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class GetThread extends Thread {
	private int n;
	private String login;
	
	GetThread (String login) {
		this.login = login;
	}

	@Override
	public void run() {
		try {
			while (!isInterrupted()) {
				URL url = new URL("http://localhost:8080/get?user=" + login + "&from=" + n);
				HttpURLConnection http = (HttpURLConnection) url.openConnection();

				InputStream is = http.getInputStream();
				try {
					int sz = is.available();
					if (sz > 0) {
						byte[] buf = new byte[is.available()];
						is.read(buf);

						Gson gson = new GsonBuilder().create();
						Message[] list = gson.fromJson(new String(buf), Message[].class);

						for (Message m : list) {
							System.out.println(m);
							n = m.getReceivedMsgs();
						}
					}
				} finally {
					is.close();
				}
				Thread.sleep(1000);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
	}
}

public class Main {
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		String room = null;
		
		try {
			System.out.print("Enter login: ");
			String login = scanner.nextLine();
			System.out.print("Enter password: ");
			String pass = scanner.nextLine();
			boolean loggedIn = logIn(login, pass);
			
			if (!loggedIn) {
				System.out.print("Incorrect login/password. Disconnected from server.");
				return;
			}
			System.out.println("Successfuly logged in.");
	
			GetThread th = new GetThread(login);
			th.setDaemon(true);
			th.start();

			while (true) {
				String text = scanner.nextLine();
				if (text.isEmpty()) {
					if (room != null) {
						exitRoom(login, room);
					}
					break;
				}

				Pattern pattern = Pattern.compile("(\\S+):(\\S+)");
				Matcher matcher = pattern.matcher(text);
				String to = "all";
				if (matcher.find()) {
					if (matcher.group(1).equals("to")){
						to = matcher.group(2);
						text = "private: " + text.substring(matcher.group(0).length() + 1);
						sendMessage(text, login, to, null);
						continue;
					}
					if (matcher.group(1).equals("user")) {
						if (matcher.group(2).equals("list")) {
							printUserList();
							continue;
						} else {
							printUser(matcher.group(2));
							continue;
						}
					}
					if (matcher.group(1).equals("room")) {
						if (matcher.group(2).equals("exit")) {
							exitRoom(login, room);
							room = null;
						} else {
							if (room != null) {
								exitRoom(login, room);
							}
							room = matcher.group(2);
							new URL("http://localhost:8080/room?login=" + login +
									"&name=" + room +
									"&action=enter").openConnection().getInputStream();
							System.out.println("You entered " + room + " room");
						}
						continue;
					}
				}
				sendMessage(text, login, to, room);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			scanner.close();
		}
	}
	
	private static void printUser(String login) {
		try {
			URL url = new URL("http://localhost:8080/user?get=" + login);
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			
			InputStream is = http.getInputStream();
			int buffLength = is.available();
			if (buffLength > 0) {
				byte[] buff = new byte[buffLength];
				is.read(buff);
				Gson gson = new GsonBuilder().create();
				User user = gson.fromJson(new String(buff), User.class);
				System.out.println(user.getLogin() + " is " + user.getStatus());
			} else {
				System.out.println("User doesn not exist!");
			}
		} catch (IOException e) {
			
		}
	}

	public static void printUserList() {
		try {
			URL url = new URL("http://localhost:8080/user?get=list");
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
	
			InputStream is = http.getInputStream();
			try {
				int sz = is.available();
				if (sz > 0) {
					byte[] buf = new byte[is.available()];
					is.read(buf);
	
					Gson gson = new GsonBuilder().create();
					User[] users = gson.fromJson(new String(buf), User[].class);
	
					for (User user : users)
						System.out.println(user.getLogin() + " " + user.getStatus() + ";");
				}
			} finally {
				is.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void exitRoom(String login, String room) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:8080/room?login=" + login +
					"&name=" + room +
					"&action=exit").openConnection();
			if (connection.getResponseCode() != 200) {
				System.out.println("exit room - bad request");
				return;
			}
			System.out.println("You left " + room + " room");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void sendMessage (String text, String login, String to, String room) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Message m = new Message();
		
		if (room != null) {
			text = "room " + room + ": " + text;
		}
		m.setDate(sdf.format(new Date()));
		m.setText(text);
		m.setFrom(login);
		m.setTo(to);
		m.setRoom(room);

		try {
			int res = m.send("http://localhost:8080/add");
			if (res != 200) {
				System.out.println("HTTP error: " + res);
				return;
			}
		} catch (IOException ex) {
			System.out.println("Error: " + ex.getMessage());
			return;
		}
	}
	
	public static boolean logIn (String login, String pass) {
		Runtime.getRuntime().addShutdownHook(new UserLogOff(login));
		
		HttpURLConnection http;
		boolean result = false;
		try {
			URL url = new URL("http://localhost:8080/login?user=" + login + "&pass=" + pass);
			http = (HttpURLConnection) url.openConnection();
			
			InputStream resp = http.getInputStream();
			byte[] buff = new byte[resp.available()];
			resp.read(buff);
			result = new String(buff).equals("ok");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	static class UserLogOff extends Thread {
		private String login;
		
		UserLogOff (String login) {
			this.login = login;
		}
		
		@Override
		public void run () {
			try {
				URL url = new URL("http://localhost:8080/login?user=" + login + "&pass=logoff");
				url.openConnection().getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.print("bye");
		}
	}
}
