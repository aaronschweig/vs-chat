package vs.chat.server;

public class ServerBootstrapper {

	public static void main(String[] args) {
		var server = new Server(9876);//new NodeConfig("localhost", 9877)
		var mainThread = new Thread(server);
		mainThread.start();

	}
}