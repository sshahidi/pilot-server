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
		Connection.ServiceType servicetype=Connection.ServiceType.ECHO_SERVER;
		Connection.Mode mode=Connection.Mode.VERBOSE;
		
		//Parsing the input arguments
		try
		{
			if(args.length>=1)
				port=Integer.parseInt(args[0]);
			if (args.length>=2)
				servicetype = Connection.ServiceType.valueOf(args[1]);
			if (args.length>=3)
				mode = Connection.Mode.valueOf(args[2]);
		}
		catch (Exception e) {
			System.out.println("Wrong input arguments.");
			System.out.println("Usage:\r\n>>java MainClass [port number] [servic type] [mode] ");
			System.out.println("\r\nService type can be: {ECHO_SERVER, FTP_SERVER,ARQ_SERVER,CC_SERVER,ROUTING_SERVER,PROJECT_SERVER}");
			System.out.println("\r\nMode can be: {VERBOSE,SILENT,LOG,LOGVERBOSE}");
			return;
		}
		
		//initialization and variable definitions.
		ServerSocket ss;
		long request_num=1; //the number of the client being connected to the server.
		
		
		//////// For debugging purpose:
		//Thread thread; 		//the thread object that will handle each client using the connection class.
		//Connection connection;
		//For now we select the service type and mode ourselves. Each client should select it's own later on.
		//Connection.Mode mode=Connection.Mode.VERBOSE;
		//Connection.ServiceType servicetype=Connection.ServiceType.PROJECT_SERVER;
		
		
		try {
			ss=new ServerSocket(port);
			System.out.println("Server online.\nHost name: "+InetAddress.getLocalHost().getHostName()+"\nHost Address: "+InetAddress.getLocalHost().getHostAddress()+":"+port+ "\nwaiting for requests.");
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("could not open the server socket on the given port: "+args[0]+". Server ended.");
			return;
		}
		
		while(true) //the while loop that keeps server alive
		{
			try
			{
				Socket socket=ss.accept();
				System.out.println("request received. request number: " + request_num + " client: "+ socket.getRemoteSocketAddress().toString());
				
				Connection connection=new Connection(socket,servicetype,mode,request_num);
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
