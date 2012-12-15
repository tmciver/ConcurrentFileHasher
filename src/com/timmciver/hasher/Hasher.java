package com.timmciver.hasher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Hasher {
    
    public static void main(String[] args) {
        File root = null;
        if (args.length > 0) {
            root = new File(args[0]);
        } else {
            root = new File(".");
        }
        
        if (root == null || !root.exists()) {
            System.err.println("Must call with a valid file path.");
            System.exit(1);
        }
        
        // populate a list of files to hash
        List<File> files = crawl(root);
        
        // create and start a FileProducer thread for each file
        for (File file : files) {
            Thread t = new Thread(new FileHasher(file));
            t.start();
            try {
                t.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Hasher.class.getName()).log(Level.SEVERE, "Failed to join thread " + t, ex);
            }
        }
        
        System.out.println("Hashing complete.");
        System.out.println("Hashed " + files.size() + " files.");
    }
    
    private static List<File> crawl(File root) {
        List<File> files = Collections.EMPTY_LIST;
        File[] children = root.listFiles();
        if (children != null) {
            files = new ArrayList<File>();
            for (File child : children) {
                if (child.isDirectory()) {
                    files.addAll(crawl(child));
                } else if (child.isFile()) {
                    files.add(child);
                }
            }
        }
        return files;
    }
}

class FileHasher implements Runnable {
    
    private File file;

    public FileHasher(File file) {
        this.file = file;
    }

    @Override
    public void run() {
        try {
            // calculate the file hash
            long hash = calcHash(file);
        } catch (FileNotFoundException ex) {
            //Logger.getLogger(FileHasher.class.getName()).log(Level.SEVERE, "Could not open file: " + file, ex);
        } catch (IOException ex) {
            //Logger.getLogger(FileHasher.class.getName()).log(Level.SEVERE, "Could not read file: " + file, ex);
        }
    }
    
    private long calcHash(File file) throws FileNotFoundException, IOException {
        InputStream in = new FileInputStream(file);
        
        byte[] bytes = new byte[(int)file.length()];
        in.read(bytes);
        
        // calculate the hash
        long hash = 0;
        for (byte val : bytes) {
            hash += val;
        }
        
        return hash;
    }
}
