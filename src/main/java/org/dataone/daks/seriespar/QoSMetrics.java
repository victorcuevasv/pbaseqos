package org.dataone.daks.seriespar;


public class QoSMetrics {
	
	
	private double time;
	private double cost;
	private double reliability;
	
	
	public QoSMetrics(double time, double cost, double reliability) {
		this.time = time;
		this.cost = cost;
		this.reliability = reliability;
	}

	
	public void setTime(double time) {
		this.time = time;
	}
	
	
	public void setCost(double cost) {
		this.cost = cost;
	}
	
	
	public void setReliability(double reliability) {
		this.reliability = reliability;
	}
	
	
	public double getTime() {
		return this.time;
	}
	
	
	public double getCost() {
		return this.cost;
	}
	
	
	public double getReliability() {
		return this.reliability;
	}
	
	
	public QoSMetrics copy() {
		QoSMetrics metricsCopy = new QoSMetrics(this.time, this.cost, this.reliability);
		return metricsCopy;
	}
	
	
	public void aggregateParallel(QoSMetrics other) {
		if( other.time > this.time )
			this.time = other.time;
		this.cost = this.cost + other.cost;
		this.reliability = this.reliability * other.reliability;
	}
	
	
	public void aggregateSequential(QoSMetrics other) {
		this.time = this.time + other.time;
		this.cost = this.cost + other.cost;
		this.reliability = this.reliability * other.reliability;
	}
	
	
	public String toString() {
		return String.format("Time: %.2f Cost:%.2f Reliability:%.2f", this.time, this.cost, this.reliability);   
	}
	
	
}




