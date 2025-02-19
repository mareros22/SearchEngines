/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 *   Processes a directory structure and indexes all PDF and text files.
 */
public class Indexer {

    /** The index to be built up by this Indexer. */
    Index index;

    /** K-gram index to be built up by this Indexer */
    KGramIndex kgIndex;

    /** The next docID to be generated. */
    private int lastDocID = 0;

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file;


    /* ----------------------------------------------- */


    /** Constructor */
    public Indexer( Index index, KGramIndex kgIndex, String patterns_file ) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.patterns_file = patterns_file;
    }


    /** Generates a new document identifier as an integer. */
    private int generateDocID() {
        return lastDocID++;
    }



    /**
     *  Tokenizes and indexes the file @code{f}. If <code>f</code> is a directory,
     *  all its files and subdirectories are recursively processed.
     */
    public void processFiles( File f, boolean is_indexing, boolean calculate_lengths, File lengths) {
        // do not try to index fs that cannot be read

        if (is_indexing) {
            if ( f.canRead() ) {
                if ( f.isDirectory() ) {
                    String[] fs = f.list();
                    // an IO error could occur
                    if ( fs != null ) {
                        for ( int i=0; i<fs.length; i++ ) {
                            processFiles( new File( f, fs[i] ), is_indexing, calculate_lengths, lengths);
                        }
                    }
                } else {
                    // First register the document and get a docID
                    int docID = generateDocID();
                    HashMap<String, Integer> wordcounts = new HashMap<>();
                    if ( docID%1000 == 0 ) System.err.println( "Indexed " + docID + " files" );
                    try {
                        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
                        Tokenizer tok = new Tokenizer( reader, true, false, true, patterns_file );
                        int offset = 0;
                        while ( tok.hasMoreTokens() ) {
                            String token = tok.nextToken();
                            insertIntoIndex( docID, token, offset++ );
                            if(calculate_lengths){
                                if(wordcounts.containsKey(token)){
                                    wordcounts.put(token, wordcounts.get(token)+1);
                                }else{
                                    wordcounts.put(token, 1);
                                }
                            }
                            
                        }
                        
                        if(calculate_lengths){
                            index.doccounts.put(docID, wordcounts);
                        }
                        //synchronized(index.fileInfoLock){
                        index.docNames.put( docID, f.getPath() );
                        index.docLengths.put( docID, offset );
                        //}
                        reader.close();
                    } catch ( IOException e ) {
                        System.err.println( "Warning: IOException during indexing." );
                    }
                }
            }
        }
    }


    /* ----------------------------------------------- */


    /**
     *  Indexes one token.
     */
    public void insertIntoIndex( int docID, String token, int offset ) {
        index.insert( token, docID, offset );
        if (kgIndex != null)
            kgIndex.insert(token);
    }

    public void readEuclideanLengths(){
        String line;
        BufferedReader in;
        try {
            in = new BufferedReader( new FileReader("index/lengths.txt"));
            while((line = in.readLine()) != null){
                int ix = line.indexOf(";");
                Double len = Double.valueOf(line.substring(ix+1));
                int id = Integer.valueOf(line.substring(0, ix));
                index.eLengths.put( id, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

