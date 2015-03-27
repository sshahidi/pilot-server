import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;


public class ArqServer {

	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private ClientConnection.Mode mode;
	private Socket socket;
	private final long CLIENT_ID;
	private String user_name="";
	Random randomGenerator = new Random();



	//constructors
	public ArqServer() throws IOException {
		this(null,-1,ClientConnection.Mode.SILENT);
	}


	public ArqServer(Socket socket, long Client_id,ClientConnection.Mode mode) throws IOException
	{
		this.socket =socket;
		this.CLIENT_ID=Client_id;
		this.mode=mode;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		//this.din = new DataInputStream(socket.getInputStream());
	}


	public void start() throws IOException {

		while(true){
			//System.out.println("Number of packets to be received");
			int packetNo = reader.read();
			if(packetNo == 0)
				break;

			int probError = reader.read();

			int i = 1;
			int lastAck = 0;
			int randomSleep = 0;
			int randomError = 0;
			while(i < packetNo){
				int received = reader.read();
				if(received == 0)
					break;

				randomError = randomGenerator.nextInt(100) + 1;
				if(randomError > probError ){

					//System.out.println("Received packet " + received);
					log("Received packet from "+CLIENT_ID+": packet #"+received);

					if(received == lastAck + 1)
					{  randomSleep = randomGenerator.nextInt(3000);
					//System.out.println("Time"+randomInt);

					try {
						Thread.sleep(randomSleep);
					} catch (Exception e) {
					}
					//System.out.println("Acknowledging packet " + received);
					log("Acknowledging packet. to "+CLIENT_ID+": packet #" + received);
					writer.write(received);
					writer.flush();
					lastAck++;  
					}else if(lastAck >0 && received > lastAck + 1){
						randomSleep = randomGenerator.nextInt(3000);
						//System.out.println("Time"+randomInt);

						try {
							Thread.sleep(randomSleep);
						} catch (Exception e) {
						}
						log("Acknowledging packet. to "+CLIENT_ID+": packet #" + received);
						//System.out.println("Acknowledging packet " + lastAck);
						writer.write(lastAck);
						writer.flush();						
					}
				}else{
					//System.out.println("Dropped packet " + received);
					log("Dropped packet of "+CLIENT_ID+": packet #" + received);
				}
			}

			writer.flush();
			log("All packets have been received successfully");
			break;


		}

	}



	///commonly used function

	public void writeline(String msg) throws IOException
	{
		writer.writeBytes(msg+CRLF);
		writer.flush();
		//if(this.mode.toString().contains("VERBOSE"))
		//System.out.println("to "+CLIENT_ID+": "+msg);	
		log("to "+CLIENT_ID+": "+msg);
	}

	public String readline() throws IOException
	{
		String str= reader.readLine();
		//if(this.mode.toString().contains("VERBOSE"))
		//System.out.println("from "+CLIENT_ID+": "+str);
		log("from "+CLIENT_ID+": "+str);
		return str;
	}


	/**
	 * used for logging purposes with different levels of importance.
	 * @param str the string to log (show in the standard output or write to a file.
	 * @param lvl the level of importance.
	 */
	private void log(String str,ClientConnection.Mode lvl) throws IOException
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
			fos.write((timeFormat.format(new Date())+str+CRLF).getBytes());
			fos.close();
		}
	}

	private void log(String str) throws IOException
	{
		log(str,mode);
	}
	////////Getters and Setters	





}
