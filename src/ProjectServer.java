import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.StringTokenizer;


public class ProjectServer {

	///////Global variables
	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private Connection.Mode mode;
	private Socket socket;
	private final long CLIENT_ID;
	private String user_name="";
	Random randomGenerator = new Random();
	
	private Socket data_socket;
	DataInputStream din;
	DataOutputStream dout;
	BufferedReader br;
	private int RTT;
	

	public ProjectServer() throws IOException {
		this(null,-1,Connection.Mode.SILENT);
	}

	public ProjectServer(Socket socket,long client_id, Connection.Mode mode) throws IOException
	{
		this.mode=mode;
		this.socket=socket;
		this.socket.setTcpNoDelay(true);
		this.reader=new BufferedReader(new InputStreamReader(this.socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(this.socket.getOutputStream());	//for writing lines.
		this.CLIENT_ID=client_id;
	}


	public void start() throws IOException {
		int score=0;
		
		score+=shortestPathSection();
		score+=DataTransferSection();
		System.out.println("--------------------------------------------------");
		log("FINAL SCORE (out of 3): "+score);

	}

	/** 
	 * function that evaluates the routing section.
	 * @return 1 if the answer is correct, 0 otherwise.
	 * @throws IOException
	 */
	public int shortestPathSection() throws IOException
	{
		double answer=-1; //the resulting delay.
		double tempRand;
		randomGenerator = new Random();
		//reader.read();
		int noNodes = 5+randomGenerator.nextInt(6); //random number of nodes [5 10] nodes.
		log("Sending the number of nodes to client:");
		writeline(""+noNodes);
		//writer.writeBytes(""+noNodes);
		//writer.flush();
		//log("to "+CLIENT_ID+": "+noNodes);
		//log("Number of nodes in the network is " + noNodes);
		double[][] matrix = new double[noNodes][noNodes];
		String total = "";
		System.out.println();
		log("Adjacency Matrix");
		for(int i = 0; i < matrix.length; i++){
			for(int j = 0; j < matrix.length; j++){
				if(i==j)
				{
					matrix[i][j]=0;
				}
				else
				{
					// The delays are randomly generated to be between 500 - 1000
					tempRand= randomGenerator.nextInt(100);
					// The network is 80% connected, i.e. 20% of edges don't exist, taking value of infinity
					if(tempRand > 20)
					{
						matrix[i][j] = Math.floor((randomGenerator.nextDouble()*500 + 500)*1.0)/1.0;
					}
					else
					{
						matrix[i][j] = Double.POSITIVE_INFINITY;
					}
				}
				System.out.print(matrix[i][j]+" ");
				total += String.valueOf(matrix[i][j]) + " ";
			}
			System.out.println();
		}
		System.out.println();
		writeline(total);


		//calculating the answer...
		List<Node> nodeList = new ArrayList<Node>();
		for(int i = 0; i < matrix.length; i++){
			nodeList.add(new Node(i));
		}

		// Create edges from adjacency matrix
		adjacenyToEdges(matrix, nodeList);

		// Finding shortest path for all nodes
		for(int count = 0; count < noNodes; count++){
			for(int i = 0; i < matrix.length; i++)
			{   	
				nodeList.get(i).minDistance = Double.POSITIVE_INFINITY;
				nodeList.get(i).previous = null;
			}
			computePaths(nodeList.get(count));
			if(count==0)
			{
				log("From Node " + count);
				answer = nodeList.get(matrix.length-1).minDistance; //calculating the answer
				for(int i = 0; i < matrix.length; i++)
				{

					Node node = nodeList.get(i);
					System.out.print("Total time to reach node " + node.name + ": " + node.minDistance+ " ms, ");
					//Finding the path itself
					List<Integer> path = getShortestPathTo(node);
					System.out.println("Path: " + path);
				}
				System.out.println();
			}
		}

		//reading the answer from the client.
		log("waiting to receive the selected path from client...");
		String response=readline();

		StringTokenizer st = new StringTokenizer(response," \r\n\t.[,]()");
		int[] path=new int[st.countTokens()];
		double delay=0;
		for(int i=0;st.hasMoreTokens();i++)
		{
			path[i]=Integer.parseInt(st.nextToken());
			if(i>0)
			{
				double step_delay=matrix[path[i-1]][path[i]];
				if (path[i-1]==path[i])
					step_delay=0;
				delay+=step_delay;
				System.out.println("("+path[i-1]+", "+path[i]+") delay: "+step_delay);
			}
		}
		this.RTT=(int) (2*delay); //setting the RTT value.
		if (Math.abs(delay-answer)<1)
		{
			log("CORRECT path selected.");
			return 1;
		}
		else
		{
			log("INCORRECT	path selected.");
			return 0;
		}

	}
	
	
	public int DataTransferSection() throws IOException
	{
		int score=0;
		log("waiting to receive the file name (in string format)...");
		String file_name = readline();
		log("waiting to receive the number of packets (in string format)...");
		int packetNo = Integer.parseInt(readline());
		//System.out.println("Number of packets to be received");
		//log(CLIENT_ID+": Number of packets to be received. from "+packetNo);
		if(packetNo == 0)
		{
			log("INCORRECT. zero packets received.");
			return 0;
		}
		
		//Using the same socket for data and control.
		//setupDataConnection();
		dout=writer;
		din=new DataInputStream(socket.getInputStream());
		
		//int probError = reader.read();
		int bandwidth=4+randomGenerator.nextInt(10); //random bandwidth between 8-20 MSS/RTT.
		// int RTT=1000; //deterministic RTT =Average RTT in millisec.
		long[] rcv_times=new long[bandwidth]; //keeping track of reception time to drop high rate tranmissited packets manually.
		Arrays.fill(rcv_times, 0); 


		long tic=System.currentTimeMillis(); //this value is used to start the timer.

		int lastAck = 0;
		int randomSleep = 0;
		
		FileOutputStream fout=new FileOutputStream(file_name,false);
		//writeline("START");
		//int randomError = 0;
		log("Waiting to receive data...");
		while(lastAck < packetNo){
			//int received = reader.read();
			byte[] buffer=new byte[1004];
			int len=din.read(buffer,0,buffer.length);
			//log("packet length is: "+len);
			if(len<=0)
				break;
			ByteBuffer wrapped = ByteBuffer.wrap(buffer); // big-endian by default
			int received = wrapped.getInt(0);
			
			log(CLIENT_ID+": Received packet#"+received);//+" time: "+System.currentTimeMillis());
			if(received <= 0)
				break;
			if(received == 1)
				tic=System.currentTimeMillis();
			long previous_time=rcv_times[lastAck%bandwidth];
			rcv_times[lastAck%bandwidth]=System.currentTimeMillis();
			//log("previous: "+ previous_time+" now: "+rcv_times[lastAck%bandwidth]+ " diff: "+ (rcv_times[lastAck%bandwidth]-previous_time));
			//log("last ack: "+lastAck+ " time diff: "+ (rcv_times[lastAck%bandwidth]-previous_time) );
			//randomError = randomGenerator.nextInt(100) + 1;
			//if(randomError > probError ){
			if(rcv_times[lastAck%bandwidth]-previous_time >= RTT) //we are within the allowed BW
			{
				//log(CLIENT_ID+": Received packet #"+received);
				if(received == lastAck + 1)
				{
					lastAck++;
					//writing to file.
					fout.write(buffer, Integer.SIZE/8, len-Integer.SIZE/8);
					fout.flush();
				}
				randomSleep = RTT;//randomGenerator.nextInt(3000); //fix RTT for simplicity
				//System.out.println("Time"+randomInt);
				new Thread(new Sender(randomSleep, lastAck)).start();
				log(CLIENT_ID+": Acknowledging packet #" + lastAck);
				//try{ //adding some delay to avoid several parralel acks being sent and mess up the socket's output stream.
				//	Thread.sleep(5);
				//}catch (Exception e) {}
				
			}
			else //congestion has happened
			{
				//System.out.println("Dropped packet " + received);
				log(CLIENT_ID+": Dropped packet  #" + received);
			}
		}
		fout.close();
		//data_socket.close();
		writer.flush();
		try {
			Thread.sleep(RTT); //adding 2RTT sleep to make sure the last ack is sent
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		log(lastAck+" out of "+packetNo+ " packets have been received.");
		
		if(lastAck==packetNo)
		{
			log("CORRECT, file transmission was reliable");
			score++;
		}
		else
			log("INCORRECT, data was not received completely");
		
		long toc=System.currentTimeMillis();
		
		//calculating the expected and the actual transmission time in terms of RTT
		int expected_time= analyzeCC(toc-tic, RTT, lastAck, bandwidth); 
		int transmission_time=(int) ((double)(toc-tic)/(double)RTT);
		
		//if (lastAck==packetNo)
		//	log(analyzeCC(toc-tic, RTT, packetNo, bandwidth),Connection.Mode.VERBOSE);
		//else
		//	log("Data was not received completely.");
		if((double)Math.abs(expected_time-transmission_time)/(double)expected_time< 0.20)
		{
			log("CORRECT answer. data received within the allowed time.");
			score++;
		}
		else
		{
			log("INCORRECT answer. The transmission rate was too slow.");			
		}
		return score;
	}


	
	//other functions
	
	private void setupDataConnection() throws IOException{
		//enering the extended passive mode.
		//selecting a random port.
		Random a=new Random();
		boolean flag=false;
		int port=0;
		ServerSocket ss=null;
		while(!flag)
		{
			port= a.nextInt(65535-1024)+1024;
			try{
				ss=new ServerSocket(port);
				flag=true;
			}
			catch(Exception e)
			{}
		}
		log("sending the data port number to the client:");
		writeline(""+port);
		data_socket= ss.accept();
		dout=new DataOutputStream(data_socket.getOutputStream());
		din=new DataInputStream(data_socket.getInputStream());
		log("Data connection established to :"+CLIENT_ID);
	}
	
	int analyzeCC(long transmission_time,int RTT,int packetNo,int bandwidth)throws IOException
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
		int ssthres=1000;
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
		sb.append("Expected transmission time using TCP Tahoe (for initial ssthres= [big value]"+"):").append(expected_time).append(" RTT.").append(CRLF);
		sb.append("Actual transmission time: ~").append((double)transmission_time/(double)RTT).append(" RTT.").append(CRLF);
		sb.append("Actual throughput: ").append((double)packetNo/((double)transmission_time/(double)(RTT))/(double)bandwidth);
		log(sb.toString());
		return expected_time;
	}
	public static void adjacenyToEdges(double[][] matrix, List<Node> v)
	{
		for(int i = 0; i < matrix.length; i++)
		{
			v.get(i).neighbors = new Edge[matrix.length];
			for(int j = 0; j < matrix.length; j++)
			{
				v.get(i).neighbors[j] =  new Edge(v.get(j), matrix[i][j]);	
			}
		}
	}
	public static void computePaths(Node source)
	{
		source.minDistance = 0.;
		PriorityQueue<Node> NodeQueue = new PriorityQueue<Node>();
		NodeQueue.add(source);

		while (!NodeQueue.isEmpty()) {
			Node sourceNode = NodeQueue.poll();

			// Visit each edge exiting u
			for (Edge edge : sourceNode.neighbors)
			{
				Node targetNode = edge.target;
				double weight = edge.weight;
				double distanceThroughU = sourceNode.minDistance + weight;
				if (distanceThroughU < targetNode.minDistance) {
					NodeQueue.remove(targetNode);
					targetNode.minDistance = distanceThroughU ;
					targetNode.previous = sourceNode;
					NodeQueue.add(targetNode);
				}
			}
		}
	}
	public static List<Integer> getShortestPathTo(Node target)
	{
		List<Integer> path = new ArrayList<Integer>();
		for (Node Node = target; Node != null; Node = Node.previous)
			path.add(Node.name);
		Collections.reverse(path);
		return path;
	}

	
	///logging and IO functions

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

//////nested classes
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
				synchronized (Sender.class) {
					writer.writeBytes(value_to_send+CRLF);
					writer.flush();					
				}
				//log(CLIENT_ID+": Acknowledging packet #" + value_to_send);
			} catch (Exception e) {
				//e.printStackTrace();
				try 
				{
					log("ERROR: "+e.getMessage());
				} catch (IOException e1) {}
			}
		}
	}

}
