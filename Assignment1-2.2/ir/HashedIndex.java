/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  


package ir;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;


/**
 *   Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /** The index as a hashtable. */
    private HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();
    


    /**
     *  Inserts this token in the hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        PostingsList l;
        PostingsEntry p;
        //
        // YOUR CODE HERE
        l = index.get(token);
        if(l != null){ // token has been inserted to index already
            p = l.get(l.size() - 1); // if there is an entry for this document it will be the most recent
            if(docVectorLengths.get(docID) == null){
                docVectorLengths.put(docID, (double)0);
            }
            double currentLength = docVectorLengths.get(docID);
            if(p.docID != docID){
                p = new PostingsEntry(docID);
                docVectorLengths.put(docID, currentLength + (2*l.size()) + 1);
            }else{
                // System.out.println("duplicate docID " + docID);
                docVectorLengths.put(docID, currentLength + (2*l.size()) + 1);
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
     *  Returns the postings for a specific term, or null
     *  if the term is not in tashe index.
     */
    public PostingsList getPostings( String token ) {
        //
        // REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        return index.get(token);
    }

    public int numTerms(){
        return index.keySet().size();
    }

    
    public Set<String> keySet(){
        return index.keySet();
    }


    /**
     *  No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }
}
