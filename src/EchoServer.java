import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;



public class EchoServer {
	
	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private Connection.Mode mode;
	private Socket socket;
	private final long CLIENT_ID;
	
	public EchoServer() throws IOException {
		this(null,-1,Connection.Mode.SILENT);
	}
	
	public EchoServer(Socket socket,long client_id, Connection.Mode mode) throws IOException
	{
		this.mode=mode;
		this.socket=socket;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		this.CLIENT_ID=client_id;
	}

	
	public void start() throws IOException
	{
		writeline("Welcome to ECE361 Server. Service type: ECHO_SERVER");
		while(true)
		{
			String str=readline();
			if(str.equalsIgnoreCase("quit"))
				break;
//			try{
//				//System.out.println("adding delay.");
//				Thread.sleep(25); //adding manual delay!
//			}
//			catch (InterruptedException e) {}
			writeline(str);
		}
		writeline("QUIT");
	}
	
	
	
	public void writeline(String msg) throws IOException
	{
		writer.writeBytes(msg+CRLF);
		writer.flush();
		log("to "+CLIENT_ID+": "+msg);
		//if(this.mode.toString().contains("VERBOSE"))
		//	System.out.println("to "+CLIENT_ID+": "+msg);	
	}
	
	public String readline() throws IOException
	{
		String str= reader.readLine();
		log("from "+CLIENT_ID+": "+str);
		//if(this.mode.toString().contains("VERBOSE"))
		//	System.out.println("from "+CLIENT_ID+": "+str);
		
		return str;
	}
	
	
	/**
	 * used for logging purposes with different levels of importance.
	 * @param str the string to log (show in the standard output or write to a file.
	 * @param lvl the level of importance.
	 */
	private void log(String str,Connection.Mode lvl) throws IOException
	{
		DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss] ");

		if (lvl.toString().contains("VERBOSE") )
		{
			System.out.println(timeFormat.format(new Date())+str);
		}
		//FIXME: this is not optimal. if a lot of log is expected we shouldn't open and close the stream for each line.
		if(lvl.toString().contains("LOG"))
		{
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			//DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss] ");
			FileOutputStream fos=new FileOutputStream("LOG"+dateFormat.format(new Date()), true);
			String a;
			fos.write((timeFormat.format(new Date())+str+CRLF).getBytes());
			fos.close();
		}
	}
	
	private void log(String str) throws IOException
	{
		log(str,mode);
	}
////////Getters and Setters	
	void setSocket(Socket socket)
	{
		this.socket=socket;
	}
	
	
}
