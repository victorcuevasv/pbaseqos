
package org.dataone.daks.seriespar;

import org.dataone.daks.pbase.treecover.Digraph;



public class ASTtoDigraph {
	
	
	private Digraph graph;
	
	
	
	public ASTtoDigraph() {
		this.graph = new Digraph();
	}
	
	protected Digraph getDigraph() {
		return graph;
	}
	
	
	protected void linkNodes(String node1, String node2) {
		graph.addEdge(node1, node2);
	}
	
	
}


