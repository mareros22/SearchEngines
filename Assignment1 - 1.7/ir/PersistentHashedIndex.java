/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The data file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;
    // public static final long TABLESIZE = 3500000L;

    /** max length of string in Entry */
    public static final int MAX_TERMSIZE = 64;
    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();

    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public class Entry {
        //
        //  YOUR CODE HERE
        //
        public String token;
        public long entryPtr;
        public int dataSize;

        public Entry(String t){
            this(t, free, 0);
            
        }

        public Entry(String t, long ptr){
            this.token = t;
            this.entryPtr = ptr;
        }
        public Entry(String t, long ptr, int size){
            this.token = t;
            this.entryPtr = ptr;
            this.dataSize = size;
            
        }

        public void setToken(String t){
            this.token = t;
        }

        public void setPtr(long p){
            this.entryPtr = p;
        }

        public String toString(){
            String s = token.strip() + " " + entryPtr + " " + dataSize + " ";
            int size = s.length();
            for(int i = size; i < MAX_TERMSIZE + 16; i++){
                s = s + " ";
            }
            return s;
        }

    }

    public long hash(String t){
        return Math.abs(t.hashCode()) % TABLESIZE;
    }


    // ==================================================================

    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {


        try {
            this.dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            this.dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            this.readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData( String dataString, long ptr ) {
        try {
            dataFile.seek( ptr ); 
            byte[] data = dataString.getBytes();
            dataFile.write( data );
            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */ 
    String readData( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry( Entry entry, long ptr ) {
        //
        //  YOUR CODE HERE
        //
        try {
            dictionaryFile.seek(ptr*80);
            byte[] data = entry.toString().getBytes();
            dictionaryFile.write( data );
        } catch (IOException e) {
            e.printStackTrace();
        }
        

    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry( long ptr ) {   
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE 
        //
        try {
            dictionaryFile.seek( ptr*80 );
            byte[] data = new byte[80];
            try {
                dictionaryFile.readFully( data );
            } catch (EOFException e) {
                return null;
            }
            
            String entryString = new String(data);
            if(entryString.isBlank()){
                return null;
            }
            int endOfTerm = entryString.indexOf(" ");
            if(endOfTerm <= 0){
                return null;
            }
            String eTerm = entryString.substring(0, endOfTerm);
            if(eTerm.isBlank()){
                return null;
            }
            entryString = entryString.substring(endOfTerm + 1);
            // System.out.println(entryString);
            endOfTerm = entryString.indexOf(" ");
            if(endOfTerm <= 0){
                return null;
            }
            long ePtr = Long.parseLong(entryString.substring(0, endOfTerm));
            entryString = entryString.substring(endOfTerm + 1);
            endOfTerm = entryString.indexOf(" ");
            // if(endOfTerm <= 0){
            //     return null;
            // }
            int eSize = Integer.parseInt(entryString.substring(0, endOfTerm));
            // if(eSize < 0){
            //     return null;
            // }
            return new Entry(eTerm, ePtr, eSize);

        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }



    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        //System.out.println("persistent hashed index writedocinfp");
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for ( Map.Entry<Integer,String> entry : docNames.entrySet() ) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write( docInfoEntry.getBytes() );
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try ( BufferedReader br = new BufferedReader(freader) ) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                this.docNames.put( new Integer(data[0]), data[1] );
                this.docLengths.put( new Integer(data[0]), new Integer(data[2]) );
            }
        }
        freader.close();
    }


    /**
     *  Write the index to files.
     */
    public void writeIndex() {
        int[] bitmap = new int[(int)TABLESIZE];
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();            
            // Write the dictionary and the postings list
            // 
            //  YOUR CODE HERE
            //
            
            for(String t : index.keySet()){
                if(t.length() > MAX_TERMSIZE){
                    continue;
                }
                long i = hash(t);
                while(bitmap[(int)i] == 1){
                    collisions++;
                    i = (i + 1) % TABLESIZE;
                    if(i == hash(t)){
                        throw new ArrayIndexOutOfBoundsException("dictionary full");
                    }
                }
                bitmap[(int)i] = 1;
                
                PostingsList p = index.get(t);
                

                String data = p.toString();
                int len = data.length();
                Entry dictEntry = new Entry(t);
                dictEntry.dataSize = len;
                writeData(data, dictEntry.entryPtr);
                writeEntry(dictEntry, i);
                free += len;
                

            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }


    // ==================================================================


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        long i = hash(token);
        Entry entryToCheck = readEntry(i);
        while(true){
            if(entryToCheck == null){
                System.out.println("No entry at hash(token)");
                return null;
            }
            if(entryToCheck.token.equals(token)){
                
                break;
            }
            i = (i + 1) % TABLESIZE;
            if(i == hash(token)){
                System.out.println("Entire index checked, token not present");
                return null;
            }
            entryToCheck = readEntry(i);
        }
        String postString = readData(entryToCheck.entryPtr, entryToCheck.dataSize);
        return listFromString(postString);

        
    }


    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        PostingsList l;
        PostingsEntry p;
        //
        // YOUR CODE HERE
        l = index.get(token);
        if(l != null){ // token has been inserted to index already
            p = l.get(l.size() - 1); // if there is an entry for this document it will be the most recent
            if(p.docID != docID){
                p = new PostingsEntry(docID);
            }else{
                p.addToEntry(offset);
                return; // document already indexed to this term
            }
        } else {
            l = new PostingsList();
            p = new PostingsEntry(docID);
        }
        p.addToEntry(offset);
        l.addEntry(p);
        index.put(token, l);
    }


    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex();
        System.err.println( "done!" );
    }




    public PostingsList listFromString(String s){
        // System.out.println("PostingsList: " + s);
        PostingsList ret = new PostingsList();
        PostingsEntry currentEntry;
        String currentList = s.strip();
        int start = 0;
        int endOfEntry = currentList.indexOf("|", start);
        // String firstEntry;
        while(endOfEntry > 0){
            // System.out.println("New Entry");
            // firstEntry = currentList.substring(0, endOfEntry);
            currentEntry = entryFromString(currentList.substring(start, endOfEntry));
            if(currentEntry == null){
                break;
            }
            //System.out.println(currentEntry.docID + " " + docNames.get(currentEntry.docID));
            ret.addEntry(currentEntry);
            start = endOfEntry + 1;
            // currentList = currentList.substring(endOfEntry + 1);
            endOfEntry = currentList.indexOf("|", start);
        }
        return ret;
    }

    public PostingsEntry entryFromString(String s){
        // System.out.println("PostingsEntry: " + s);
        String currentList = s;
        int start = 0;
        int endOfTerm = currentList.indexOf(" ", start);
        if(endOfTerm <= 0){
            return null;
        }
        int id = Integer.parseInt(currentList.substring(0, endOfTerm).strip());
        start = endOfTerm + 1;
        // currentList = currentList.substring(endOfTerm + 1);
        PostingsEntry ret = new PostingsEntry(id);
        endOfTerm = currentList.indexOf(" ", start);
        while(endOfTerm >= 0){
            ret.addToEntry(Integer.parseInt(currentList.substring(start, endOfTerm)));
            // currentList = currentList.substring(endOfTerm + 1);
            start = endOfTerm + 1;
            endOfTerm = currentList.indexOf(" ", start);
        }
        return ret;

    }
}
