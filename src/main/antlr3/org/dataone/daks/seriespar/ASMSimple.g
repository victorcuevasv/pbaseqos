/*
   This grammar is used to generate a lexer and a parser to generate an
   abstract syntax tree of an ASM represented as a series-parallel graph.
   The tree is to be further processed by a walker.
*/

grammar ASMSimple;

//The output is an abstract syntax tree
options {
	output=AST;
}

//Tokens used to give structure to the AST
tokens {
	ASM; BLOCK; SEQ='seq'; ENDSEQ='endseq'; SEQBLOCK; PAR='par'; 
        ENDPAR='endpar'; PARBLOCK;
}

// applies only to the parser:
@header {package org.dataone.daks.seriespar;}

// applies only to the lexer:
@lexer::header {package org.dataone.daks.seriespar;}


asm
	:	rule+ EOF 
		-> ^(ASM ^(BLOCK rule+)) ;
	

rule
	:	ID				-> ^(ID)
	|	PAR rule+ ENDPAR		-> ^(PARBLOCK rule+)
	|	SEQ rule+ ENDSEQ		-> ^(SEQBLOCK rule+)
	;
	
	
// L e x i c a l  R u l e s

fragment
LETTER 	:	               'a'..'z' |'A'..'Z' ;

fragment
DIGIT 	:	               '0'..'9' ;

ID  :   LETTER (LETTER | DIGIT | '_')*  ;

WS  :   ( ' ' | '\t' | '\r' | '\n' )+ { $channel = HIDDEN; } ;   

SL_COMMENT
    :   '#' ~('\r'|'\n')* {$channel=HIDDEN;} ;








