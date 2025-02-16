/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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
        }else{ // must be ranked
            PostingsList aux1;
            PostingsList aux2;
            PostingsList ret = new PostingsList();
            PostingsEntry e;
            String word;
            double idf, tdf, dft, lend;
            int numdocs = index.docNames.keySet().size();
            double scores[] = new double[numdocs];
            double lengths[] = new double[numdocs];
            for(int docID : index.docLengths.keySet()){
                lengths[docID] = index.docLengths.get(docID);
            }
            for(Query.QueryTerm qt : query.queryterm){
                word = qt.term;
                aux1 = index.getPostings(word);
                for(PostingsEntry pe : aux1.getList()){
                    int id = pe.docID;
                    lend = index.docLengths.get(id);
                    tdf = pe.offsets.size();
                    dft = index.getPostings(word).size();
                    idf = Math.log(numdocs / dft);
                    scores[id] += tdf * idf / lend;
                }
            }
            System.out.println("-----Scores-----");
            System.out.println(Arrays.toString(scores));
            for(int i = 0; i < numdocs; i++){
                if(scores[i] > 0 && lengths[i] > 0){
                    e = new PostingsEntry(i, scores[i]/lengths[i]);
                    ret.addEntry(e);
                }

                
            }
            /*

            double[] qvector = generateQVector(query);
            String word = query.queryterm.get(0).term;
            PostingsList ret = index.getPostings(word);
            if(ret == null){
                System.out.println("First term doesn't exist");
                return null;
            }
            // System.out.println("Starting Doc vector");
            aux1 = new PostingsList();
            for(int l = 0; l < ret.size(); l++){
                pe = ret.get(l);
                pe.vector = updateDocVector(pe, pe, word,query.queryterm.size(), 0, ret.size());
                pe.score = cosScore(qvector, pe.getVector(), pe.getId());
                aux1.addEntry(pe);
            }
            ret = aux1;
            
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
                    int j1 = aux1.get(j).getId();
                    int k1 = aux2.get(k).getId();
                    if(j1 == k1){
                        pe = aux1.get(j);
                        pe.vector = updateDocVector(pe, aux2.get(k), word, query.queryterm.size(), i, aux2.size());
                        pe.score = cosScore(qvector, pe.getVector(), pe.getId());
                        ret.addEntry(pe);
                        j++;
                        k++;
                    }else if(j1 < k1){
                        pe = aux1.get(j);
                        pe.score = cosScore(qvector, pe.getVector(), pe.getId());
                        ret.addEntry(pe);
                        j++;
                    }else{
                        pe = aux2.get(k);
                        pe.vector = updateDocVector(pe, aux2.get(k), word, query.queryterm.size(), i, aux2.size());
                        pe.score = cosScore(qvector, pe.getVector(), pe.getId());
                        ret.addEntry(pe);
                        k++;
                    }
                }
            }
        */
            ret.sortList();
            return ret; 
        }


        // return null;
    }

    public double[] generateQVector(Query query){
        HashMap<String, Integer> terms_count = new HashMap<>();
        int lend;
        int numTerms = query.queryterm.size();
        String word;
        double[] qvector = new double[numTerms];
        int N = index.docNames.keySet().size();
        int i = 0;

        
        double idf, tdf;
        for(Query.QueryTerm qt : query.queryterm){
            word = qt.term;
            if(terms_count.containsKey(word)){
                terms_count.put(word, terms_count.get(word)+1);
            }else{
                terms_count.put(word, 1);
            }
        }
        for(Query.QueryTerm t: query.queryterm){
            word = t.term;
            lend = query.queryterm.size();
            tdf = terms_count.get(word);
            idf = Math.log(N / tdf);
            qvector[i] = Math.sqrt(tdf / lend);
            i++;
        }
        System.out.println("Query Vector " + Arrays.toString(qvector));
        return qvector;
    }

    public HashMap<String, Integer> getWTQ(Query query){
        HashMap<String, Integer> terms_count = new HashMap<>();
        String word;
        for(Query.QueryTerm qt : query.queryterm){
            word = qt.term;
            if(terms_count.containsKey(word)){
                terms_count.put(word, terms_count.get(word)+1);
            }else{
                terms_count.put(word, 1);
            }
        }
        return terms_count;
    }


    public double[] updateDocVector(PostingsEntry pe, PostingsEntry p2, String w,int numterms, int wordnum, int numdocs){
        double[] vector;
        int lend;
        double tdf, idf;
        double dft;
        int N = index.docNames.keySet().size();
        if(pe.getVector() == null || pe.getVector().length != numterms){
            vector = new double[numterms];
        }else{
            vector =  pe.getVector();
        }
        lend = index.docLengths.get(p2.docID);
        tdf = p2.offsets.size();
        dft = index.getPostings(w).size();
        idf = Math.log(N / dft);
        vector[wordnum] = tdf * idf / lend;
        return vector;
    }

    public double cosScore(double[] vec1, double[] vec2,int docID){
        double num=0, den, den1=0, den2=0;
        // dot product
        for(int i = 0; i < vec1.length; i++){
            num += vec1[i] * vec2[i];
            den1 += (vec1[i])*(vec1[i]);
        }
        den2 = index.docVectorLengths.get(docID);
        den = Math.sqrt(den1)*Math.sqrt(den2);
        if(den == 0){
            System.out.println("Den == 0 : " + docID);
            return 0;
        }
        System.out.println("----------------------------------------");
        System.out.println(Arrays.toString(vec2));
        System.out.println(den2 + " : " + (num / den));
        return num / den;
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