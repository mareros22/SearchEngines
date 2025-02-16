/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;
    public ArrayList<Integer> offsets;
    public double[] vector;

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }

    public double[] getVector(){
        return vector;
    }
    
    //
    // YOUR CODE HERE
    //
    public PostingsEntry(int id, double sc){
        docID = id;
        score = sc;
        offsets = new ArrayList<>();
    }

    public PostingsEntry(int id){
        this(id, 0);
    }

    public PostingsEntry(byte[] bytes){
        score = 0;
        offsets = new ArrayList<>();
        byte[] tempBytes = new byte[4];
        int currentI = 0;
        int o;
        for(int i = 0; i < 4; i++){
            tempBytes[i] = bytes[i];
        }
        currentI += 4;
        docID = new BigInteger(tempBytes).intValue();
        while(currentI < bytes.length){
            for(int j = 0; j < 4; j++){
                tempBytes[currentI + j] = bytes[j];
            }
            currentI += 4;
            o = new BigInteger(tempBytes).intValue();
            offsets.add(o);
        }
    }

    public void addToEntry(int offset){
        offsets.add(offset);
    }

    public String toString(){
        String s = docID + " ";
        for(int i : offsets){
            s = s + i + " ";
        }
        return s;
    }

    public int getId(){
        return docID;
    }

    public byte[] toBytes(){
        byte[] ret;
        int size = 4+4*offsets.size();
        ret = new byte[size];
        String size_str = "" + size;
        byte[] size_b = size_str.getBytes();
        for(int i = 0; i < 4; i++){
            ret[i] = size_b[i];
        }
        for(int j = 0; j < offsets.size(); j++){
            String offset_str = "" + offsets.get(j);
            byte[] offset_b = offset_str.getBytes();
            for(int k = 0; k < 4; k++){
                ret[4+4*j+k] = offset_b[k];
            }
        } 
        return ret;
    }

}

