/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 *  This is the main class for the search engine.
 */
public class Engine {

    /** The inverted index. */
    Index index = new HashedIndex();
    // Assignment 1.7: Comment the line above and uncomment the next line
    // Index index = new PersistentHashedIndex();
    // Index index = new PersistentScalableHashedIndex();

    /** The indexer creating the search index. */
    Indexer indexer;

    /** The searcher used to search the index. */
    Searcher searcher;

    /** K-gram index */
    KGramIndex kgIndex = null;
    // Assignment 3: Comment the line above and uncomment the next line
    // KgramIndex kgIndex = new KGramIndex(2);

    /** Spell checker */
    SpellChecker speller;
    // Assignment 3: Comment the line above and uncomment the next line
    // SpellChecker = new SpellChecker( index, kgIndex );
    
    /** The engine GUI. */
    SearchGUI gui;

    /** Directories that should be indexed. */
    ArrayList<String> dirNames = new ArrayList<String>();

    /** Lock to prevent simultaneous access to the index. */
    Object indexLock = new Object();

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file = null;

    /** The file containing the logo. */
    String pic_file = "";

    /** The file containing the pageranks. */
    String rank_file = "";

    /** For persistent indexes, we might not need to do any indexing. */
    boolean is_indexing = true;

    String length_file = "index/lengths.txt";

    boolean calculate_lengths;
    
    File lengths;



    /* ----------------------------------------------- */


    /**  
     *   Constructor. 
     *   Indexes all chosen directories and files
     */
    public Engine( String[] args ) {
        decodeArgs( args );
        indexer = new Indexer( index, kgIndex, patterns_file );
        searcher = new Searcher( index, kgIndex );
        gui = new SearchGUI( this );
        gui.init();
        /* 
         *   Calls the indexer to index the chosen directory structure.
         *   Access to the index is synchronized since we don't want to 
         *   search at the same time we're indexing new files (this might 
         *   corrupt the index).
         */
        if (is_indexing) {
            synchronized ( indexLock ) {
                gui.displayInfoText( "Indexing, please wait..." );
                lengths = new File(length_file);
                calculate_lengths = !lengths.isFile();
                if(calculate_lengths){
                    System.out.println("Calculating Euclidean lengths");
                }
                long startTime = System.currentTimeMillis();
                for ( int i=0; i<dirNames.size(); i++ ) {
                    File dokDir = new File( dirNames.get( i ));
                    indexer.processFiles( dokDir, is_indexing, calculate_lengths, lengths);
                }
                if(calculate_lengths){
                    calcLength();
                }else{
                    indexer.readEuclideanLengths();
                }
                System.out.println("Test: length of doc 0: " + index.eLengths.get(0));
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                gui.displayInfoText( String.format( "Indexing done in %.1f seconds.", elapsedTime/1000.0 ));
                index.cleanup();
            }
        } else {
            gui.displayInfoText( "Index is loaded from disk" );
        }
    }


    /* ----------------------------------------------- */

    /**
     *   Decodes the command line arguments.
     */
    private void decodeArgs( String[] args ) {
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-d".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    dirNames.add( args[i++] );
                }
            } else if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    patterns_file = args[i++];
                }
            } else if ( "-l".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    pic_file = args[i++];
                }
            } else if ( "-r".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    rank_file = args[i++];
                }
            } else if ( "-ni".equals( args[i] )) {
                i++;
                is_indexing = false;
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }
        
    }



    public void calcLength(){
        int numdocs = index.docNames.keySet().size();
        System.out.println("Calclength " + numdocs);
        EuclideanThread[] manager = new EuclideanThread[numdocs];
        double doclength;
        for(int i = 0; i < numdocs; i++){
            EuclideanThread er = new EuclideanThread(i);
            manager[i] = er;
            er.run();
        }
        for(int j = 0; j < numdocs; j++){
            try {
                manager[j].join();
                doclength = manager[j].getLength();
                index.eLengths.put(j, doclength);   
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(lengths, true));
            for(int docID : index.eLengths.keySet()){
                doclength = index.eLengths.get(docID);
                writer.write(docID + ";" + doclength + "\n");
            }
            writer.close();
           
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class EuclideanThread extends Thread {
        int docID;
        HashMap<String, Integer> wordcounts;
        double doclength;
        public EuclideanThread(int id){
            docID = id;
            wordcounts = index.doccounts.get(docID);
            if(wordcounts == null){
                System.out.println("DOCCOUNTS IS NULL: " + docID);
            }
            doclength = 0;
        }
        public void run() {
            double tf, idf, df, N, tf_idf;
            N = (double)index.docNames.keySet().size();
            for(String word : wordcounts.keySet()){
                tf = (double)wordcounts.get(word);
                df = index.getPostings(word).size();
                idf = Math.log(N/df);
                tf_idf = tf * idf;
                doclength += tf_idf * tf_idf;
            }
            doclength = Math.sqrt(doclength);
            // index.eLengths.put(docID, doclength);
        }

        public double getLength(){
            return doclength;
        }
    }
    /* ----------------------------------------------- */


    public static void main( String[] args ) {
        Engine e = new Engine( args );
    }

}

