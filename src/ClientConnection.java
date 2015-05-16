import java.net.Socket;


public class ClientConnection implements Runnable {

	public static enum ServiceType{PHONE_SERVER, }; 
	//variables
	private final String CRLF= "\r\n";
	private Socket socket;
	private final long CLIENT_ID;
	private ServiceType service_type;
	Slog log;
	private Slog.Mode log_filter;
	private Slog.Output log_output;
	
	//default constructor
	public ClientConnection() {
		this(null,ServiceType.PHONE_SERVER,-1,Slog.Mode.INFO,Slog.Output.STDOUT);
	}

	
	//constructor2
	ClientConnection(Socket socket, ServiceType service_type,long client_id,Slog.Mode log_filter,Slog.Output log_output)
	{
		this.socket=socket;
		this.service_type=service_type;
		this.CLIENT_ID=client_id;
		this.log_filter=log_filter;
		this.log_output=log_output;
		log = new Slog(log_filter, log_output);
		log.i("connection established:"+CRLF+ this.toString());
	}
	
	
	
	@Override
	public void run() {
		if(socket==null)
		{
			log.e("Null socket reference.");
			return;
		}
		
		try {
			handleClient();
		} catch (Exception e) {
			log.w("connection to client: "+CLIENT_ID+" ended unexpectedly.");
			log.printStackTrace(e, Slog.Mode.DEBUG);
		}
	}

	private void handleClient() throws Exception{
		
		//reader and writer
		//BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		//DataOutputStream writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		
		//Calling the proper service. 
		
		switch (this.service_type) {
		case PHONE_SERVER:
			new PhoneServer(socket,CLIENT_ID, log_filter,log_output).start();
			break;
		default:
			log.e("The requested service is either unkonwn or not yet supported.\r\n Service ended.");
			break;
		}
		socket.close();
		//if(this.mode.toString().contains("VERBOSE"))
		log.i("connection to "+CLIENT_ID+" closed.");
		
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
	


	@Override
	public String toString() {
		StringBuilder builder=new StringBuilder();
		builder.append("service type:").append(service_type.toString()).append(CRLF);
		builder.append("client id:").append(CLIENT_ID).append(CRLF);
		//builder.append("remote address:").append(socket.getRemoteSocketAddress().toString()).append(CRLF);
		builder.append("socket: ").append(socket.toString()).append(CRLF);
		
		return builder.toString();
	}
	
	

	
}
