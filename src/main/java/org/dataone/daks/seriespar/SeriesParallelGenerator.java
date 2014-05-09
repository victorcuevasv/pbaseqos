
package org.dataone.daks.seriespar;

import java.util.Random;


public class SeriesParallelGenerator {

	
	private static int STMTIDX;
	private Random rand;
	
	
	public SeriesParallelGenerator() {
		this.rand = new Random();
	}
	
	
	public static void main(String args[]) {
		SeriesParallelGenerator generator = new SeriesParallelGenerator();
		System.out.println(generator.asm(2, 4, 0.6, 0.1));
	}
	
	
	public String asm(int minStatements, int maxStatements, double nonTermProb, double weaken) {
		STMTIDX = 1;
		int dice = randInt(1, 2);
		String retVal = null;
		//Top construct is either seq (1) or par (2)
		if( dice == 1 )
			retVal = seq(minStatements, maxStatements, nonTermProb-weaken, weaken);
		else if( dice == 2 )
			retVal = par(minStatements, maxStatements, nonTermProb-weaken, weaken);
		else
			System.out.println("Error random number outside range.");
		return retVal;
	}
	
	
	private String seq(int minStatements, int maxStatements, double nonTermProb, double weaken) {
		int dice = randInt(minStatements, maxStatements);
		StringBuilder builder = new StringBuilder();
		//Dice tells the number of statements to generate
		for(int i = 1; i <= dice; i++) {
			double randVal = Math.random();
			//Generate a nested par ... endpar
			if( randVal < nonTermProb )
				builder.append(par(minStatements, maxStatements, nonTermProb-weaken, weaken));
			//Generate a simple statement
			else {
				builder.append(" a" + STMTIDX + " ");
				STMTIDX++;
			}
		}
		return " seq " + builder.toString() + " endseq ";
	}
	
	
	private String par(int minStatements, int maxStatements, double nonTermProb, double weaken) {
		int dice = randInt(minStatements, maxStatements);
		StringBuilder builder = new StringBuilder();
		//Dice tells the number of statements to generate
		for(int i = 1; i <= dice; i++) {
			double randVal = Math.random();
			//Generate a nested par ... endpar
			if( randVal < nonTermProb )
				builder.append(seq(minStatements, maxStatements, nonTermProb-weaken, weaken));
			//Generate a simple statement
			else {
				builder.append(" a" + STMTIDX + " ");
				STMTIDX++;
			}
		}
		return " par " + builder.toString() + " endpar ";
	}
	
	
	private int randInt(int min, int max) {
	    int randomNum = this.rand.nextInt((max - min) + 1) + min;
	    return randomNum;
	}
	
	
}


