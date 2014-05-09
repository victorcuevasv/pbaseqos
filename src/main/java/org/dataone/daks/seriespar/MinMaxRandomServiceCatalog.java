
package org.dataone.daks.seriespar;

import java.util.List;
import java.util.Hashtable;
import java.util.Random;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;


public class MinMaxRandomServiceCatalog {
	
	
	private Hashtable<String, QoSMetrics> minMetricsHT;
	private Hashtable<String, QoSMetrics> maxMetricsHT;
	private List<String> serviceNames;
	private static double MINTIME = 10.0;
	private static double MAXTIME = 110.0;
	private static double MINCOST = 10.0;
	private static double MAXCOST = 110.0;
	private static double MINRELIABILITY = 0.95;
	private static double MAXRELIABILITY = 1.0;
	private static double TIMERANGE = 10.0;
	private static double COSTRANGE = 10.0;
	
	
	public MinMaxRandomServiceCatalog() {
		
	}

	
	public static void main(String args[]) {
		if( args.length != 2 ) {
			System.out.println("Usage: java org.dataone.daks.seriespar.MinMaxRandomServiceCatalog <num services> <output file>");
			System.exit(0);
		}
		MinMaxRandomServiceCatalog catalog = new MinMaxRandomServiceCatalog();
		catalog.initializeRandom(Integer.parseInt(args[0]));
		catalog.saveAsJSONFile(args[1]);
	}
	
	
	public void initializeRandom(int nServices) {
		this.minMetricsHT = new Hashtable<String, QoSMetrics>();
		this.maxMetricsHT = new Hashtable<String, QoSMetrics>();
		Random rand = new Random();
		this.serviceNames = new ArrayList<String>();
		for(int i = 1; i <= nServices; i++)
			this.serviceNames.add("s" + i);
		for( String servName : this.serviceNames ) {
			//Time and cost metrics lie within a range
			double targetTime = randDouble(rand, MINTIME, MAXTIME);
			double targetCost = randDouble(rand, MINCOST, MAXCOST);
			double minTime = targetTime - TIMERANGE;
			double maxTime = targetTime + TIMERANGE;
			if( minTime < MINTIME )
				minTime = MINTIME;
			if( maxTime > MAXTIME )
				maxTime = MAXTIME;
			double minCost = targetCost - COSTRANGE;
			double maxCost = targetCost + COSTRANGE;
			if( minCost < MINCOST )
				minCost = MINCOST;
			if( maxCost > MAXCOST )
				maxCost = MAXCOST;
			//Reliability is a specific probability
			double reliability = randDouble(rand, MINRELIABILITY, MAXRELIABILITY);
			QoSMetrics minMetrics = new QoSMetrics(minTime, minCost, reliability);
			this.minMetricsHT.put(servName, minMetrics);
			QoSMetrics maxMetrics = new QoSMetrics(maxTime, maxCost, reliability);
			this.maxMetricsHT.put(servName, maxMetrics);
		}
	}
	
	
	public void initializeFromJSONString(String jsonStr) {
		this.minMetricsHT = new Hashtable<String, QoSMetrics>();
		this.maxMetricsHT = new Hashtable<String, QoSMetrics>();
		try {
			JSONArray jsonArray = new JSONArray(jsonStr);
			for( int i = 0; i < jsonArray.length(); i++ ) {
				JSONObject values = jsonArray.getJSONObject(i);
				String servName = values.getString("name");
				double minTime = values.getDouble("mintime");
				double maxTime = values.getDouble("maxtime");
				double minCost = values.getDouble("mincost");
				double maxCost = values.getDouble("maxcost");
				double reliability = values.getDouble("reliability");
				QoSMetrics minMetrics = new QoSMetrics(minTime, minCost, reliability);
				this.minMetricsHT.put(servName, minMetrics);
				QoSMetrics maxMetrics = new QoSMetrics(maxTime, maxCost, reliability);
				this.maxMetricsHT.put(servName, maxMetrics);
			}		
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	public void initializeFromJSONFile(String filename) {
		String jsonStr = this.readFile(filename);
		this.initializeFromJSONString(jsonStr);
	}
	
	
	private String readFile(String filename) {
		StringBuilder builder = null;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line = null;
			builder = new StringBuilder();
			String NEWLINE = System.getProperty("line.separator");
			while( (line = reader.readLine()) != null ) {
				builder.append(line + NEWLINE);
			}
			reader.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return builder.toString();
	}
	
	
	public void saveAsJSONFile(String filename) {
		try {
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
			String jsonStr = this.toJSONArray().toString();
			writer.print(jsonStr);
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public JSONArray toJSONArray() {
		JSONArray jsonArray = new JSONArray();
		try {
			JSONObject values = null;
			String servName = null;
			for(Map.Entry<String, QoSMetrics> entry : this.minMetricsHT.entrySet()) {
				servName = entry.getKey();
				QoSMetrics minMetrics = entry.getValue();
				QoSMetrics maxMetrics = this.maxMetricsHT.get(servName);
				values = new JSONObject();
				values.put("mintime", minMetrics.getTime());
				values.put("maxtime", maxMetrics.getTime());
				values.put("mincost", minMetrics.getCost());
				values.put("maxcost", maxMetrics.getCost());
				values.put("reliability", minMetrics.getReliability());
			    values.put("name", servName);
			    jsonArray.put(values);
			}
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		return jsonArray;
	}
	
	
	public QoSMetrics getMinQoSMetrics(String servName) {
		QoSMetrics minMetrics = this.minMetricsHT.get(servName);
		return minMetrics;
	}
	
	
	public QoSMetrics getMaxQoSMetrics(String servName) {
		QoSMetrics maxMetrics = this.maxMetricsHT.get(servName);
		return maxMetrics;
	}
	
	
	public List<String> getServicesList() {
		List<String> services = new ArrayList<String>();
		for(Map.Entry<String, QoSMetrics> entry : this.minMetricsHT.entrySet()) {
			String servName = entry.getKey();
			services.add(servName);
		}
		return services;
	}
	
	
	public static int randInt(Random rand, int min, int max) {
	    int randomNum = rand.nextInt((max - min) + 1) + min;
	    return randomNum;
	}
	
	
	public static double randDouble(Random rand, double min, double max) {
	    double randomNum = min + (max - min) * rand.nextDouble();
	    return randomNum;
	}

	
}



