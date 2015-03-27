import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import javax.xml.ws.handler.MessageContext.Scope;


public class ClientConnection implements Runnable {

	public static enum ServiceType{ECHO_SERVER, FTP_SERVER,ARQ_SERVER,CC_SERVER,ROUTING_SERVER,PROJECT_SERVER}; //CC=congestion control
	public static enum Mode{VERBOSE,SILENT,LOG,LOGVERBOSE,};
	//variables
	private final String CRLF= "\r\n";
	private Socket socket;
	private final long CLIENT_ID;
	private ServiceType service_type;
	private Mode mode;
	
	//default constructor
	public ClientConnection() {
		this(null,ServiceType.ECHO_SERVER,Mode.SILENT,-1);
	}
	
	
	//constructor2
	ClientConnection(Socket socket, ServiceType service_type,Mode mode,long client_id)
	{
		this.socket=socket;
		this.service_type=service_type;
		this.mode=mode;
		this.CLIENT_ID=client_id;
		
		if(this.mode.toString().contains("VERBOSE"))
			System.out.println("connection established:"+CRLF+ this.toString());
	}
	
	
	
	@Override
	public void run() {
		if(socket==null)
		{
			System.out.println("ERROR: Null socket reference.");
			return;
		}
		
		try {
			handleClient();
		} catch (Exception e) {
			System.out.println("connection to client: "+CLIENT_ID+" ended unexpectedly.");
			e.printStackTrace();
		}
	}

	private void handleClient() throws Exception{
		
		//reader and writer
		//BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		//DataOutputStream writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		
		//Calling the proper service. 
		
		switch (this.service_type) {
		case ECHO_SERVER:
			new EchoServer(socket,CLIENT_ID, mode).start();
			break;
		case FTP_SERVER:
			new FtpServer(socket, CLIENT_ID, mode).start();
			break;
		case ARQ_SERVER:
			new ArqServer(socket,CLIENT_ID,mode).start();
			break;
		case CC_SERVER:
			new CcServer(socket, CLIENT_ID, mode).start();
			break;
		case ROUTING_SERVER:
			new RoutingServer(socket, CLIENT_ID, mode).start();
			break;
		case PROJECT_SERVER:
			new ProjectServer(socket,CLIENT_ID,mode).start();
			break;
		default:
			System.out.println("The requested service is either unkonwn or not yet supported.\r\n Service ended.");
			break;
		}
		socket.close();
		//if(this.mode.toString().contains("VERBOSE"))
		System.out.println("connection to "+CLIENT_ID+" closed.");
		
	}

/////////Getters and setters
	public Socket getSocket() {
		return socket;
	}


	public void setSocket(Socket socket) {
		this.socket = socket;
	}


	public long getClient_id() {
		return CLIENT_ID;
	}


	public ServiceType getService_type() {
		return service_type;
	}
	
	public Mode getMode() {
		return mode;
	}


	@Override
	public String toString() {
		StringBuilder builder=new StringBuilder();
		builder.append("service type:").append(service_type.toString()).append(CRLF);
		builder.append("mode:").append(mode.toString()).append(CRLF);
		builder.append("client id:").append(CLIENT_ID).append(CRLF);
		//builder.append("remote address:").append(socket.getRemoteSocketAddress().toString()).append(CRLF);
		builder.append("socket: ").append(socket.toString()).append(CRLF);
		
		return builder.toString();
	}
	
	

	
}
