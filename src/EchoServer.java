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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class EchoServer {

	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private ClientConnection.Mode mode;
	private Socket socket;
	private final long CLIENT_ID;

	public EchoServer() throws Exception {
		this(null,-1,ClientConnection.Mode.SILENT);
	}

	public EchoServer(Socket socket,long client_id, ClientConnection.Mode mode) throws Exception
	{
		this.mode=mode;
		this.socket=socket;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		this.CLIENT_ID=client_id;
	}


	public void start() throws Exception
	{
		writeline("Welcome to Pilot Server. Service type: ECHO_SERVER");
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
		//writeline("QUIT");
	}



	public void writeline(String msg) throws Exception
	{
		writer.writeBytes(msg+CRLF);
		writer.flush();
		log("to "+CLIENT_ID+": "+msg);
		//if(this.mode.toString().contains("VERBOSE"))
		//	System.out.println("to "+CLIENT_ID+": "+msg);	
	}

	public String readline() throws Exception
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
	 * @throws Exception 
	 */
	private void log(String str,ClientConnection.Mode lvl) throws Exception
	{
		DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss] ");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		if (lvl.toString().contains("VERBOSE") )
		{
			System.out.println(timeFormat.format(new Date())+str);
		}
		//FIXME: this is not optimal. if a lot of log is expected we shouldn't open and close the stream for each line.
		if(lvl.toString().contains("LOG"))
		{
			//DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			//DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss] ");
			FileOutputStream fos=new FileOutputStream("LOG"+dateFormat.format(new Date()), true);
			String a;
			fos.write((timeFormat.format(new Date())+str+CRLF).getBytes());
			fos.close();
		}
		Date now=new Date();
		saveInDb(dateFormat.format(now)+'-'+timeFormat.format(now), str);
	}

	private void log(String str) throws Exception
	{
		log(str,mode);
	}
	////////Getters and Setters	
	void setSocket(Socket socket)
	{
		this.socket=socket;
	}

	boolean saveInDb(String date,String str) throws Exception
	{
		try
		{
		// Connecting from an external network. 
		Class.forName("com.mysql.jdbc.Driver"); 
		String url = "jdbc:mysql://173.194.246.47:3306";//?user=root"; 
		//}

		Connection conn = DriverManager.getConnection(url,"shervin","123456");
		ResultSet rs = conn.createStatement().executeQuery("use chats");

		//INSERT
		// create a sql date object so we can use it in our INSERT statement
		//Calendar calendar = Calendar.getInstance();
		//java.sql.Date startDate = new java.sql.Date(calendar.getTime().getTime());

		// the mysql insert statement
		String query = " INSERT INTO log (echo_time, text,id)" + " values (?, ?, ?)";

		// create the mysql insert preparedstatement
		PreparedStatement preparedStmt = conn.prepareStatement(query);
		preparedStmt.setString (1, date);
		preparedStmt.setString (2, str);
		preparedStmt.setLong(3, CLIENT_ID);
		//preparedStmt.setDate   (3, startDate);
		//preparedStmt.setBoolean(4, false);
		//preparedStmt.setInt    (5, 5000);

		// execute the preparedstatement
		preparedStmt.execute();

		/*
		//SELECT
		rs = conn.createStatement().executeQuery("SELECT * from users");

		//reading results
		// iterate through the java resultset
		while (rs.next())
		{
			//int id = rs.getInt("id");
			String echo_time = rs.getString("echo_time");
			String text = rs.getString("text");
			//Date dateCreated = rs.getDate("date_created");
			//boolean isAdmin = rs.getBoolean("is_admin");
			//int numPoints = rs.getInt("num_points");

			// print the results
			System.out.format("%s: %s\n",echo_time,text);
		}*/
		conn.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

}
