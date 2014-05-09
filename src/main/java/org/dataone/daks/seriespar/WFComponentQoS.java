package org.dataone.daks.seriespar;


public class WFComponentQoS {

	
	protected String start;
	protected String end;
	
	protected QoSMetrics qosMetrics;
	
	
	public WFComponentQoS(String start, String end) {
		this.start = start;
		this.end = end;
	}

	
	public WFComponentQoS(String start, String end, QoSMetrics metrics) {
		this.start = start;
		this.end = end;
		this.qosMetrics = metrics;
	}
	
	
	public WFComponentQoS() {
		
	}
	
	
	public QoSMetrics getQoSMetrics() {
		return this.qosMetrics;
	}
	
	
}


