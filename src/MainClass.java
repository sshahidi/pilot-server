import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;





public class MainClass {

	private static int port; //The port for the server to listen to.
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//loading the default values.
		port=9876;
		//for now we just have phone server.
		ClientConnection.ServiceType servicetype=ClientConnection.ServiceType.PHONE_SERVER;
		Slog.Mode log_filter=Slog.Mode.VERBOSE;
		Slog.Output log_output = Slog.Output.STDOUT;
		
		//Parsing the input arguments
		try
		{
			if(args.length>=1)
				port=Integer.parseInt(args[0]);
			if (args.length>=2)
				servicetype = ClientConnection.ServiceType.valueOf(args[1]);
			if (args.length>=3)
				log_filter = Slog.Mode.valueOf(args[2]);
			if (args.length>=4)
				log_output = Slog.Output.valueOf(args[3]);
		}
		catch (Exception e) {
			System.out.println("Wrong input arguments.");
			System.out.println("Usage:\r\n>>java MainClass [port number] [servic type] [log filter][log output] ");
			System.out.println("\r\nService type can be just: {PHONE_SERVER} for now");
			System.out.println("\r\nLog filter can be: {VERBOSE,DEBUG,INFO,WEARNING,ERROR}");
			System.out.println("\r\nlog output can be: {STDOUT,NETWORK,FILE}");
			
			return;
		}
		
		//initialization and variable definitions.
		Slog log=new Slog(log_filter,log_output); //for logging
		ServerSocket ss;
		long request_num=1; //the number of the client being connected to the server.
		
		
		//////// For debugging purpose:
		//Thread thread; 		//the thread object that will handle each client using the connection class.
		//Connection connection;
		//For now we select the service type and mode ourselves. Each client should select it's own later on.
		//Connection.Mode mode=Connection.Mode.VERBOSE;
		//Connection.ServiceType servicetype=Connection.ServiceType.PROJECT_SERVER;
		
		
		try {
			
			System.out.println("Server loading... Logger status: \n"+log.toString());
			ss=new ServerSocket(port);
			log.i("Server online.\nHost name: "+InetAddress.getLocalHost().getHostName()+"\nHost Address: "+InetAddress.getLocalHost().getHostAddress()+":"+port+ "\nwaiting for requests.");
		} catch (IOException e) {
			log.e("could not open the server socket on the given port: "+args[0]+". Server ended.");
			e.printStackTrace();
			return;
		}
		
		while(true) //the while loop that keeps server alive
		{
			try
			{
				Socket socket=ss.accept();
				log.i("request received. request number: " + request_num + " client: "+ socket.getRemoteSocketAddress().toString());
				
				ClientConnection connection=new ClientConnection(socket,servicetype,request_num,log_filter,log_output);
				Thread thread=new Thread(connection); //the thread object that will handle each client using the connection class.
				thread.start();
				request_num++;
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
		
		
		

	}

}
