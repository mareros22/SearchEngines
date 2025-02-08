/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;
import java.util.ArrayList;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType, NormalizationType normType ) { 
        //
        //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
        if(queryType == QueryType.INTERSECTION_QUERY){
            String word = query.queryterm.get(0).term;
            PostingsList ret = index.getPostings(word);
            if(ret == null){
                System.out.println("First term doesn't exist");
                return null;
            }
            PostingsList aux1;
            PostingsList aux2;
            for(int i = 1; i < query.queryterm.size(); i++){
                word = query.queryterm.get(i).term;
                aux1 = ret;
                ret = new PostingsList();
                aux2 = index.getPostings(word);
                if(aux2 == null){
                    System.out.println((i+1) + " term doesn't exist");
                    return null;
                }
                int j = 0;
                int k = 0;
                while(j < aux1.size() && k < aux2.size()){
                    int j1 = aux1.get(j).docID;
                    int k1 = aux2.get(k).docID;
                    if(j1 == k1){
                        ret.addEntry(aux1.get(j));
                        j++;
                        k++;
                    }else if(j1 < k1){
                        j++;
                    }else{
                        k++;
                    }
                }
            }
            return ret; 
        }else if(queryType == QueryType.PHRASE_QUERY){
            String word = query.queryterm.get(0).term;
            PostingsList ret = index.getPostings(word);
            if(ret == null){
                return null;
            }
            PostingsList aux1;
            PostingsList aux2;
            for(int i = 1; i < query.queryterm.size(); i++){
                word = query.queryterm.get(i).term;
                aux1 = ret;
                ret = new PostingsList();
                aux2 = index.getPostings(word);
                if(aux2 == null){
                    return null;
                }
                int j = 0;
                int k = 0;
                while(j < aux1.size() && k < aux2.size()){
                    PostingsEntry jdoc = aux1.get(j);
                    PostingsEntry kdoc = aux2.get(k);
                    int j1 = jdoc.docID;
                    int k1 = kdoc.docID;
                    if(j1 == k1){
                        ArrayList<Integer> j_offsets = jdoc.offsets;
                        ArrayList<Integer> k_offsets = kdoc.offsets;
                        int l = 0;
                        int m = 0;
                        PostingsEntry cumulative = new PostingsEntry(k1);
                        while(l < j_offsets.size() && m < k_offsets.size()){
                            int l1 = j_offsets.get(l);
                            int m1 = k_offsets.get(m);
                            if(l1 == m1 - 1){
                                cumulative.addToEntry(m1);
                                l++;
                                m++;
                            } else if(l1 < m1-1){
                                l++;
                            }else{
                                m++;
                            }
                        }
                        if(!cumulative.offsets.isEmpty()){
                            ret.addEntry(cumulative);
                        }
                        j++;
                        k++;
                    }else if(j1 < k1){
                        j++;
                    }else{
                        k++;
                    }
                }
            }
            return ret; 
        }

        return null;
    }


    public PostingsList testSearch(Query query, QueryType queryType, RankingType rankingType, NormalizationType normType){
        String word = query.queryterm.get(0).term;
        PostingsList ret = index.getPostings(word);
        PostingsList aux1;
        PostingsList aux2;
        int  size = query.queryterm.size();
        for(int i = 1; i < query.queryterm.size(); i++){
            word = query.queryterm.get(i).term;
            aux1 = ret;
            ret = new PostingsList();
            aux2 = index.getPostings(word);
            int j = 0;
            int k = 0;
            while(j < aux1.size() && k < aux2.size()){
                int j1 = aux1.get(j).docID;
                int k1 = aux2.get(k).docID;
                if(j1 == k1){
                    ret.addEntry(aux1.get(j));
                    j++;
                    k++;
                }else if(j1 < k1){
                    j++;
                }else{
                    k++;
                }
            }
        }
        return ret; 
    }
}