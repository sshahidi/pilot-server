import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Slog {

	private final String CRLF="\r\n";
	public  enum Mode{VERBOSE,DEBUG,INFO,WEARNING,ERROR};
	public  enum Output{STDOUT,NETWORK,FILE};
	public  String log_path="./";
	public  int log_port=9875;
	private  Output output;
	private Mode filter; 

	/**
	 * constructor 1, with default params: 
	 * verbose filter, 
	 * output to standard output, 
	 * and current working directory as the log path.
	 */
	Slog()
	{
		this(Mode.VERBOSE,Output.STDOUT,"./");
	}
	
	Slog(Mode filter)
	{
		this(filter,Output.STDOUT,"./");
	}
	
	Slog(Mode filter,Output output)
	{
		this(filter,output,"./");
	}
	
	/**
	 * Constructor
	 * @param filter the level in which the logs should be written. Anything below the filter level will be ignored.
	 * @param output the output type.
	 * @param log_path in case the output is to file, the directory of log files.
	 */
	Slog(Mode filter,Output output,String log_path)
	{
		this.output=output;
		this.filter=filter;
		this.log_path=log_path;

	}
	
	

	/**
	 * Verbose level logging
	 * @param str
	 */
	public  void v(String str) //throws IOException
	{
		log(str,Mode.VERBOSE);
	}


	/**
	 * debug level logging
	 * @param str
	 */
	public  void d(String str) //throws IOException
	{
		log(str,Mode.DEBUG);
	}

	/**
	 * debug level logging
	 * @param str
	 */
	public  void i(String str) //throws IOException
	{
		log(str,Mode.INFO);
	}


	/**
	 * Warning level logging
	 * @param str
	 */
	public  void w(String str) //throws IOException
	{
		log(str,Mode.WEARNING);
	}


	/**
	 * Error level logging
	 * @param str
	 */
	public  void e(String str) //throws IOException
	{
		log(str,Mode.ERROR);
	}
	
	public void printStackTrace(Exception e,Slog.Mode lvl)
	{
		StackTraceElement[] elements=e.getStackTrace();
		String errmsg="";
		for(int i=0;i < elements.length;i++)
			errmsg= errmsg+CRLF+(elements[i].toString());
		log(e.getMessage()+CRLF+errmsg,lvl);
	}
	
	/**
	 * used for logging purposes with different levels of importance.
	 * @param str the string to log (show in the standard output or write to a file.
	 * @param lvl the level of importance.
	 */
	public  void log(String str,Slog.Mode lvl) //throws IOException
	{
		try
		{
		if(this.output== Output.STDOUT)
		{

			if (lvl.compareTo( this.filter) >=0 )
			{
				DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss.SSS] ");
				if(lvl==Mode.ERROR)
					System.err.println(timeFormat.format(new Date())+lvl.toString()+": "+str);
				else
					System.out.println(timeFormat.format(new Date())+lvl.toString()+": "+str);
			}
		}
		//FIXME: this is not optimal. if a lot of log is expected we shouldn't open and close the stream for each line.
		else if(this.output== Output.FILE)
		{
			if (lvl.compareTo( this.filter) >=0 )
			{
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss.SSS] ");
				FileOutputStream fos=new FileOutputStream(log_path+ "LOG"+dateFormat.format(new Date()), true);
				fos.write((timeFormat.format(new Date())+lvl.toString()+": "+str+CRLF).getBytes());
				fos.close();
			}
		}
		} catch (IOException e) {
			System.err.println("Error happend in Slog while trying to write this message to output:\n"+
					str+"\n Error reason: "+e.getMessage());
		}
	}
	
	//////Getters and Setters
	
	public  Output getOutput() {
		return output;
	}

	public  void setOutput(Output output) {

		if(output==Output.STDOUT)
			System.out.println("Log to standard output selected.");
		else if (output==Output.FILE)
		{
			System.err.println("File logging not impelented yet. sorry. setting output to STDOUT");
			output=Output.STDOUT;
			//System.out.println("Log to file selected. output path: "+log_path);
		}
		else if (output==Output.NETWORK)
		{
			System.err.println("Network logging not impelented yet. sorry. setting output to STDOUT");
			output=Output.STDOUT;
			//System.out.println("Log to network selected. log port: "+log_port);
		}
		this.output = output;
	}

	public Mode getFilter() {
		return filter;
	}

	public void setFilter(Mode filter) {
		this.filter = filter;
		this.i("Filter mode changed to : "+filter.toString());
	}

	@Override
	public String toString() {
		return "Slog [output=" + output + ", filter=" + filter + ", log_path=" + log_path + ", log_port=" + log_port+ "]";
	}
	


}
