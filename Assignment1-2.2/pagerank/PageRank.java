import java.util.*;
import java.io.*;
import java.lang.reflect.Array;
import org.w3c.dom.stylesheets.LinkStyle;

public class PageRank {

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Mapping from document names to document numbers.
     */
    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**  
     *   A memory-efficient representation of the transition matrix.
     *   The outlinks are represented as a HashMap, whose keys are 
     *   the numbers of the documents linked from.<p>
     *
     *   The value corresponding to key i is a HashMap whose keys are 
     *   all the numbers of documents j that i links to.<p>
     *
     *   If there are no outlinks from i, then the value corresponding 
     *   key i is null.
     */
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /**
     *   The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /**
     *   The probability that the surfer will be bored, stop
     *   following links, and take a random jump somewhere.
     */
    final static double BORED = 0.15;

    /**
     *   Convergence criterion: Transition probabilities do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.0001;

       
    /* --------------------------------------------- */


    public PageRank( String filename ) {
			int noOfDocs = readDocs( filename );
			iterate( noOfDocs, 1000 );
    }


    /* --------------------------------------------- */


    /**
     *   Reads the documents and fills the data structures. 
     *
     *   @return the number of documents read.
     */
    int readDocs( String filename ) {
	int fileIndex = 0;
	try {
	    System.err.print( "Reading file... " );
	    BufferedReader in = new BufferedReader( new FileReader( filename ));
	    String line;
	    while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
		int index = line.indexOf( ";" );
		String title = line.substring( 0, index );
		Integer fromdoc = docNumber.get( title );
		//  Have we seen this document before?
		if ( fromdoc == null ) {	
		    // This is a previously unseen doc, so add it to the table.
		    fromdoc = fileIndex++;
		    docNumber.put( title, fromdoc );
		    docName[fromdoc] = title;
		}
		// Check all outlinks.
		StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
		while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
		    String otherTitle = tok.nextToken();
		    Integer otherDoc = docNumber.get( otherTitle );
		    if ( otherDoc == null ) {
			// This is a previousy unseen doc, so add it to the table.
			otherDoc = fileIndex++;
			docNumber.put( otherTitle, otherDoc );
			docName[otherDoc] = otherTitle;
		    }
		    // Set the probability to 0 for now, to indicate that there is
		    // a link from fromdoc to otherDoc.
		    if ( link.get(fromdoc) == null ) {
			link.put(fromdoc, new HashMap<Integer,Boolean>());
		    }
		    if ( link.get(fromdoc).get(otherDoc) == null ) {
			link.get(fromdoc).put( otherDoc, true );
			out[fromdoc]++;
		    }
		}
	    }
	    if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
		System.err.print( "stopped reading since documents table is full. " );
	    }
	    else {
		System.err.print( "done. " );
	    }
	}
	catch ( FileNotFoundException e ) {
	    System.err.println( "File " + filename + " not found!" );
	}
	catch ( IOException e ) {
	    System.err.println( "Error reading file " + filename );
	}
	System.err.println( "Read " + fileIndex + " number of documents" );
	return fileIndex;
    }


    /* --------------------------------------------- */


    /*
     *   Chooses a probability vector a, and repeatedly computes
     *   aP, aP^2, aP^3... until aP^i = aP^(i+1).
     */
    void iterate( int numberOfDocs, int maxIterations ) {
			double[] a1 = new double[numberOfDocs];
			double[] a2 = new double[numberOfDocs];
			int iterations = 0;
			HashMap<Integer, Boolean> linkset;
			Arrays.fill(a2, 1/(double)numberOfDocs);
			System.out.println(numberOfDocs);
			System.out.println(1/(double)numberOfDocs);
			// System.out.println(Arrays.toString(a2));
			while(!Arrays.equals(a1, a2) && iterations < maxIterations){
				// System.out.println("Iterate!");
				a1 = a2;
				a2 = new double[numberOfDocs];
				Arrays.fill(a2, BORED/(double)numberOfDocs);
				for(int i = 0; i < numberOfDocs; i++){
					double factor = a1[i];
					if(out[i] == 0){
						for(int j = 0; j < numberOfDocs; j++){
							a2[j] += factor * (1-BORED)/((double)numberOfDocs);
						}
					}else{
						linkset = link.get(i);
						for(int j : linkset.keySet()){
							a2[j] += factor * (1-BORED)/((double)out[i]);
							if(out[i] != linkset.keySet().size()){
								System.err.println("out and map");
							}
						}
					}
					
				}
				iterations++;
			}

			File f = new File("PageRank_Rankings.txt");
			try {
				BufferedWriter fw = new BufferedWriter(new FileWriter(f));	
				for(int k = 0; k < numberOfDocs; k++){
					String rankDesc;
					rankDesc = "" + docName[k] + ":" + String.format("%,.8f",a2[k]) + "\n";
					fw.write(rankDesc);
				}
				fw.close();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

			// verify results
			double test = 0;
			for(int k = 0; k < numberOfDocs; k++){
				test += a2[k];
			}
			System.out.println("Test: " + test);
			//print results
			int topID;
			double topRank;
			for(int i = 0; i < 30; i++){
				topID = 0;
				topRank = 0;
				for(int j = 0; j < numberOfDocs; j++){
					if(a2[j] > topRank){
						topRank = a2[j];
						topID = j;
					}
				}
				System.out.println(docName[topID] + " " + topRank);
				a2[topID] = 0;
			}
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
	if ( args.length != 1 ) {
	    System.err.println( "Please give the name of the link file" );
	}
	else {
	    new PageRank( args[0] );
	}
    }
}