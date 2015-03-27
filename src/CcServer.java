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
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;



public class CcServer {

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
	public CcServer() throws IOException {
		this(null,-1,ClientConnection.Mode.SILENT);
	}


	public CcServer(Socket socket, long Client_id,ClientConnection.Mode mode) throws IOException
	{
		this.socket =socket;
		this.socket.setTcpNoDelay(true);
		this.CLIENT_ID=Client_id;
		this.mode=mode;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		//this.din = new DataInputStream(socket.getInputStream());
		System.out.println("Client Connected ...");
	}


	public void start() throws IOException {
		int packetNo = reader.read();
		//System.out.println("Number of packets to be received");
		log(CLIENT_ID+": Number of packets to be received. from "+packetNo);
		if(packetNo == 0)
			return;

		//int probError = reader.read();
		int bandwidth=5+randomGenerator.nextInt(6); //random bandwidth between 8-20 MSS/RTT.
		int RTT=1000; //deterministic RTT =Average RTT in millisec.
		long[] rcv_times=new long[bandwidth]; //keeping track of reception time to drop high rate tranmissited packets manually.
		Arrays.fill(rcv_times, 0); 


		long tic=0; //this value is used to start the timer.

		int lastAck = 0;
		int randomSleep = 0;
		//int randomError = 0;
		while(lastAck < packetNo){
			int received = reader.read();
			log(CLIENT_ID+": Received packet#"+received);
			if(received <= 0)
				break;
			if(received == 1)
				tic=System.currentTimeMillis();
			long previous_time=rcv_times[lastAck%bandwidth];
			//TODO: if the packet sequence is not correct, we won't drop it now. maybe we should time another way to drop them if sent fast.
			if(received == lastAck +1)
				rcv_times[lastAck%bandwidth]=System.currentTimeMillis();
			//log("previous: "+ previous_time+" now: "+rcv_times[lastAck%bandwidth]+ " diff: "+ (rcv_times[lastAck%bandwidth]-previous_time));
			//log("last ack: "+lastAck+ " time diff: "+ (rcv_times[lastAck%bandwidth]-previous_time) );
			//randomError = randomGenerator.nextInt(100) + 1;
			//if(randomError > probError ){
			if(rcv_times[lastAck%bandwidth]-previous_time >= RTT) //we are within the allowed BW
			{
				//log(CLIENT_ID+": Received packet #"+received);

				if(received == lastAck + 1)
					lastAck++;
				randomSleep = RTT;//randomGenerator.nextInt(3000);
				//System.out.println("Time"+randomInt);
				new Thread(new Sender(randomSleep, lastAck)).start();

				log(CLIENT_ID+": Acknowledging packet #" + lastAck);
				
			}
			else //congestion has happened
			{
				//System.out.println("Dropped packet " + received);
				log(CLIENT_ID+": Dropped packet  #" + received);
			}
		}

		writer.flush();
		try {
			Thread.sleep(RTT); //adding 2RTT sleep to make sure the last ack is sent
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		log(lastAck+" out of "+packetNo+ " packets have been received successfully");
		long toc=System.currentTimeMillis();
		if (lastAck==packetNo)
			log(analyze(toc-tic, RTT, packetNo, bandwidth),ClientConnection.Mode.VERBOSE);
		else
			log("Data was not received completely.");
	}


	String analyze(long transmission_time,int RTT,int packetNo,int bandwidth)
	{
		StringBuilder sb=new StringBuilder();
		//parameters
		sb.append(CRLF);
		sb.append("Total bandwidth: ").append(bandwidth).append(" MSS/RTT").append(CRLF);
		sb.append("Total number of packets: ").append(packetNo).append(CRLF);
		sb.append("Total transmission time: ").append(transmission_time).append(" seconds.").append(CRLF);
		sb.append("Average round trip time: ").append(RTT).append(CRLF);
		sb.append(CRLF);
		
		//analysis
		int expected_time=0;
		int time_out=RTT; //sampleRTT + 4DevRTT.
		int rate=1;
		int sent=0;
		int ssthres=16;
		while(sent<packetNo)
		{
			if(rate<bandwidth)
			{
				sent+=rate;
				if(rate<ssthres)
					rate*=2;
				else
					rate++;
			}
			else
			{
				sent+=bandwidth;
				ssthres=rate/2;
				rate=1;
			}
			expected_time++;
		}
		
		//analysis result.
		sb.append("Minimum possible transmission time: ").append(((double)packetNo/(double)bandwidth)).append(" RTT.").append(CRLF);
		sb.append("Expected transmission time using TCP Tahoe (for initial ssthres= "+16+"):").append(expected_time).append(" RTT.").append(CRLF);
		sb.append("Actual transmission time: ~").append((double)transmission_time/(double)RTT).append(" RTT.").append(CRLF);
		sb.append("Actual throughput: ").append((double)packetNo/((double)transmission_time/(double)(RTT))/(double)bandwidth);
		return sb.toString();
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



	////// nested classes
	protected class Sender implements Runnable
	{
		int sleep_time;
		int value_to_send;
		public Sender(int sleep_time,int value_to_send)
		{
			this.sleep_time=sleep_time;
			this.value_to_send=value_to_send;
		}
		@Override
		public void run() {
			try {
				Thread.sleep(Math.max(sleep_time,1));
				writer.write(value_to_send);
				writer.flush();
				//log(CLIENT_ID+": Acknowledging packet #" + value_to_send);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}
