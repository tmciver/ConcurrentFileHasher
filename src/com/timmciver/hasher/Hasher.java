package com.timmciver.hasher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Hasher {
    
    private static final int NUM_CONSUMER_THREADS = 10;
    private static final long SLEEP_TIME_MS = 100;
    
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
        
        // create the queues
        BlockingQueue<File> fileQueue = new ArrayBlockingQueue<File>(10);
        BlockingQueue<Long> hashQueue = new LinkedBlockingDeque<Long>();
        
        // create and start one file producer
        FileProducer fileCrawler = new FileProducer(root, fileQueue);
        new Thread(fileCrawler).start();
        
        // create and start the hashers
        Collection<Thread> hashers = new ArrayList<Thread>(NUM_CONSUMER_THREADS);
        for (int i = 0; i < NUM_CONSUMER_THREADS; ++i) {
            Thread t = new Thread(new FileHasher(fileQueue, hashQueue));
            hashers.add(t);
            t.start();
        }
        
        // wait for the fileCrawler to finish
        while (!fileCrawler.isDone()) {
            try {
                Thread.sleep(SLEEP_TIME_MS);
            } catch (InterruptedException ex) {/* do nothing */}
        }
        
        // now wait for the file queue to be empty
        while (!fileQueue.isEmpty()) {
            try {
                Thread.sleep(SLEEP_TIME_MS);
            } catch (InterruptedException ex) {/* do nothing */}
        }
        
        // now stop all hasher threads
        for (Thread t : hashers) {
            t.interrupt();
        }
        
        System.out.println("Hashing complete.");
        System.out.println("Hashed " + hashQueue.size() + " files.");
    }
}

class FileProducer implements Runnable {
    
    private File root;
    private BlockingQueue<File> queue;
    private Hasher app;
    private boolean done = false;

    public FileProducer(File root, BlockingQueue<File> queue) {
        this.root = root;
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            crawl(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            done = true;
        }
    }
    
    public boolean isDone() {
        return done;
    }
    
    /*
     * Crawls the file tree begining at root adding all files to the queue.
     * @param root of the file tree to crawl.
     */
    private void crawl(File root) throws InterruptedException {
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    crawl(child);
                } else if (child.isFile()) {
                    System.out.println("Crawler grabbed file: " + child.getAbsolutePath());
                    queue.put(child);
                }
            }
        }
    }
}

class FileHasher implements Runnable {
    
    private BlockingQueue<File> fileQueue;
    private BlockingQueue<Long> hashQueue;

    public FileHasher(BlockingQueue<File> fileQueue, BlockingQueue<Long> hashQueue) {
        this.fileQueue = fileQueue;
        this.hashQueue = hashQueue;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // get a file from the queue
                File file = fileQueue.take();
                
                System.out.println("FileHasher with ID: " + Thread.currentThread().getId() + " is hashing file: " + file.getAbsolutePath());

                // calculate the file hash
                long hash = calcHash(file);

                // put the hash in the hash queue
                hashQueue.put(hash);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private long calcHash(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FileHasher.class.getName()).log(Level.SEVERE, "Could not open file: " + file, ex);
        }
        
        byte[] bytes = new byte[(int)file.length()];
        try {
            in.read(bytes);
        } catch (IOException ex) {
            Logger.getLogger(FileHasher.class.getName()).log(Level.SEVERE, "Could not read file: " + file, ex);
        }
        
        // calculate the hash
        long hash = 0;
        for (byte val : bytes) {
            hash += val;
        }
        
        return hash;
    }
}
