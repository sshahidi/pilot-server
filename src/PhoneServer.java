import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.mysql.jdbc.Statement;


public class PhoneServer {

	public enum JsonKeys{
		/* aps */mac(00),ssid(01),frequency(02),capabilities(11),date_created(21),date_modified(31),visit_count(10),last_visit(41),
		/* rps */rp_id(20),rp_name(51),rp_type(61),latitude(05),longitude(15),altitude(25),accuracy(35),floor_number(03),creator_id(30),floor_id(40),
		/* aprp */rss(04),
		/* events */ event_id(50),start_date(71),end_date(81),start_time(91),end_time(101),event_title(111),short_info(121),long_info(131),media_id(06),
		/* users */ user_id(60),user_name(141),passwd(151),email(161),first_name(171),last_name(181),birth_date(191),
		/* control keys */ command_type(201),local_mac(70),response_type(221),error_code(231),message(241),aps(07);
		/* data types: long(%10=0) String(%10=1) int(%10=2) short(%10=3) byte(%10=04) double (%10=5) inputStream(%10=6) JSONarray (%10=7)*/
		private final int id;
		JsonKeys(int id) {this.id=id;}
		public JsonKeysTypes getKeyType()
		{
			return JsonKeysTypes.values()[this.id %10];
		}
	}

	public enum JsonKeysTypes{
		longType,StringType,intType,shortType, byteType, doubleType, inputStreamType, JSONArrayType
	}
	public enum CommandType{
		Add_Manual_RP,Add_Auto_RP,Remove_RP,
		Localize,Get_Close_RPs,Get_Floor_RPs,
		Add_Event, Remove_Event, Get_RP_events,
		Update_RP,Update_Event,Disconnet;
		/*private final int id;
		COMMAND_TYPE(int id) { this.id = id; }
		public int getValue() { return id; }*/
	}


	public enum ResponseType{Manual_RP_Added,Auto_RP_Added,RP_Removed,
		Location, Close_RPs, Floor_RPs, 
		Event_Added, Event_Removed, RP_Events,
		RP_Updated,Event_Updated,Error;
	/*private final int id;
		COMMAND_TYPE(int id) { this.id = id; }
		public int getValue() { return id; }*/
	}


	public enum ErrorCode{Insufficient_Arguments,RP_Not_Found,RP_already_exists,
		RP_protected,No_Common_AP_Found,Event_Not_Found,
		Event_Already_exists,Event_protected,Unknown_Error,
		Localization_Error,Insufficient_Privilages, DB_Error;
	/*private final int id;
		COMMAND_TYPE(int id) { this.id = id; }
		public int getValue() { return id; }*/
	}

	///////Global variables
	private final String CRLF="\r\n";
	private BufferedReader reader;
	private DataOutputStream writer;
	private Slog log;
	private Slog.Mode log_filter;
	private Slog.Output log_output;
	private Socket socket;
	private final long CLIENT_ID;
	private Connection db_conn=null;

	////Constructors

	public PhoneServer() throws Exception {
		this(null,-1,Slog.Mode.ERROR,Slog.Output.STDOUT);
	}

	public PhoneServer(Socket socket,long client_id, Slog.Mode log_filter,Slog.Output log_output) throws Exception
	{
		this.log_filter=log_filter;
		this.log_output =log_output;
		log = new Slog(log_filter, log_output);
		this.socket=socket;
		this.reader=new BufferedReader(new InputStreamReader(socket.getInputStream())); //for reading lines
		this.writer=new DataOutputStream(socket.getOutputStream());	//for writing lines.
		this.CLIENT_ID=client_id;
	}


	///////////////Public methods:


	@SuppressWarnings("unchecked")
	public void start() throws Exception
	{
		writeline("HELLO WELOCME to Pilot Server. Service type: PHONE_SERVER");
		while(true)
		{
			//TODO: we need authetication first. otherewise anyone without loggin can work with sever.
			String str=readline();
			JSONParser jparser=new JSONParser();
			JSONObject json= (JSONObject) jparser.parse(str); //new JSONObject();
			log.d("whole json object: " + json.toString());
			//Type Local MAC Address(String)		Name (String)	AP_Numbers(int)	Latitude(float)	Longitude(float)	Floor_Number(int)	MAC1(String)	RSS1(int)	SSID1 (String:32-bytes)	Frequency1 (int)	Capabilites1 (String:32-bytes)
			CommandType command_type= CommandType.valueOf( (String) json.get(JsonKeys.command_type.toString()));
			JSONObject jresponse=new JSONObject();
			switch(command_type)
			{
			//TODO: support all command types.
			case Add_Manual_RP:
				jresponse=addRereferencePoint(json);
				break;
			case Add_Auto_RP:
				jresponse=addRereferencePoint(json); //currently, this case is the same as the one above.
				break;
				//			case Remove_RP:
				//				break;
			case Localize:
				jresponse=localize(json);
				break;
				//			case Get_Close_RPs:
				//				break;
				//			case Get_Floor_RPs:
				//				break;
				//			case Add_Event:
				//				break;
				//			case Remove_Event:
				//				break;
				//			case Get_RP_events:
				//				break;
			case Update_RP:
				jresponse=updateReferencePoint(json);
				break;
				//			case Update_Event:
				//				break;
			case Disconnet:
				//jresponse.put("response_code", ResponseType.DISCONNECT);
				str="quit";
				break;
			default:
				//JSONObject jresponse=new JSONObject();
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
				jresponse.put(JsonKeys.message.toString(), (String)("Unsupported request code received: "+command_type.toString()));
				writeline(jresponse.toJSONString());
			}
			if(str.equalsIgnoreCase("quit"))
				break;
			writeline(jresponse.toJSONString());
		}
		//writeline("QUIT");
		//closing connection to db
		if(db_conn !=null && !db_conn.isClosed())
			disconnetDb();
	}

	///////////// Private methods /////////////////////

	/* **********Handling requrests ********* */

	/**
	 * Adds a reference point by first making sure no already registered RP within 1 meters of this RP exists in DB.
	 * If an RP already exists, it is updated.
	 * If an RP doesn;t exist, it will be created.
	 * @param json The json object received containing the contents 
	 */
	@SuppressWarnings("unchecked")
	private JSONObject addRereferencePoint(JSONObject json) throws IOException,SQLException
	{
		log.d("Add reference point entry...");
		JSONObject jresponse=new JSONObject();
		/*
		 * Type-1	Local MAC Address(String)		Name (String)	AP_Numbers(int)	Latitude(float)	Longitude(float)	Floor_Number(int)	MAC1(String)	RSS1(int)	SSID1 (String:32-bytes)	Frequency1 (int)	Capabilites1 (String:32-bytes)	...
		 */
		long user_mac= json.containsKey(JsonKeys.local_mac.toString())? ((Number)json.get(JsonKeys.local_mac.toString())).longValue():0;
		String user_name=json.containsKey(JsonKeys.user_name.toString() ) ? (String) json.get(JsonKeys.user_name.toString()):"";
		long id=json.containsKey(JsonKeys.user_id.toString()) ? ((Number) json.get(JsonKeys.user_id.toString())).longValue():0;
		//TODO: we should make sure username and user id belong to the same person and belong to the same person who has logged in.

		//for now, if the location is not known, we can't add the RP.
		if(!json.containsKey(JsonKeys.latitude.toString()) || !json.containsKey(JsonKeys.longitude.toString()) )
		{
			log.e("lat/long is not contained in the json.");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.Insufficient_Arguments.toString());
			jresponse.put(JsonKeys.message.toString(), "The resquented RP could not be added since it does not have a (valid) lat/long.");
			return jresponse;
		}

		double lat= ((Number)json.get(JsonKeys.latitude.toString())).doubleValue();
		double lon= ((Number)json.get(JsonKeys.longitude.toString())).doubleValue();
		short floor=json.containsKey(JsonKeys.floor_number.toString())? ((Number) json.get(JsonKeys.floor_number.toString())).shortValue():0; //default value is 0
		double accuracy= json.containsKey(JsonKeys.accuracy.toString())?  ((Number) json.get(JsonKeys.accuracy.toString())).doubleValue():Double.NaN; //default value is NaN


		//checking with DB if we should update or we should create.

		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");

		//SELECT * FROM rps WHERE floor_number = floor AND latitude BETWEEN (lat-1meter,lat+1meter) AND longitude BETWEEN (lon-1,lon+1)
		ResultSet rs=null;
		for(int i=0;i<trials;i++)
		{
			rs=query("SELECT RP_id , latitude , longitude , floor_number FROM rps WHERE (latitude BETWEEN "+(lat-1d/111111d)+" AND "+(lat+1d/111111d)+") AND (longitude BETWEEN "+(lon-1d/111111d/Math.cos(lat*Math.PI/180d))+" AND "+(lon+1d/111111d/Math.cos(lat*Math.PI/180d))+")");
			if(rs!=null)
				break;
		}
		if(rs==null)
			throw new SQLException("Could not query db after "+trials+" trials");

		double closest_distance=10;
		long closest_rp_id=0; 
		while(rs.next())
		{
			//int id = rs.getInt("id");
			double neighbour_lat =rs.getDouble("latitude");
			double neighbour_lon = rs.getDouble("longitude");
			short closest_floor = rs.getShort("floor_number");
			if(closest_floor == floor && getDistanceFromLatLonInm(neighbour_lat, neighbour_lon, lat, lon) <closest_distance)
			{
				closest_distance=getDistanceFromLatLonInm(neighbour_lat, neighbour_lon, lat, lon);
				closest_rp_id= rs.getLong("RP_id");
			}
		}

		if (closest_rp_id!=0) //a close point existed before. its an update.
		{
			log.d("neighbour point exists. updating the closest point instead of inserting...");
			json.put(JsonKeys.rp_id.toString(), closest_rp_id);
			json.put(JsonKeys.command_type.toString(), CommandType.Update_RP.toString());
			return updateReferencePoint(json);

		}
		else //the point did not exist before. its a create.
		{
			//SELECT * FROM rps WHERE floor_number = floor AND latitude BETWEEN (lat-1meter,lat+1meter) AND longitude BETWEEN (lon-1,lon+1)
			JsonKeys[] args=new JsonKeys[] {JsonKeys.rp_name,JsonKeys.rp_type,JsonKeys.latitude,JsonKeys.longitude,JsonKeys.accuracy,JsonKeys.floor_number,JsonKeys.creator_id,JsonKeys.date_modified,JsonKeys.floor_id};//"visit_count","no_aps"};
			String query_begin ="INSERT INTO rps (";//name, rp_type,latitude, longitude, accuracy, floor_number, creator_id,date_modified,visit_count,no_aps)"
			String query_end =  " values ( "; //?, ?, ?,?,?,?,?,?,?,?)";
			ArrayList<Object> values=new ArrayList<>();
			ArrayList<JsonKeysTypes> column_types=new ArrayList<>();
			for(int i=0;i<args.length;i++)
			{
				if(json.containsKey(args[i].toString()))
				{
					query_begin+=args[i].toString()+", ";
					query_end+="?, ";
					column_types.add(args[i].getKeyType());
					values.add(json.get(args[i].toString()));
				}
			}

			//doing the insertion and retrieving the id of inserted rp.
			rs = manipulate(query_begin.substring(0, query_begin.length()-2)+") "+query_end.substring(0, query_end.length()-2)+")",column_types,values);
			if(rs==null || rs.next()==false)
			{
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.DB_Error.toString());
				jresponse.put(JsonKeys.message.toString(), "The database did not return a valid RP_id after insertion. RP insertaion failed.");
				return jresponse;
			}
			long rp_id = rs.getLong(1);
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.Manual_RP_Added.toString());
			jresponse.put(JsonKeys.rp_id.toString(), rp_id);


			//creating a new point in the DB.
			//int ap_numbers = (Integer)json.get("ap_numbers");
			JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
			String sql_query1="INSERT INTO aps (MAC,ssid,frequency,capabilities,date_modified) VALUES(?,?,?,?, CURRENT_TIMESTAMP)ON DUPLICATE KEY UPDATE ssid=?, frequency = ?, capabilities = ?, date_modified= CURRENT_TIMESTAMP";
			String sql_query2=" INSERT INTO aprp (RP_id,MAC,rss,date_modified) VALUES(?,?,?, CURRENT_TIMESTAMP ) ON DUPLICATE KEY UPDATE rss= ?";
			PreparedStatement preparedStmt_aps = db_conn.prepareStatement(sql_query1);
			PreparedStatement preparedStmt_aprps = db_conn.prepareStatement(sql_query2);
			//fetching the aps and their info
			for(int i=0;i<aps.size(); i++)
			{
				JSONObject obj= (JSONObject) aps.get(i);
				if(!obj.containsKey(JsonKeys.mac.toString()) || !obj.containsKey(JsonKeys.frequency.toString()) || ! obj.containsKey(JsonKeys.capabilities.toString()))
				{
					log.e("mac address is not contained in the json.");
					jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
					jresponse.put(JsonKeys.error_code.toString(), ErrorCode.Insufficient_Arguments.toString());
					jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address or frequency or capabilities field.");
					return jresponse;
				}
				long ap_mac= obj.containsKey(JsonKeys.mac.toString())? ((Number)obj.get(JsonKeys.mac.toString())).longValue():0;
				byte rss =  obj.containsKey(JsonKeys.rss.toString())? 	((Number) obj.get(JsonKeys.rss.toString())).byteValue():-128;
				String ssid =  obj.containsKey(JsonKeys.ssid.toString())? (String) obj.get(JsonKeys.ssid.toString()):"";
				int freq=  obj.containsKey(JsonKeys.frequency.toString())? ((Number) obj.get(JsonKeys.frequency.toString())).intValue():0;
				String capabilities =  obj.containsKey(JsonKeys.capabilities.toString())? (String) obj.get(JsonKeys.capabilities.toString()):"";

				preparedStmt_aps.setLong(1, ap_mac);
				preparedStmt_aps.setString(2, ssid);
				preparedStmt_aps.setInt(3, freq);
				preparedStmt_aps.setString(4, capabilities);
				preparedStmt_aps.setString(5, ssid);
				preparedStmt_aps.setInt(6, freq);
				preparedStmt_aps.setString(7, capabilities);
				preparedStmt_aps.addBatch();

				preparedStmt_aprps.setLong(1, rp_id);
				preparedStmt_aprps.setLong(2, ap_mac);
				preparedStmt_aprps.setByte(3, rss);
				preparedStmt_aprps.setByte(4, rss);
				preparedStmt_aprps.addBatch();
			}
			int[] res=preparedStmt_aps.executeBatch();
			log.d("insertion to aps result: "+Arrays.toString(res));
			preparedStmt_aprps.executeBatch();
			log.d("insertion to aprp result: "+ Arrays.toString(res));
			return jresponse;
		}
	}

	/**
	 * updates a reference point, it can be just adjusting the floor, lat, and long,
	 *  sending new rss values, providing higher accuracy, or changing rp type. 
	 *  The modifier_id will be changed during this process.
	 * @param json
	 */
	@SuppressWarnings("unchecked")
	private JSONObject updateReferencePoint(JSONObject json) throws IOException,SQLException
	{
		log.d("Update reference point entry...");
		JSONObject jresponse=new JSONObject();
		long user_mac= json.containsKey(JsonKeys.local_mac.toString())? ((Number)json.get(JsonKeys.local_mac.toString())).longValue():0;
		String user_name=json.containsKey(JsonKeys.user_name.toString() ) ? (String) json.get(JsonKeys.user_name.toString()):"";
		long id=json.containsKey(JsonKeys.user_id.toString()) ? ((Number) json.get(JsonKeys.user_id.toString())).longValue():0;
		//TODO: we should make sure username and user id belong to the same person and belong to the same person who has logged in.

		//for now, if the location is not known, we can't add the RP.
		if(!json.containsKey(JsonKeys.rp_id.toString()  ))
		{
			log.e("rp_id is not contained in the json.");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.Insufficient_Arguments.toString());
			jresponse.put(JsonKeys.message.toString(), "The resquented RP could not be updated since it does not have a (valid) rp_id.");
			return jresponse;
		}

		long rp_id=((Number) json.get(JsonKeys.rp_id.toString())).longValue();
		double lat= json.containsKey(JsonKeys.latitude.toString())? ((Number)json.get(JsonKeys.latitude.toString())).doubleValue():Double.NaN;
		double lon= json.containsKey(JsonKeys.longitude.toString())? ((Number)json.get(JsonKeys.longitude.toString())).doubleValue():Double.NaN;
		short floor=json.containsKey(JsonKeys.floor_number.toString())? ((Number) json.get(JsonKeys.floor_number.toString())).shortValue():0; //default value is 0
		double accuracy= json.containsKey(JsonKeys.accuracy.toString())?  ((Number) json.get(JsonKeys.accuracy.toString())).doubleValue():Double.NaN; //default value is NaN

		// UPDATE rps  SET name='test2',rp_type=1,latitude=11,longitude=12,accuracy=10,floor_number=null,creator_id=2,date_modified=CURRENT_TIMESTAMP,floor_id=2 WHERE rp_id=1;
		JsonKeys[] args=new JsonKeys[] {JsonKeys.rp_name,JsonKeys.rp_type,JsonKeys.latitude,JsonKeys.longitude,JsonKeys.accuracy,JsonKeys.floor_number,JsonKeys.creator_id,JsonKeys.date_modified,JsonKeys.floor_id};//"visit_count","no_aps"};
		String query_begin ="UPDATE rps SET ";//name, rp_type,latitude, longitude, accuracy, floor_number, creator_id,date_modified,visit_count,no_aps)"
		String query_end =  "date_modified=CURRENT_TIMESTAMP WHERE rp_id= "+rp_id;
		ArrayList<Object> values=new ArrayList<>();
		ArrayList<JsonKeysTypes> column_types=new ArrayList<>();
		for(int i=0;i<args.length;i++)
		{
			if(json.containsKey(args[i].toString()))
			{
				query_begin+=args[i].toString()+"= ?, ";
				//query_end+="?, ";
				column_types.add(args[i].getKeyType());
				values.add(json.get(args[i].toString()));
			}
		}

		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");

		//doing the insertion and retrieving the id of inserted rp.
		ResultSet rs = manipulate(query_begin+query_end,column_types,values);
		if(rs==null)
		{
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.DB_Error.toString());
			jresponse.put(JsonKeys.message.toString(), "The database did not return a valid RP_id after insertion. RP insertaion failed.");
			return jresponse;
		}
		jresponse.put(JsonKeys.response_type.toString(), ResponseType.RP_Updated.toString());
		jresponse.put(JsonKeys.rp_id.toString(), rp_id);


		//creating a new point in the DB.
		//int ap_numbers = (Integer)json.get("ap_numbers");
		JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
		String sql_query1="INSERT INTO aps (MAC,ssid,frequency,capabilities,date_modified) VALUES(?,?,?,?, CURRENT_TIMESTAMP)ON DUPLICATE KEY UPDATE ssid=?, frequency = ?, capabilities = ?, date_modified= CURRENT_TIMESTAMP";
		String sql_query2=" INSERT INTO aprp (RP_id,MAC,rss,date_modified) VALUES(?,?,?, CURRENT_TIMESTAMP ) ON DUPLICATE KEY UPDATE rss= ?";
		PreparedStatement preparedStmt_aps = db_conn.prepareStatement(sql_query1);
		PreparedStatement preparedStmt_aprps = db_conn.prepareStatement(sql_query2);
		//fetching the aps and their info
		for(int i=0;i<aps.size(); i++)
		{
			JSONObject obj= (JSONObject) aps.get(i);
			if(!obj.containsKey(JsonKeys.mac.toString()) || !obj.containsKey(JsonKeys.frequency.toString()) || ! obj.containsKey(JsonKeys.capabilities.toString()))
			{
				log.e("mac address is not contained in the json.");
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.Insufficient_Arguments.toString());
				jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address or frequency or capabilities field.");
				return jresponse;
			}
			long ap_mac= obj.containsKey(JsonKeys.mac.toString())? ((Number)obj.get(JsonKeys.mac.toString())).longValue():0;
			byte rss =  obj.containsKey(JsonKeys.rss.toString())? 	((Number) obj.get(JsonKeys.rss.toString())).byteValue():-128;
			String ssid =  obj.containsKey(JsonKeys.ssid.toString())? (String) obj.get(JsonKeys.ssid.toString()):"";
			int freq=  obj.containsKey(JsonKeys.frequency.toString())? ((Number) obj.get(JsonKeys.frequency.toString())).intValue():0;
			String capabilities =  obj.containsKey(JsonKeys.capabilities.toString())? (String) obj.get(JsonKeys.capabilities.toString()):"";

			preparedStmt_aps.setLong(1, ap_mac);
			preparedStmt_aps.setString(2, ssid);
			preparedStmt_aps.setInt(3, freq);
			preparedStmt_aps.setString(4, capabilities);
			preparedStmt_aps.setString(5, ssid);
			preparedStmt_aps.setInt(6, freq);
			preparedStmt_aps.setString(7, capabilities);
			preparedStmt_aps.addBatch();

			preparedStmt_aprps.setLong(1, rp_id);
			preparedStmt_aprps.setLong(2, ap_mac);
			preparedStmt_aprps.setByte(3, rss);
			preparedStmt_aprps.setByte(4, rss);
			preparedStmt_aprps.addBatch();
		}
		int[] res=preparedStmt_aps.executeBatch();
		log.d("insertion to aps result: "+Arrays.toString(res));
		preparedStmt_aprps.executeBatch();
		log.d("insertion to aprp result: "+ Arrays.toString(res));
		return jresponse;

	}

	@SuppressWarnings("unchecked")
	private JSONObject localize(JSONObject json) throws SQLException
	{
		log.d("Localize entry...");
		JSONObject jresponse=new JSONObject();		
		long user_mac= json.containsKey(JsonKeys.local_mac.toString())? ((Number)json.get(JsonKeys.local_mac.toString())).longValue():0;
		String user_name=json.containsKey(JsonKeys.user_name.toString() ) ? (String) json.get(JsonKeys.user_name.toString()):"";
		long id=json.containsKey(JsonKeys.user_id.toString()) ? ((Number) json.get(JsonKeys.user_id.toString())).longValue():0;
		//TODO: we should make sure username and user id belong to the same person and belong to the same person who has logged in.
		
		//connecting to db if necessary
		final int trials=3; //number of trials for connecting to db.
		for(int i=0;i<trials;i++)
		{
			if(db_conn ==null || db_conn.isClosed())
				connectDb();
			else
				break;
		}
		if(db_conn==null) //after trying it is still not connected
			throw new SQLException("Could not connect to db after "+trials+" trials");
		
		JSONArray aps= (JSONArray) json.get(JsonKeys.aps.toString());
		String macs="";
		for(int i=0;i<aps.size();i++)
		{
			JSONObject obj=(JSONObject) aps.get(i);
			if(!obj.containsKey(JsonKeys.mac.toString()) )
			{
				jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
				jresponse.put(JsonKeys.error_code.toString(), ErrorCode.Insufficient_Arguments.toString());
				jresponse.put(JsonKeys.message.toString(), "At least one of the resquented APs could not be added since it did not have a (valid) MAC address.");
				return jresponse;
			}
			long mac= ((Number)obj.get(JsonKeys.mac.toString())).longValue();
			//for now we don't care about rss!
			//byte rss= ((Number) obj.get(JsonKeys.rss.toString())).byteValue();
			macs+= mac+(i < aps.size()-1? ", ":"");
		}
		
		String query_str="SELECT rps.latitude, rps.longitude, rps.floor_number, aprp.rp_id, COUNT(MAC) AS NumberOfAPs"+ 
                     " FROM (aprp INNER JOIN rps ON aprp.rp_id = rps.rp_id)"+
                     " WHERE MAC IN ("+ macs+" )"+
                     " GROUP BY rp_id"+
                     " ORDER BY NumberOfAPs DESC";
		ResultSet rs=query(query_str);
		if(rs==null)
		{
			log.e("DB query failed. couldn't find location");
			jresponse.put(JsonKeys.response_type.toString(), ResponseType.Error.toString());
			jresponse.put(JsonKeys.error_code.toString(), ErrorCode.DB_Error.toString());
			jresponse.put(JsonKeys.message.toString(), "Could not locate the user. DB query failed");
			return jresponse;
		}
		
		//Doing 1NN now! just returning the point with the most number of common APs.
		//while(rs.next())
		//{
		rs.next();
		double lat =rs.getDouble("latitude");
		double lon = rs.getDouble("longitude");
		short floor = rs.getShort("floor_number");
		long rp_id= rs.getLong("RP_id");
		jresponse.put(JsonKeys.response_type.toString(), ResponseType.Location.toString());
		jresponse.put(JsonKeys.latitude.toString(), lat);
		jresponse.put(JsonKeys.longitude.toString(), lon);
		jresponse.put(JsonKeys.floor_number.toString(), floor);
		jresponse.put(JsonKeys.rp_id.toString(), rp_id);
		//}
		log.d("returning statement: "+jresponse.toJSONString());
		return jresponse;
	}


	////// IO functions.

	private Connection connectDb()
	{
		// Connecting from an external network. 
		try {
			//TODO: this shouldn't be hardcoded!
			Class.forName("com.mysql.jdbc.Driver");
			String url = "jdbc:mysql://173.194.247.241:3306";//?user=root"; 
			db_conn = DriverManager.getConnection(url,"shervin","123456");
			//by default using the location db when stablishing connection
			ResultSet rs = db_conn.createStatement().executeQuery("use loc_db"); 
			return db_conn;
		} catch (ClassNotFoundException | SQLException e) {
			log.e("Serving client "+CLIENT_ID+": could not connect to db.");
			log.printStackTrace(e,Slog.Mode.DEBUG);
			return null;
		} 
	}
	private boolean disconnetDb()
	{
		try {
			db_conn.close();
			return true;
		} catch (SQLException e) {
			log.w("Serving client "+CLIENT_ID+": Error in closing database connection.");
			log.printStackTrace(e, Slog.Mode.DEBUG);
			return false;
		}
	}
	private ResultSet query(String query_str)
	{
		log.d("calling a select statement. qurey_str: "+query_str);
		try {
			return db_conn.createStatement().executeQuery(query_str);
		} catch (SQLException e) {
			log.w("Serving client "+CLIENT_ID+": Error in querying database.");
			log.printStackTrace(e, Slog.Mode.DEBUG);
			return null;
		}

	}
	/**
	 * can call for a insert,update, or delete sql statement via {@link com.mysql.jdbc.PreparedStatement},
	 * @param query_str the query string with parameters set as '?'
	 * @param columns the parameters data types
	 * @param values the parameter values
	 * @return a {@link com.mysql.jdbc.ResultSet} containing the auto_generated_keys. If no auto generated keys are returned, result set will be empty. 
	 * If a problem happens in the call, <code>null</code> is returned.
	 */
	private ResultSet manipulate(String query_str,ArrayList<JsonKeysTypes> column_types, ArrayList<Object> values)
	{
		log.d("calling a prepared statement. qurey_str: "+query_str);
		try
		{
			//" INSERT INTO log (echo_time, text,id)" + " values (?, ?, ?)";
			String query = query_str;



			// create the mysql insert preparedstatement
			PreparedStatement preparedStmt = db_conn.prepareStatement(query,PreparedStatement.RETURN_GENERATED_KEYS);
			for(int i=0;i<values.size();i++)
			{
				switch (column_types.get(i))
				{
				case StringType:
					preparedStmt.setString (i+1, (String)values.get(i));
					break;
				case longType:
					preparedStmt.setLong(i+1, ((Number)values.get(i)).longValue());
					break;
				case doubleType:
					preparedStmt.setDouble(i+1, ((Number)values.get(i)).doubleValue());
					break;
				case byteType:
					preparedStmt.setByte(i+1, ((Number)values.get(i)).byteValue());
					break;
				case intType:
					preparedStmt.setInt(i+1, ((Number)(values.get(i))).intValue());
					break;
				case shortType:
					preparedStmt.setShort(i+1, ((Number)(values.get(i))).shortValue());
					break;
					//not implelmented for now.	
					//case inputStreamType:
					//	preparedStmt.setblob(i+1, ...
					//	break;
				default:
					throw new Exception("Unkonwn column type, when preparing the sql statement.");
				}
			}
			// execute the preparedstatement
			log.d("final prepared statement: "+ preparedStmt.toString());
			preparedStmt.executeUpdate();
			
			return preparedStmt.getGeneratedKeys();
		}
		catch(Exception e)
		{
			log.e("Writing to DB was not successful");
			log.printStackTrace(e, Slog.Mode.DEBUG);
			return null;
		}
	}
	private boolean saveInDb(String date,String str) throws Exception
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

	private void writeline(String msg) throws Exception
	{
		writer.writeBytes(msg+CRLF);
		writer.flush();
		log.v("to "+CLIENT_ID+": "+msg);	
	}

	private String readline() throws Exception
	{
		String str= reader.readLine();
		log.v("from "+CLIENT_ID+": "+str);
		return str;
	}

	///Other methods that come handy 
	double getDistanceFromLatLonInm(double lat1,double lon1,double lat2,double lon2) 
	{
		double R = 6378137; // Avg Radius of the earth in m
		double dLat = (lat2-lat1)*(Math.PI/180);  // degree2radian
		double dLon = (lon2-lon1)*(Math.PI/180);  // degree2radian
		double a = 
				Math.sin(dLat/2) * Math.sin(dLat/2) +
				Math.cos(lat1*(Math.PI/180)) * Math.cos(lat2*(Math.PI/180)) * 
				Math.sin(dLon/2) * Math.sin(dLon/2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		double d = R * c; // Distance in m
		return d;
	}


	////////Getters and Setters	
	public void setSocket(Socket socket)
	{
		this.socket=socket;
	}


}
