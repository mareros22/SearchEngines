/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class PostingsList {
    
    /** The postings list */
    private ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();


    /** Number of postings in this list. */
    public int size() {
    return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
    return list.get( i );
    }

    public void addEntry(PostingsEntry p){
        list.add(p);
    }

    public String toString(){
        String s = "";
        for(PostingsEntry e : list ){
            s = s + e.toString() + "|";
        }
        return s;
    }

    public PostingsEntry findByID(int DocID){
        for(PostingsEntry e : list){
            if(e.getId() == DocID){
                return e;
            }else if(e.getId() > DocID){
                return null;
            }
        }
        return null;
    }

    public void clear(){
        list.clear();
    }
    // 
    //  YOUR CODE HERE
    //
    public byte[] toBytes(){
        byte[] ret;
        int size_ret = 0;
        for(PostingsEntry p : list){
            size_ret += 4;
            size_ret += p.toBytes().length;
        }
        
        ret = new byte[size_ret];
        int currentIndex = 0;
        for(PostingsEntry pe : list){
            String size_str = "" + pe.toBytes().length;
            byte[] size_bytes = size_str.getBytes();
            for(int i = 0; i < 4; i++){
                ret[currentIndex + i] = size_bytes[i];
            }
            currentIndex += 4;
            byte[] post_bytes = pe.toBytes();
            for(int j = 0; j < post_bytes.length; j++){
                ret[currentIndex + j] = post_bytes[j];
            }
            currentIndex += post_bytes.length;
        }
        return ret;
    }

    public void setList(ArrayList<PostingsEntry> newList){
        this.list = newList;
    }

    public ArrayList<PostingsEntry> getList(){
        return list;
    }

    public void sortList(){
        Collections.sort(this.list);
    }

}

