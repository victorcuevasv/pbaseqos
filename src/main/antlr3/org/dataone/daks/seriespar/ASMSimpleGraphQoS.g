/*
   This grammar is used to generate a directed graph representation
   of an ASM, based on an abtract syntax tree representation of the 
   ASM specification. In addition, it calculates aggregate QoS
   metrics for the ASM-specified workflow.
*/

tree grammar ASMSimpleGraphQoS;


options {
	tokenVocab=ASMSimple;
	ASTLabelType=CommonTree;
}


@members {
	String startNode = null;
	String endNode = null;
        int parIndex = 1;
	ASTtoDigraph astToDigraph = new ASTtoDigraph();
        RandomServiceCatalog catalog;
        WFComponentQoS topComponent;
}


@header {
	package org.dataone.daks.seriespar;
        
}



asm
	:	^(ASM { ; } 
                ^(BLOCK { startNode = "start"; endNode = "end"; } 
                (r=rule{ astToDigraph.linkNodes(startNode, $r.ruleWFCQoS.start);
                         astToDigraph.linkNodes($r.ruleWFCQoS.end, endNode);
                         topComponent = new WFComponentQoS(startNode, endNode, $r.ruleWFCQoS.qosMetrics.copy()); })+)) ;
    

rule returns [WFComponentQoS ruleWFCQoS]
	:	^(id=ID{ QoSMetrics metrics = catalog.getQoSMetrics($id.text).copy(); 
                         $ruleWFCQoS = new WFComponentQoS($id.text, $id.text, metrics); }) 
	|	^(PARBLOCK { $ruleWFCQoS = new WFComponentQoS();
                             String parOpenNode = "par" + parIndex;
                             String parCloseNode = "endpar" + parIndex;
                             parIndex++;
                             $ruleWFCQoS.start = parOpenNode;
                             $ruleWFCQoS.end = parCloseNode; }
                             (r=rule { 
                                astToDigraph.linkNodes($ruleWFCQoS.start, $r.ruleWFCQoS.start);
                                astToDigraph.linkNodes($r.ruleWFCQoS.end, $ruleWFCQoS.end);
                                if( $ruleWFCQoS.qosMetrics == null )
                                    $ruleWFCQoS.qosMetrics = $r.ruleWFCQoS.qosMetrics.copy();
                                else
                                    $ruleWFCQoS.qosMetrics.aggregateParallel($r.ruleWFCQoS.qosMetrics); } )+)     
	|	^(SEQBLOCK { $ruleWFCQoS = new WFComponentQoS(); }
                            (r=rule { 
                                if( $ruleWFCQoS.start == null ) {
                                    $ruleWFCQoS.start=$r.ruleWFCQoS.start;
                                    $ruleWFCQoS.qosMetrics = $r.ruleWFCQoS.qosMetrics.copy();
                                }
                                else {
                                    astToDigraph.linkNodes($ruleWFCQoS.end, $r.ruleWFCQoS.start);
                                    $ruleWFCQoS.qosMetrics.aggregateSequential($r.ruleWFCQoS.qosMetrics);
                                }
                                $ruleWFCQoS.end=$r.ruleWFCQoS.end; } )+)
	;
	
        
	








