package ir;


import java.io.*;
import java.util.*;
import java.nio.file.*;

public class PersistentScalableHashedIndex extends PersistentHashedIndex{
  Object fileInfoLock = new Object();
  Object queuesLock = new Object();
  Object recordCountLock = new Object();

  /** Mapping from document identifiers to document names. */
  //public ConcurrentHashMap<Integer,String> concurrentDocNames = new ConcurrentHashMap<Integer,String>();
    
  /** Mapping from document identifier to document length. */
  //public ConcurrentHashMap<Integer,Integer> concurrentDocLengths = new ConcurrentHashMap<Integer,Integer>();


  public static int indexCounter = 0;
  public static int recordCounter = 0;
  public static Queue<RandomAccessFile> tempDictFiles;
  public static Queue<RandomAccessFile> tempDataFiles;
  public static Queue<int[]> tempDictBitmaps;
  public static Queue<Integer> docNums;
  public static int currentDoc1;
  public static int currentDoc2;
  public static RandomAccessFile masterData;
  public static RandomAccessFile masterDict;
  public static int[] currentBitmap;

  public static ArrayList<mergeThread> threadManager;

  public PersistentScalableHashedIndex(){
    super();
    try {
      synchronized (fileInfoLock) {
        this.readDocInfo();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    threadManager = new ArrayList<>();
    tempDictFiles = new LinkedList<>();
    tempDataFiles = new LinkedList<>();
    tempDictBitmaps = new LinkedList<>();
    docNums = new LinkedList<>();

    masterData = dataFile;
    masterDict = dictionaryFile;
    currentBitmap = new int[(int)TABLESIZE];
  }
  
  public void reset(){
    this.free = 0L;
    indexCounter = 0;
    this.index.clear();
    
    docNames.clear();
    docLengths.clear();
    try {
      synchronized (recordCountLock) {
        recordCounter++;
        dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + recordCounter, "rw" );
        dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + recordCounter, "rw" );
        currentBitmap = new int[(int)TABLESIZE];  
        currentDoc1 = recordCounter;
      }
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  public void insert(String token, int docID, int offset){
    super.insert(token, docID, offset);
    indexCounter++;
    if(indexCounter >= TABLESIZE * 3){ // *4 for davis
      writeTempIndex();
      
    }
  }

  public void writeTempIndex(){
    this.writeIndex(dictionaryFile, dataFile, index);
    synchronized (queuesLock) {
      this.tempDictFiles.add(dictionaryFile);
      this.tempDataFiles.add(dataFile);
      this.docNums.add(currentDoc1);
      this.tempDictBitmaps.add(currentBitmap);
    }
    try {
      this.writeDocInfo();  
    } catch (IOException e) {
      e.printStackTrace();
    }
    reset();
    if(tempDictFiles.size() >= 2){
      
      mergeThread m = new mergeThread();
      threadManager.add(m);
      m.start();
      System.out.println("Thread added " + m);
    }
  }

  public void cleanup() {
    int lastdocnameval = 0;
    System.out.println("CLEANING UP NOW");
    writeTempIndex();
    for(mergeThread m : threadManager){
      try {
        m.join();
        lastdocnameval = m.getdocnameval();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      
    }
    while(tempDictFiles.size() >= 2){
      threadManager.clear();
      int s = tempDictFiles.size();
      for(int i = 0; i < s / 2; i++){
        mergeThread mt = new mergeThread();
        threadManager.add(mt);
        mt.start();
        System.out.println("Thread added " + mt);
      }
      for(mergeThread m : threadManager){
        try {
          m.join();
          lastdocnameval = m.getdocnameval();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      
    }
    try {
      synchronized(fileInfoLock){
        this.writeDocInfo(); 
      } 
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    
    RandomAccessFile dataContents = tempDataFiles.remove();
    RandomAccessFile dictContents = tempDictFiles.remove();

    try {
      dataContents.close();
      dictContents.close();  
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    
    File dictName = new File( INDEXDIR + "/" + DICTIONARY_FNAME);
    File dataName = new File( INDEXDIR + "/" + DATA_FNAME);
    File dict1;
    File data1;
    
    try {
      masterData.close();
      masterDict.close();   
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      dictName.delete();
      dataName.delete();  
    } catch (Exception e) {
      e.printStackTrace();
    }
    dict1 = new File( INDEXDIR + "/" + DICTIONARY_FNAME + lastdocnameval);
    data1 = new File( INDEXDIR + "/" + DATA_FNAME + lastdocnameval);
    try {
      Files.move(dict1.toPath(), dictName.toPath(), StandardCopyOption.REPLACE_EXISTING);
      Files.move(data1.toPath(), dataName.toPath(), StandardCopyOption.REPLACE_EXISTING); 
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      dict1 = new File( INDEXDIR + "/" + DICTIONARY_FNAME + lastdocnameval);
      data1 = new File( INDEXDIR + "/" + DATA_FNAME + lastdocnameval);
      dict1.delete();
      data1.delete();
      dictionaryFile = new RandomAccessFile(dictName, "rw" );
      dataFile = new RandomAccessFile(dataName, "rw" );
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    try {
      synchronized (fileInfoLock) {
        this.readDocInfo();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.err.println( "done!" );
  }

  public class mergeThread extends Thread{
    int docnameval;
    int[] bitmap = new int[(int)TABLESIZE];
    mergeThread mt;
    int freePtr = 0;
    public void run(){
      RandomAccessFile dict1, dict2, data1, data2;
      int d1, d2;
      synchronized (queuesLock) {
        dict1 = tempDictFiles.remove();
        dict2 = tempDictFiles.remove();
        data1 = tempDataFiles.remove();
        data2 = tempDataFiles.remove();
        d1 = docNums.remove();
        d2 = docNums.remove();
        // bitmap = tempDictBitmaps.remove();
      }
      synchronized(queuesLock){
        if(tempDataFiles.size() > 2){
          mt = new mergeThread();
          threadManager.add(mt);
          mt.run();
        }
      }

      docnameval = mergeFiles(dict1, dict2, data1, data2, d1, d2);
      if(mt != null){
        try {
          mt.join();
          docnameval = Math.max(docnameval, mt.getdocnameval());
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    public int getdocnameval(){
      return docnameval;
    }
    public int mergeFiles (RandomAccessFile dict1, RandomAccessFile dict2, RandomAccessFile data1, RandomAccessFile data2, int d1, int d2){
      Entry dicEntry;
      PostingsList p1, p2, retp;
      PostingsEntry rete;
      int id1, id2, i, j, s1, s2;
      RandomAccessFile retdict, retdata;
      String token, postString;
      HashSet<String> tokens = new HashSet<>();
      TreeSet<Integer> offsets = new TreeSet<>();
      long freeInFile = 0L;
      int docnum;
      try {
        synchronized (recordCountLock) {
          recordCounter++;
          retdict = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + recordCounter, "rw" );
          retdata = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + recordCounter, "rw" );
          docnum = recordCounter;
          // retbitmap = new int[(int)TABLESIZE];
        }
      } catch ( IOException e ) {
        e.printStackTrace();
        return -1;
      }
      // System.out.println("Going through first dictionary");
      for(int d = 0; d < TABLESIZE; d++){
        dicEntry = readEntry(dict1, d);
        if(dicEntry == null){
          continue;
        }
        retp = new PostingsList();
        token = dicEntry.token;
        tokens.add(token);
        postString = readData(data1, dicEntry.entryPtr, dicEntry.dataSize);
        if(postString == null){
          continue;
        }
        p1 = listFromString(postString);
        p2 = getPostingFromToken(dict2, data2, token);
        i = 0;
        j = 0;
        s1 = p1.size();
        if(p2 != null){
          s2 = p2.size();
        }else{
          s2 = 0;
        }
        while(i < s1 || j < s2){
          if(i >= s1){
            // iterate through rest of p2 / write p2 entry to disk
            retp.addEntry(p2.get(j));
            j++;
          }else if(j >= s2){
            // iterate through rest of p1 / write p1 entry to disk
            retp.addEntry(p1.get(i));
            i++;
          }else{
            //compare p1 and p2 current entries
            id1 = p1.get(i).docID;
            id2 = p2.get(j).docID;
            if(id1 < id2){
              // write id1 entry to disk
              retp.addEntry(p1.get(i));
              i++;
            }else if(id1 > id2){
              // write id2 entry to disk
              retp.addEntry(p2.get(j));
              j++;
            }else{
              // System.out.println("Thread - merging offsets list");
              // they are the same document, must merge the offsets list
              offsets.addAll(p1.get(i).offsets);
              offsets.addAll(p2.get(j).offsets);
              rete = new PostingsEntry(id1);
              for(int o : offsets){
                rete.addToEntry(o);
              }
              retp.addEntry(rete);
              offsets.clear();
              i++;
              j++;
            }
  
          }
        }
        writeOneEntry(retdata, retdict, retp, token);
      }
      // System.out.println("Thread - going through second dictionary");
      for(int w = 0; w < TABLESIZE; w++){
        dicEntry = readEntry(dict2, w);
        if(dicEntry == null){
          continue;
        }
        token = dicEntry.token;
        if(tokens.contains(token)){
          continue;
        }
        postString = readData(data2, dicEntry.entryPtr, dicEntry.dataSize);
        if(postString == null){
          continue;
        }
        p2 = listFromString(postString);
        // System.out.println("Thread - writing to file (2)");
        writeOneEntry(retdata, retdict, p2, token);
        
      }
      try {
        data1.close();
        dict1.close(); 
        data2.close();
        dict2.close(); 
        File f1a = new File( INDEXDIR + "/" + DICTIONARY_FNAME + d1);
        File f1b = new File( INDEXDIR + "/" + DATA_FNAME + d1);
        f1a.delete();
        f1b.delete();
        f1a = new File( INDEXDIR + "/" + DICTIONARY_FNAME + d2);
        f1b = new File( INDEXDIR + "/" + DATA_FNAME + d2);
        f1a.delete();
        f1b.delete();
      } catch (IOException e) {
        e.printStackTrace();
      }
      
      // System.out.println("Thread - adding docs back to queue");
      synchronized (queuesLock) {
        tempDictFiles.add(retdict);
        tempDataFiles.add(retdata);
        docNums.add(docnum);
        tempDictBitmaps.add(bitmap);
      }
      return docnum;
    }

    public void writeOneEntry(RandomAccessFile retdata, RandomAccessFile retdict, PostingsList retp, String token){
      Entry retEntry;
      /*try {
        synchronized(fileInfoLock){
          writeDocInfo(); 
        } */ 
        long ix = hash(token);
        while(bitmap[(int)ix] == 1){
            ix = (ix + 1) % TABLESIZE;
            if(ix == hash(token)){
                throw new ArrayIndexOutOfBoundsException("dictionary full");
            }
        }
        bitmap[(int)ix] = 1;      
  
        String data = retp.toString();
        int len = data.length();
        retEntry = new Entry(token, freePtr);
        retEntry.dataSize = len;
        writeData(retdata, data, retEntry.entryPtr);
        writeEntry(retdict, retEntry, ix);
        freePtr += len;
    }
  
  }

  

  private void readDocInfo() throws IOException {
    File file = new File( INDEXDIR + "/docInfo" );
    FileReader freader = new FileReader(file);
    try ( BufferedReader br = new BufferedReader(freader) ) {
      String line;
      while ((line = br.readLine()) != null) {
        String[] data = line.split(";");
        this.docNames.put( new Integer(data[0]), data[1] );
        if(data[1] == null){
          System.out.println("HUGE ISSUE: READING IN NULL DOCNAME");
        }
        this.docLengths.put( new Integer(data[0]), new Integer(data[2]) );
      }
    }
    freader.close();
    /*System.out.println("READ IN FILE INFO");
    if (this.docNames.keySet().toString().equals(docNames.keySet().toString())) {
      System.out.println("Match");
    }else{
      System.out.println("Not Match");
    }*/
  }

  Entry readEntry( RandomAccessFile d, long ptr ) {   
  //
  //  REPLACE THE STATEMENT BELOW WITH YOUR CODE 
  //
  try {
      d.seek( ptr*80 );
      byte[] data = new byte[80];
      try {
          d.readFully( data );
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
      entryString = entryString.substring(endOfTerm + 1);
      endOfTerm = entryString.indexOf(" ");
      if(endOfTerm <= 0){
        return null;
      }
      long ePtr = Long.parseLong(entryString.substring(0, endOfTerm));
      entryString = entryString.substring(endOfTerm + 1);
      endOfTerm = entryString.indexOf(" ");
      if(endOfTerm <= 0){
        return null;
      }
      int eSize = Integer.parseInt(entryString.substring(0, endOfTerm));
      return new Entry(eTerm, ePtr, eSize);

    } catch ( IOException e ) {
        e.printStackTrace();
        return null;
    }
  }

  String readData( RandomAccessFile d, long ptr, int size ) {
    try {
        d.seek( ptr );
        byte[] data = new byte[size];
        try {
          d.readFully( data );
        } catch (EOFException e) {
          return null;
        }
        return new String(data);
    } catch ( IOException e ) {
        e.printStackTrace();
        return null;
    }
  }

  void writeEntry(RandomAccessFile d, Entry entry, long ptr ) {
    //
    //  YOUR CODE HERE
    //
    try {
        d.seek(ptr*80);
        byte[] data = entry.toString().getBytes();
        d.write( data );
    } catch (IOException e) {
        e.printStackTrace();
    }
  } 

  int writeData(RandomAccessFile d, String dataString, long ptr ) {
    try {
        d.seek( ptr ); 
        byte[] data = dataString.getBytes();
        d.write( data );
        return data.length;
    } catch ( IOException e ) {
        e.printStackTrace();
        return -1;
    }
  } 

  public void writeIndex(RandomAccessFile dictf, RandomAccessFile dataf, HashMap<String, PostingsList> ix) {
    long freeInFile = 0L;
    int collisions = 0;
        
        for(String t : ix.keySet()){
            if(t.length() > MAX_TERMSIZE){
                continue;
            }
            long i = hash(t);
            while(currentBitmap[(int)i] == 1){
                collisions++;
                i = (i + 1) % TABLESIZE;
                if(i == hash(t)){
                    throw new ArrayIndexOutOfBoundsException("dictionary full");
                }
            }
            currentBitmap[(int)i] = 1;
            
            PostingsList p = ix.get(t);
            

            String data = p.toString();
            int len = data.length();
            Entry dictEntry = new Entry(t, freeInFile);
            dictEntry.dataSize = len;
            writeData(dataf, data, dictEntry.entryPtr);
            writeEntry(dictf, dictEntry, i);
            freeInFile += len;
            

        }
    System.err.println( collisions + " collisions." );
}

  private void writeDocInfo() throws IOException {
    FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo", true);
    for ( Map.Entry<Integer,String> entry : docNames.entrySet() ) {
        Integer key = entry.getKey();
        String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
        if(entry.getValue() == null){
          System.out.println("HUGE ISSUE: READING IN NULL DOCNAME");
        }
        fout.write( docInfoEntry.getBytes() );
    }
    fout.close();
  }

  public PostingsList getPostings(Entry e, RandomAccessFile dataf) {
    String postString = readData(dataf, e.entryPtr, e.dataSize);
    System.out.println(postString);
    return listFromString(postString);
  }

  public PostingsList getPostingFromToken(RandomAccessFile dictf, RandomAccessFile dataf, String token ) {
    //
    //  REPLACE THE STATEMENT BELOW WITH YOUR CODE
    //
    long i = hash(token);
    Entry entryToCheck = readEntry(dictf,i);
    while(true){
        if(entryToCheck == null){
            return null;
        }
        if(entryToCheck.token.equals(token)){
            break;
        }
        i = (i + 1) % TABLESIZE;
        if(i == hash(token)){
            return null;
        }
        entryToCheck = readEntry(dictf, i);
    }
    String postString = readData(dataf, entryToCheck.entryPtr, entryToCheck.dataSize);
    if(postString == null){
      return null;
    }
    return listFromString(postString);
  }

  public void writeIndexTwoMaps(RandomAccessFile dictf, RandomAccessFile dataf, HashMap<String, PostingsList> i1, HashMap<String, PostingsList> i2) {
    int[] bitmap = new int[(int)TABLESIZE];
    long freeInFile = 0L;
    int collisions = 0;
    PostingsList p1, p2;
    PostingsEntry pe1, pe2, pe;
    int ix1, ix2, s2;
    HashSet<String> allTokens = new HashSet<>();
    TreeSet<Integer> allOffsets = new TreeSet<>();
    String data;
        for(String t : i1.keySet()){ // First set of tokens
          allTokens.add(t);
          //if(t.equals("zombie")){
          //  System.out.println("Zombie");
          //}
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
          
          p1 = i1.get(t);
          if(!i2.keySet().contains(t)){
            data = p1.toString();
            // System.out.println("Combined toString");
          }else{
            p2 = i2.get(t);
            s2 = p2.size();
          
            data = "";
            ix1 = 0;
            ix2 = 0;
            while(ix1 < p1.size() || ix2 < s2){
              if(ix1 >= p1.size()){
                data = data + p2.get(ix2).toString() +  "|";
                ix2++;
              }else if(ix2 >= s2){
                data = data + p1.get(ix1).toString() +  "|";
                ix1++;
              }else{
                pe1 = p1.get(ix1);
                pe2 = p2.get(ix2);
                if(pe1.docID < pe2.docID){
                  data = data + p1.get(ix1).toString() +  "|";
                  ix1++;
                }else if(pe2.docID < pe1.docID){
                  data = data + p2.get(ix2).toString() +  "|";
                  ix2++;
                }else{
                  pe = new PostingsEntry(pe1.docID);
                  allOffsets.addAll(pe1.offsets);
                  allOffsets.addAll(pe2.offsets);
                  pe.offsets = new ArrayList<Integer>(allOffsets);
                  data = data + pe.toString() + "|";
                  ix1++;
                  ix2++;
                }
              }
            }
          } 
          int len = data.length();
          Entry dictEntry = new Entry(t, freeInFile);
          dictEntry.dataSize = len;
          writeData(dataf, data, dictEntry.entryPtr);
          writeEntry(dictf, dictEntry, i);
          freeInFile += len;
        }

        for(String t2 : i2.keySet()){ // Any remaining tokens
          if(allTokens.contains(t2)){
            continue;
          }
          allTokens.add(t2);

          long i = hash(t2);
          while(bitmap[(int)i] == 1){
            collisions++;
            i = (i + 1) % TABLESIZE;
            if(i == hash(t2)){
                throw new ArrayIndexOutOfBoundsException("dictionary full");
            }
          }
          bitmap[(int)i] = 1;
          if(t2.equals("zombie")){
            System.out.println("Zombie doc " + i2.get(t2).size());
          }
          data = i2.get(t2).toString();
          int len = data.length();
          Entry dictEntry = new Entry(t2,freeInFile);
          dictEntry.dataSize = len;
          writeData(dataf, data, dictEntry.entryPtr);
          writeEntry(dictf, dictEntry, i);
          freeInFile += len;
        }
    System.err.println( collisions + " collisions." );
  }

  public int mergeTwoMaps(RandomAccessFile dict1, RandomAccessFile dict2, RandomAccessFile data1, RandomAccessFile data2, int d1, int d2){
    int ret = 0;
    HashMap<String,PostingsList> tempIndex = new HashMap<String,PostingsList>();
    HashMap<String, PostingsList> tempIndex2 = new HashMap<String, PostingsList>();
    Entry dicEntry;
    PostingsList p1, p2;
    int docnum = 0;
    // System.out.println("Construct first map");
    for(int i = 0; i < TABLESIZE; i++){
      dicEntry = readEntry(dict1, i);
      if(dicEntry != null){
        p1 = getPostings(dicEntry, data1);
        if(p1 == null){
          System.out.println("First PostingsList is null" + dicEntry + " index: " + i);
          continue;
        }
        tempIndex.put(dicEntry.token, p1);
      }
    }
    // System.out.println("COnstruct second map");
    for(int j = 0; j < TABLESIZE; j++){
      dicEntry = readEntry(dict2, j);
      if(dicEntry != null){
        p2 = getPostings(dicEntry, data2);
        if(p2 == null){
          continue;
        }
        tempIndex2.put(dicEntry.token, p2);
      }
    }
    try {
      data1.close();
      dict1.close(); 
      data2.close();
      dict2.close(); 
      File f1a = new File( INDEXDIR + "/" + DICTIONARY_FNAME + d1);
      File f1b = new File( INDEXDIR + "/" + DATA_FNAME + d1);
      f1a.delete();
      f1b.delete();
      f1a = new File( INDEXDIR + "/" + DICTIONARY_FNAME + d2);
      f1b = new File( INDEXDIR + "/" + DATA_FNAME + d2);
      f1a.delete();
      f1b.delete();
    } catch (IOException e) {
      e.printStackTrace();
    }
    try{
      synchronized (recordCountLock) {
        recordCounter++;
        ret = recordCounter;
        dict1 = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + recordCounter, "rw" );
        data1 = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + recordCounter, "rw" );  
        docnum = recordCounter;
      }
      
    } catch ( IOException e ) {
      e.printStackTrace();
    }
    
    // System.out.println("Call writeIndexTwoMaps");
    // bitmap = writeIndex(dict1, data1, tempIndex, bitmap);
    writeIndexTwoMaps(dict1, data1, tempIndex, tempIndex2);
    synchronized (queuesLock) {
      tempDictFiles.add(dict1);
      tempDataFiles.add(data1);
      docNums.add(docnum);
      // tempDictBitmaps.add(bitmap);
    }
    System.out.println(ret);
    return ret;
    
  } 

  public void merge(){
    RandomAccessFile dict1, dict2, data1, data2;
    
    synchronized (queuesLock) {
      dict1 = tempDictFiles.remove();
      dict2 = tempDictFiles.remove();
      data1 = tempDataFiles.remove();
      data2 = tempDataFiles.remove();
    }
    HashMap<String,PostingsList> tempIndex = new HashMap<String,PostingsList>();
    Entry dicEntry;
    PostingsList p1, p2;
    PostingsEntry e1, e2;
    int id;

    for(int i = 0; i < TABLESIZE; i++){
      dicEntry = readEntry(dict1, i);
      if(dicEntry != null){
        p1 = getPostings(dicEntry, data1);
        if(p1 == null){
          System.out.println("First PostingsList is null" + dicEntry + " index: " + i);
          continue;
        }
        tempIndex.put(dicEntry.token, p1);
      }
    }
    // now tempIndex has all of the first index's records
    for(int i = 0; i < TABLESIZE; i++){
      dicEntry = readEntry(dict2, i);
      if(dicEntry != null){
        p2 = getPostings(dicEntry, data2);
        if(p2 == null){
          System.out.println("Second PostingsList is null (second pass) " + dicEntry);
          break;
        }
        if(tempIndex.containsKey(dicEntry.token)){
          p1 = tempIndex.get(dicEntry.token);

          for(int j = 0; j < p2.size(); j++){
            e2 = p2.get(j);
            if(e2 == null){
              System.out.println("second PostingsEntry is null"+ "entry " + i + ", index: " + j);
              continue;
            }
            id =  e2.getId();
            if(p1 == null){
              System.out.println("First PostingsList is null (second pass) " + id);
            }
            e1 = p1.findByID(id);
            if(e1 == null){
              e1 = new PostingsEntry(id);
              p1.addEntry(e1);
              tempIndex.put(dicEntry.token, p1);
            }else{
              for(int o : e2.offsets){
                int ix = 0;
                while(ix <= e1.offsets.size()-1 && e1.offsets.get(ix) < o ){
                  // System.out.println(e1.offsets.get(ix) +" < " + o);
                  ix++;
                }
                e1.offsets.add(Math.max(ix, 0), o);

              }

            }
          }
        }else{
          if(p2 != null){
            tempIndex.put(dicEntry.token,p2);
          }
        }
      }
    }
    try{
      synchronized (recordCountLock) {
        recordCounter++;
        dict1 = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + recordCounter, "rw" );
        data1 = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + recordCounter, "rw" );  
        currentDoc1 = recordCounter;
      }
      
    } catch ( IOException e ) {
      e.printStackTrace();
    }

    writeIndex(dict1, data1, tempIndex);
    synchronized (queuesLock) {
      tempDictFiles.add(dict1);
      tempDataFiles.add(data1);
      // tempDictBitmaps.add(bitmap);
    }
    
  }

  
  public Entry dictEntryFromString(String entryString){
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
  }
}



