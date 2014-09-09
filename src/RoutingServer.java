import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;


class Node implements Comparable<Node>
{
	public final int name;
	public Edge[] neighbors;
	public double minDistance = Double.POSITIVE_INFINITY;
	public Node previous;     // to keep the path
	public Node(int argName) 
	{ 
		name = argName; 
	}

	public int compareTo(Node other)
	{
		return Double.compare(minDistance, other.minDistance);
	}
}

class Edge
{
	public final Node target;
	public final double weight;
	public Edge(Node argTarget, double argWeight)
	{ 
		target = argTarget;
		weight = argWeight; 
	}
}

public class RoutingServer {

	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private Connection.Mode mode;
	private Socket socket;
	private final long CLIENT_ID;
	private String user_name="";
	Random randomGenerator = new Random();



	//constructors
	public RoutingServer() throws IOException {
		this(null,-1,Connection.Mode.SILENT);
	}


	public RoutingServer(Socket socket, long Client_id,Connection.Mode mode) throws IOException
	{
		this.socket =socket;
		this.CLIENT_ID=Client_id;
		this.mode=mode;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		//this.din = new DataInputStream(socket.getInputStream());
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


	public void start() throws IOException {

		while(true){
			double tempRand;
			Random randomGenerator = new Random();
			int noNodes = reader.read();
			if(noNodes == 0)
				break;
			log("Number of nodes in the network is " + noNodes);
			double[][] matrix = new double[noNodes][noNodes];
			String total = "";
			System.out.println();
			log("Adjacency Matrix");
			for(int i = 0; i < matrix.length; i++){
				for(int j = 0; j < matrix.length; j++){
					// The delays are randomly generated to be between 100 - 1000
					tempRand= randomGenerator.nextInt(100);
					// The network is 80% connected, i.e. 20% of edges don't exist, taking value of infinity
					if(tempRand > 20)
					{
						matrix[i][j] = Math.floor((randomGenerator.nextDouble()*900 + 100)*1.0)/1.0;
					}
					else
					{
						matrix[i][j] = Double.POSITIVE_INFINITY;
					}
					System.out.print(matrix[i][j]+" ");
					total += String.valueOf(matrix[i][j]) + " ";
				}
				System.out.println();
			}
			System.out.println();
			writeline(total);

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
				log("Node " + count);
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
		//log("to "+CLIENT_ID+": "+msg);
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
