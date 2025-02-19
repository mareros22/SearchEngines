/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;
import java.io.BufferedReader;
import java.io.FileReader;
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
            if(rankingType == RankingType.TF_IDF){
                PostingsList aux1;
                PostingsList aux2;
                PostingsList ret = new PostingsList();
                PostingsEntry e;
                String word;
                double idf, tf, dft, lend, tf_idf;
                int numdocs = index.docNames.keySet().size();
                double scores[] = new double[numdocs];
                double length[] = new double[numdocs];
                boolean multiterm = query.queryterm.size() > 1;
                for(Query.QueryTerm qt : query.queryterm){
                    word = qt.term;
                    aux1 = index.getPostings(word);
                    for(PostingsEntry pe : aux1.getList()){
                        int id = pe.docID;
                        if(normType == NormalizationType.NUMBER_OF_WORDS){
                            lend = index.docLengths.get(id);
                        }else{
                            lend = index.eLengths.get(id);
                        }
                        
                        tf = pe.offsets.size();
                        dft = aux1.size();
                        idf = Math.log(numdocs / dft);
                        tf_idf = tf * idf;
                        if(!multiterm){
                            length[id] = 1;
                        }else{
                            length[id] = lend;
                        }
                        scores[id] += tf_idf;
                        
                    }
                }
                // System.out.println("-----Scores-----");
                for(int i = 0; i < numdocs; i++){
                    if(scores[i] > 0){
                        e = new PostingsEntry(i, scores[i]/length[i]);
                        ret.addEntry(e);
                        // System.out.println(index.docNames.get(i) + " : " + scores[i]);
                    }
    
                }
                ret.sortList();
                System.out.println("Document with ID 0:" + index.docNames.get(0));
                return ret; 
                
            }else if(rankingType == RankingType.PAGERANK){
                int numdocs = index.docNames.keySet().size();
                BufferedReader in;
                HashMap<String, Integer> davisIDHashMap = new HashMap<>();
                double[] davisscores = new double[2000000];
                double[] scores = new double[numdocs];
                String line;
                String title = "";
                try {
                    in = new BufferedReader( new FileReader("pagerank/davisTitles.txt"));
                    while((line = in.readLine()) != null){
                        int ix = line.indexOf(";");
                        title = "C:\\Users\\rmare\\OneDrive\\Desktop\\SE_Assignment1\\davisWiki\\" + line.substring(ix+1);
                        int num = Integer.valueOf(line.substring(0, ix));
                        davisIDHashMap.put( title, num);
                    }
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
                try {
                    in = new BufferedReader(new FileReader("pagerank/PageRank_Rankings.txt"));
                    while((line = in.readLine()) != null){
                        int index = line.indexOf(":");
                        double score = Double.valueOf(line.substring(index+1));
                        int num = Integer.valueOf(line.substring(0, index));
                        davisscores[num] = score;
                    }
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
                // System.out.println(title);
                // maps filled in, go through postings
                PostingsList aux1;
                PostingsList aux2;
                PostingsList ret = new PostingsList();
                PostingsEntry e;
                String word;
                for(Query.QueryTerm qt : query.queryterm){
                    word = qt.term;
                    aux1 = index.getPostings(word);
                    for(PostingsEntry pe : aux1.getList()){
                        int id = pe.docID;
                        String docName = index.docNames.get(id);
                        // System.out.println(docName);
                        int davisID = davisIDHashMap.get(docName);
                        if(davisID != 0){
                            scores[id] = davisscores[davisID];
                        }                        
                    }
                }

                for(int i = 0; i < numdocs; i++){
                    if(scores[i] > 0){
                        e = new PostingsEntry(i, scores[i]);
                        ret.addEntry(e);
                    }
    
                }
                ret.sortList();
                return ret; 
            }else{
                //combination
                PostingsList tf_idf = search(query, queryType, RankingType.TF_IDF, normType);
                PostingsList pr = search(query, queryType, RankingType.PAGERANK, normType);
                PostingsList ret = new PostingsList();
                PostingsEntry current;
                int numdocs = index.docNames.keySet().size();
                double scores[] = new double[numdocs];
                for(int i = 0; i < tf_idf.size(); i++){
                    current = tf_idf.get(i);
                    scores[current.docID] += current.score;
                }
                for(int j = 0; j < pr.size(); j++){
                    current = pr.get(j);
                    scores[current.docID] *= (1 + numdocs *current.score);
                }
                for(int k = 0; k < numdocs; k++){
                    if(scores[k] > 0){
                        current = new PostingsEntry(k, scores[k]);
                        ret.addEntry(current);
                    }
    
                }
                ret.sortList();
                return ret;
            } 
        }  
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
        double tdf, idf;
        double dft;
        int N = index.docNames.keySet().size();
        if(pe.getVector() == null || pe.getVector().length != numterms){
            vector = new double[numterms];
        }else{
            vector =  pe.getVector();
        }
        tdf = p2.offsets.size();
        dft = index.getPostings(w).size();
        idf = Math.log(N / dft);
        vector[wordnum] = tdf * idf;
        return vector;
    }

    public double cosScore(double[] vec1, double[] vec2,int docID,boolean doclen){
        double num=0, den, den1=0, den2=0;
        // dot product
        for(int i = 0; i < vec1.length; i++){
            num += vec1[i] * vec2[i];
            den1 += (vec1[i])*(vec1[i]);
        }
        if(doclen){
            den2 = index.docLengths.get(docID);
        }else{
            den2 = index.eLengths.get(docID);
        }
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