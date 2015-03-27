import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.StringTokenizer;

import javax.swing.plaf.metal.MetalIconFactory.FolderIcon16;




public class FtpServer
{

	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private ClientConnection.Mode mode;
	private Socket control_socket;
	private Socket data_socket;
	private final long CLIENT_ID;
	private String user_name="";

	DataInputStream din;
	DataOutputStream dout;
	BufferedReader br;

	public FtpServer()  throws IOException 
	{
		this(null,-1,ClientConnection.Mode.SILENT);
	}

	public FtpServer(Socket control_socket,long client_id, ClientConnection.Mode mode) throws IOException
	{
		this.mode=mode;
		this.control_socket=control_socket;
		this.reader=new BufferedReader(new InputStreamReader(control_socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(control_socket.getOutputStream());	//for writing lines.
		this.CLIENT_ID=client_id;


		System.out.println("FTP Client Connected ...");
	}


	public void start() throws IOException
	{
		writeline("220 WElCOME to ECE361 FTP server");

		while(true)
		{
			//System.out.println("Waiting for Command ...");
			//reading and tokenizing the command.	
			String command=readline();
			//log(command);
			StringTokenizer st=new StringTokenizer(command,"\r\n\t ");
			String[] subcommands=new String[5];

			for(int i=0; st.hasMoreTokens() && i<subcommands.length ;i++)
			{
				subcommands[i]=st.nextToken();
			}

			////////available commands;
			if(subcommands[0].equalsIgnoreCase("SYST"))
			{
				writeline("215 "+System.getProperty("os.name"));
			}
			else if(subcommands[0].equalsIgnoreCase("USER"))
			{
				user_name=subcommands[1];
				writeline("230 "+user_name +" logged in.");
			}
			else if(subcommands[0].equalsIgnoreCase("PWD"))
			{
				writeline("250 \""+System.getProperty("user.dir")+"\"");

			}
			else if(subcommands[0].equalsIgnoreCase("LIST"))
			{
				if(subcommands[1]!= null && !subcommands[1].equals(""))
					listDir(subcommands[1]);
				else
					listDir("./");

			}
			else if(subcommands[0].equalsIgnoreCase("PORT"))
			{
				try
				{
					StringTokenizer st2=new StringTokenizer(subcommands[1],",");
					log("number of tokens:"+st2.countTokens());
					String host=st2.nextToken()+"."+st2.nextToken()+"."+st2.nextToken()+"."+st2.nextToken();
					int p1=Integer.valueOf(st2.nextToken()), p2=Integer.valueOf(st2.nextToken());
					int dport=p1*256 + p2;
					log("p1,p2: "+p1+","+p2+" host: "+host+" port: "+dport);
					data_socket=new Socket(host,dport);
					dout=new DataOutputStream(data_socket.getOutputStream());
					din=new DataInputStream(data_socket.getInputStream());
					log("Data connection established to :"+CLIENT_ID);
					writeline("200 OK");
				}
				catch(Exception e)
				{
					writeline("500 entering passive mode was not successful.");
				}
			}
			//enter passive mode
			else if(subcommands[0].equalsIgnoreCase("EPSV"))
			{
				try
				{
					setupDataConnection();
				}
				catch(Exception e)
				{
					writeline("500 entering passive mode was not successful.");
				}
			}
			else if(subcommands[0].equalsIgnoreCase("RETR"))
			{
				SendFile(subcommands[1]);
			}
			else if(subcommands[0].equalsIgnoreCase("STOR"))
			{
				ReceiveFile(subcommands[1]);
			}
			else if(subcommands[0].equalsIgnoreCase("FEAT"))
			{
				//we don't have features now:
				writeline("211 No features supported.");
				//if we have features:
				//writeline("211-Extensions supported:"); writeline("MLST size*;create;modify*;perm;media-type"); writeline("SIZE"); writeline("COMPRESSION"); writeline("211 END");
			}
			else if(subcommands[0].contains("ABOR"))
			{
				if(data_socket!=null && !data_socket.isClosed())
					data_socket.close();
				writeline("226 data connection closed.");

			}
			else if(subcommands[0].equalsIgnoreCase("QUIT") || subcommands[0].equalsIgnoreCase("EXIT"))
			{
				if(data_socket!=null && !data_socket.isClosed())
					data_socket.close();
				writeline("221 Goodbye.");
				break;
			}
			else
			{
				writeline("202 Command not implemented.");
			}
		}
	}


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
		writeline("229 Entering Extended Passive Mode (|||"+port+"|).");
		data_socket= ss.accept();
		dout=new DataOutputStream(data_socket.getOutputStream());
		din=new DataInputStream(data_socket.getInputStream());
		log("Data connection established to :"+CLIENT_ID);
	}

	void SendFile(String filename) throws IOException
	{        
		File f=new File(filename);
		if(!f.exists())
		{
			writeline("550 	Requested action not taken. File unavailable (e.g., file not found, no access).");
			writeline("426 	Connection closed; transfer aborted.");
			data_socket.close();
			return;
		}
		else
		{
			writeline("150 here comes the file:");
			FileInputStream fin=new FileInputStream(f);
			while(true)
			{
				byte[] buffer=new byte[1024];
				int len=fin.read(buffer);
				if(len<=0)
					break;
				dout.write(buffer, 0, len);
			}    
			fin.close();                                
		}
		writeline("226 Transfer complete.");
		data_socket.close();
	}

	void ReceiveFile(String filename) throws IOException
	{
		File f=new File(filename);

		if(f.exists())
		{
			//TODO: do something! for now we overwrite!
		}

		writeline("150 waiting to receive the file");


		FileOutputStream fout=new FileOutputStream(f,false);
		int i=0;
		while(!data_socket.isClosed())
		{
			byte[] buffer=new byte[1024];
			int len=din.read(buffer,0,buffer.length);
			//System.out.println(len+"bytes received");
			if(len<=0)
			{
				break;                    
			}
			fout.write(buffer,0,len);
			fout.flush();
		}
		fout.close();

		writeline("226 Transfer complete.");
		data_socket.close();


	}


	void listDir(String path) throws IOException
	{

		writeline("150 Here comes the directory listing");
		//finding the list of directories and files.
		File working_dir=new File(path);
		File[] files_list = working_dir.listFiles();
		for(int i=0; i< files_list.length; i++)
		{
			datawriteline(files_list[i].getName());
		}
		//writeline("250 Directory send OK.");
		writeline("226 Directory send OK.");
		data_socket.close();
	}


	public void datawriteline(String msg) throws IOException
	{
		dout.writeBytes(msg+CRLF);
		dout.flush();
		//if(this.mode.toString().contains("VERBOSE"))
		//System.out.println("to "+CLIENT_ID+": "+msg);	
		log("on DATA, to "+CLIENT_ID+": "+msg);
	}


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