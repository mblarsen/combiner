/*
 * Copyright (c) 2009 Nicholas C. Zakas. All rights reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
 
package net.nczonline.web.combiner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

public class FileCombiner {

    private HashMap sourceFiles = null;
    private ArrayList todo = null;
    
    /**
     *  Creates a new FileCombiner object.
     */
    public FileCombiner(){
        this.sourceFiles = new HashMap();
        this.todo = new ArrayList();
    }
    
    /**
     * Combines a list of files and outputs the result onto the given writer.
     * @param out Where to place the output.
     * @param files The files to combine.
     * @param charset The character set to use.
     * @param verbose Indicates if warnings and additional information should be output.
     * @param separator Indicates if a separator should be output between files in the final output.
     * @param eliminateUnused Indicates if unused files (those with no dependencies and upon which nothing depends) should be eliminated.
     */
    public void combine(Writer out, File[] files, String charset, boolean verbose, boolean separator, boolean eliminateUnused) throws java.io.IOException {
        processSourceFiles(files, charset, verbose);
	
		ArrayList<SourceFile> finalFiles = new ArrayList<SourceFile>();
		constructFileList(finalFiles, sourceFiles.values(), new Stack<SourceFile>(), verbose, eliminateUnused);			
		SourceFile[] finalSourceFiles = new SourceFile[finalFiles.size()];
        finalFiles.toArray(finalSourceFiles);
        
        writeToOutput(out, finalSourceFiles, verbose, separator);
    }
    
    /**
     * Combines a list of files and outputs the result onto the given writer.
     * @param out Where to place the output.
     * @param files The filenames of the files to combine.
     * @param charset The character set to use.
     * @param verbose Indicates if warnings and additional information should be output.
     * @param separator Indicates if a separator should be output between files in the final output.
     * @param eliminateUnused Indicates if unused files (those with no dependencies and upon which nothing depends) should be eliminated.
     */    
    public void combine(Writer out, String[] filenames, String charset, boolean verbose, boolean separator, boolean eliminateUnused) throws java.io.IOException {
        ArrayList files = new ArrayList();
        
        for (int i=0; i < filenames.length; i++){
            File file = new File(filenames[i]);
            if (file.isFile()){
                files.add(file);
                if (verbose){
                    System.err.println("[INFO] Adding file '" + file.getCanonicalPath() + "'");
                }
            } else {
                if (verbose){
                    System.err.println("[INFO] Couldn't find file '" + filenames[i] + "'");
                }
                
            }
        }
        
        File[] finalFiles = new File[files.size()];
        files.toArray(finalFiles);        
        combine(out, finalFiles, charset, verbose, separator, eliminateUnused);
    }
    
    private void processSourceFiles(File[] files, String charset, boolean verbose) throws java.io.IOException {
        //add to ToDo list
        for (int i=0; i < files.length; i++){
            todo.add(files[i]);
        }
        
        //process files
        for (int i=0; i < todo.size(); i++){
            //get a source file object
            SourceFile depSourceFile = getSourceFile((File) todo.get(i));

            //if there's no contents, then it needs to be processed
            if (depSourceFile.getContents() == null){            
                processSourceFile((File) todo.get(i), charset, verbose);
            }
        }
    }
    
    private SourceFile getSourceFile(File file) throws java.io.IOException {
        SourceFile sourceFile = null;
        if (sourceFiles.containsKey(file.getCanonicalPath())){
            return (SourceFile) sourceFiles.get(file.getCanonicalPath());
        } else {
            sourceFile = new SourceFile(file);
            sourceFiles.put(file.getCanonicalPath(), sourceFile);
            return sourceFile;
        }
              
    }
    
    private void processSourceFile(File file, String charset, boolean verbose) throws java.io.IOException {
        SourceFile sourceFile = getSourceFile(file);
        
        //if it already has dependencies, then it's already been processed (prevents infinite loop if a circular dependency is detected)
        if (sourceFile.hasDependencies()){
            return;
        }
                
        try {
            Reader in = new InputStreamReader(new FileInputStream(file), charset);
            StringBuffer fileData = new StringBuffer();
            String filename = null;
            File depFile = null;
            int requirements = 0;
            
            if (verbose){
                System.err.println("[INFO] Processing file '" + file.getAbsolutePath() + "'");
            }
            
            int c = 0, prev=0;

            while((c = in.read()) != -1){
                if ((char)c == '/' && (char)prev != '*'){
                    char[] buf = new char[9];
                    in.read(buf, 0, 9);
                    if (String.valueOf(buf).equals("*requires")){
                        filename = "";
                        while ((c = in.read()) != 0){
                            if ((char)c == '*'){
                                c = in.read();
                                if ((char)c == '/'){
                                    break;
                                } else {
                                   System.err.println("[ERROR] Invalid requires comment."); 
                                   System.exit(1);
                                }
                            } else {
                                filename += String.valueOf((char)c);
                            }
                        }
                        
                        filename = filename.trim();
                        
                        if (verbose){
                            System.err.println("[INFO] ... has dependency on " + filename);
                        }
                        
                        if (!filename.startsWith("/") && !filename.startsWith("\\")){
                            filename = sourceFile.getDirectory() + filename;
                        }
                        
                        depFile = new File(filename);
                        
                        //verify that the file actually exists
                        if (depFile.isFile()){
                            
                            //get a source file object
                            SourceFile depSourceFile = getSourceFile(depFile);
                            
                            //if there's no contents, then it needs to be processed
                            if (depSourceFile.getContents() == null){
                                todo.add(depFile);                            
                            }
                            sourceFile.addDependency(depSourceFile);
                        } else {
                            System.err.println("[ERROR] Dependency file not found: '" + filename + "'");
                            System.exit(1);
                        }
                        
                        requirements++;
                    } else {
                        fileData.append((char)c);
                        fileData.append(buf);
                    }
                } else {
                    fileData.append((char)c);
                }

                prev = c;
            }
            
            sourceFile.setContents(fileData.toString());
            in.close();
            
            if (verbose && requirements == 0){
                System.err.println("[INFO] ... no dependencies found.");
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }      
    }
    
 	private void constructFileList(ArrayList<SourceFile> output, Collection<SourceFile> dependencies, Stack<SourceFile> ancestorStack, boolean verbose, boolean eliminateUnused) throws java.io.IOException{
		for (Iterator<SourceFile> it = dependencies.iterator(); it.hasNext();) {
			SourceFile sourceFile = it.next();
			
			if (verbose) {
				System.err.println("[INFO] Verifying dependencies of '" + sourceFile.getName());
			}
			
			// If the ancestor stack contains the file we have a cycle.
			if (ancestorStack.contains(sourceFile)) {
				for(Iterator<SourceFile> ssIt = ancestorStack.iterator(); ssIt.hasNext();) {
					SourceFile dependent = ssIt.next();
					// Check if the ancestor is both depedent and dependee - if so - quit the application.
					if (dependent.hasDependency(sourceFile) && sourceFile.hasDependency(dependent)) {
						System.err.println("ERROR: Circular dependency found" + sourceFile.getName() + " and " + dependent.getName());
						System.exit(1);						
					}
				}
			}
			
			// If file already output skip.
			if (output.contains(sourceFile)) {
				continue;
			}
						
			// If the file has no dependencies no need to go deeper.
			if (!sourceFile.hasDependencies()) {
				// If eliminateUnused flag is raised don't add to output
				if (!eliminateUnused) {
					output.add(sourceFile);					
				}
				continue;
			}
			
			// Add file to ancestor stack and check depencies recursivly.
			ancestorStack.push(sourceFile);
			
			ArrayList<SourceFile> dependencySourceFiles = new ArrayList<SourceFile>(java.util.Arrays.asList(sourceFile.getDependencies()));
			constructFileList(output, dependencySourceFiles, ancestorStack, verbose, eliminateUnused);
			
			// The file can safly be added to output.
			output.add(sourceFile);
			
			// Remove the file from stack.
			ancestorStack.pop();
		}
	}
    
    private void writeToOutput(Writer out, SourceFile[] finalFiles, boolean verbose, boolean separator) {
        try {
            for (int i=0; i < finalFiles.length; i++) {
                if (verbose){
                    System.out.println("[INFO] Adding '" + finalFiles[i].getName() + "' to output.");
                }
                if (separator){
                    out.write("\n/*------" + finalFiles[i].getName() + "------*/\n");
                }
                out.write(finalFiles[i].getContents());
            }
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }            
    }
}