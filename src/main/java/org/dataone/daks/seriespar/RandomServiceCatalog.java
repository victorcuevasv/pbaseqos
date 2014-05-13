
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


public class RandomServiceCatalog {
	
	
	private Hashtable<String, QoSMetrics> ht;
	
	
	public RandomServiceCatalog() {
		this.ht = new Hashtable<String, QoSMetrics>();
	}

	
	public static void main(String args[]) {
		RandomServiceCatalog catalog = new RandomServiceCatalog();
		String phrase = catalog.readFile(args[0]);
		List<String> serviceNames = catalog.generateServiceList(phrase);
		catalog.initializeRandom(serviceNames);
		catalog.saveAsJSONFile(args[1]);
	}
	
	
	public void initializeRandom(List<String> serviceNames) {
		Random rand = new Random();
		for( String servName : serviceNames ) {
			double time = randDouble(rand, 1.0, 10.0);
			double cost = randDouble(rand, 0.0, 10.0);
			int dice = randInt(rand, 1, 4);
			double reliability = 1.0;
			if( dice == 2 )
				reliability = 0.5 + randDouble(rand, 0.0, 0.5);
			QoSMetrics qosMetrics = new QoSMetrics(time, cost, reliability);
			this.ht.put(servName, qosMetrics);
		}
	}
	
	
	public void initializeFromJSONString(String jsonStr) {
		try {
			JSONArray jsonArray = new JSONArray(jsonStr);
			for( int i = 0; i < jsonArray.length(); i++ ) {
				JSONObject qosMetricsJSON = jsonArray.getJSONObject(i);
				JSONObject valuesJSON = qosMetricsJSON.getJSONObject("values");
				String servName = qosMetricsJSON.getString("name");
				double time = valuesJSON.getDouble("time");
				double cost = valuesJSON.getDouble("cost");
				double reliability = valuesJSON.getDouble("reliability");
				QoSMetrics qosMetrics = new QoSMetrics(time, cost, reliability);
				this.ht.put(servName, qosMetrics);
			}		
		}
		catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	
	public void initializeSetZero(List<String> servicesList) {
		for( String service: servicesList ) {
			QoSMetrics qosMetrics = new QoSMetrics(0.0, 0.0, 0.0);
			this.ht.put(service, qosMetrics);
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
			for(Map.Entry<String, QoSMetrics> entry : this.ht.entrySet()) {
				servName = entry.getKey();
				QoSMetrics qosMetrics = entry.getValue();
				values = new JSONObject();
				values.put("time", qosMetrics.getTime());
				values.put("cost", qosMetrics.getCost());
				values.put("reliability", qosMetrics.getReliability());
				JSONObject metrics = new JSONObject();
			    metrics.put("name", servName);
			    metrics.put("values", values);
			    jsonArray.put(metrics);
			}
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		return jsonArray;
	}
	
	
	public QoSMetrics getQoSMetrics(String servName) {
		QoSMetrics metrics = this.ht.get(servName);
		return metrics;
	}
	
	
	public void addQoSMetrics(String servName, QoSMetrics qosMetrics) {
		this.ht.put(servName, qosMetrics);
	}
	
	
	public static int randInt(Random rand, int min, int max) {
	    int randomNum = rand.nextInt((max - min) + 1) + min;
	    return randomNum;
	}
	
	
	public static double randDouble(Random rand, double min, double max) {
	    double randomNum = min + (max - min) * rand.nextDouble();
	    return randomNum;
	}
	
	
	public List<String> generateServiceList(String phrase) {
		StringTokenizer tokenizer = new StringTokenizer(phrase);
		List<String> servList = new ArrayList<String>();
		while( tokenizer.hasMoreTokens() ) {
			String token = tokenizer.nextToken().trim();
			if( token.equalsIgnoreCase("par") || token.equalsIgnoreCase("endpar") ||
					token.equalsIgnoreCase("seq") || token.equalsIgnoreCase("endseq") ) 
				continue;
			else
				servList.add(token);
		}
		return servList;
	}
	
}



