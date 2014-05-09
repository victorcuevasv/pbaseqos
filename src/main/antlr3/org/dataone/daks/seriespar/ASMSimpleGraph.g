/*
   This grammar is used to generate a directed graph representation
   of an ASM, based on an abtract syntax tree representation of the 
   ASM specification.
*/

tree grammar ASMSimpleGraph;


options {
	tokenVocab=ASMSimple;
	ASTLabelType=CommonTree;
}


@members {
	String startNode = null;
	String endNode = null;
        int parIndex = 1;
	ASTtoDigraph astToDigraph = new ASTtoDigraph();
}


@header {
	package org.dataone.daks.seriespar;
}



asm
	:	^(ASM ^(BLOCK{startNode = "start"; endNode = "end";} (r=rule{astToDigraph.linkNodes(startNode, $r.ruleWFC.start); astToDigraph.linkNodes($r.ruleWFC.end, endNode); })+)) ;
    

rule returns [WFComponent ruleWFC]
	:	^(id=ID{$ruleWFC = new WFComponent($id.text, $id.text);}) 
	|	^(PARBLOCK{$ruleWFC = new WFComponent(); String parOpenNode = "par" + parIndex; String parCloseNode = "endpar" + parIndex; parIndex++; $ruleWFC.start = parOpenNode; $ruleWFC.end = parCloseNode;} (r=rule{astToDigraph.linkNodes($ruleWFC.start, $r.ruleWFC.start); astToDigraph.linkNodes($r.ruleWFC.end, $ruleWFC.end);})+)
	|	^(SEQBLOCK{$ruleWFC = new WFComponent();} (r=rule{if($ruleWFC.start == null) $ruleWFC.start=$r.ruleWFC.start; else astToDigraph.linkNodes($ruleWFC.end, $r.ruleWFC.start); $ruleWFC.end=$r.ruleWFC.end;})+)
	;
	
        
	








