To execute the LDBDAOTest application use the command

mvn exec:java -Dexec.mainClass="org.dataone.daks.pbaserdf.dao.LDBDAOTest" -Dexec.args="provone"	  



mvn exec:java -Dexec.mainClass="org.dataone.daks.seriespar.DatasetGenerator" -Dexec.args="wfs.txt wfs services50.json"


mvn exec:java -Dexec.mainClass="org.dataone.daks.pbaserdf.dao.LDBDAOTest" -Dexec.args="seqpargraphs"


<json files folder> <wf ids file> <n traces file> <services catalog file>


mvn exec:java -Dexec.mainClass="org.dataone.daks.seriespar.DigraphJSONtoRDFQoS" -Dexec.args="wfs2 wfs.txt numtraces.txt services50.json"     






