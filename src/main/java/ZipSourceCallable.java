/*
 *  Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 *
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (C) 2015 The Project Lombok Authors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.InvalidInputException;
import hudson.FilePath;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class ZipSourceCallable extends MasterToSlaveFileCallable<String> {

    final FilePath workspace;
    final String includes;  // never null
    final String excludes;  // never null

    public static final String zipSourceError = "zipSource usage: prefixToTrim must be contained in the given directory.";

    public ZipSourceCallable(FilePath workspace) {
        this(workspace, null, null);
    }

    public ZipSourceCallable(FilePath workspace, String includes, String excludes) {
        this.workspace = workspace;
        this.includes = Util.fixNull(includes);
        this.excludes = Util.fixNull(excludes);
    }

    @Override
    public String invoke(File f, VirtualChannel channel) throws IOException {
        // Create a temp file to zip into so we do not zip ourselves
        File tempFile = File.createTempFile(f.getName(), null, null);
        try(OutputStream zipFileOutputStream = new FileOutputStream(tempFile)) {
            zipSourceWithArchiver(zipFileOutputStream);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }

        // Copy zip to the location we expect it to be in
        FileUtils.copyFile(tempFile, f);

        try {
            tempFile.delete();
        } catch (Exception e) {
            // If this fails, the file will just be cleaned up
            // by the system later.  We are just trying to be
            // good citizens here.
        }

        return S3DataManager.getZipMD5(f);
    }

    @Restricted(NoExternalUse.class)    // For testing purpose
    protected void zipSourceWithArchiver(final OutputStream out) throws InvalidInputException, IOException, InterruptedException {
        if (!workspace.exists() || !workspace.isDirectory()) {
            throw new InvalidInputException("Empty or invalid source directory: " + workspace.getRemote());
        }
        Archiver archiver = ArchiverFactory.ZIP.create(out);
        try {
            this.zipSourceWithArchiverImpl(archiver);
        } finally {
            archiver.close();
        }
    }

    private void zipSourceWithArchiverImpl(final Archiver archiver) throws InvalidInputException, IOException, InterruptedException {
        String sourceFilePath = workspace.getRemote();

        // NOTE: This code is running on the remote.
        // FilePath.list() is really powerful, but cannot be used as it doesn't pick empty directories.
        FileSet fs = Util.createFileSet(new File(sourceFilePath), includes, excludes);
        fs.setDefaultexcludes(false);   // for backward compatibility
        DirectoryScanner ds;
        try {
            ds = fs.getDirectoryScanner(new Project());
        } catch (BuildException e) {
            throw new IOException(e.getMessage());
        }
        // To include directories with no files
        for (String dir: ds.getIncludedDirectories()) {
            if ("".equals(dir)) {
                // skip the top directory to make an invalid archive if no files.
                // (backward compatibility)
                continue;
            }
            archiver.visit(new File(sourceFilePath, dir), dir);
        }
        for (String file: ds.getIncludedFiles()) {
            archiver.visit(new File(sourceFilePath, file), file);
        }
    }

    // Recursively zips everything in the given directory into a zip file using the given ZipOutputStream.
    // @param directory: whose contents will be zipped.
    // @param out: ZipOutputStream that will write data to its zip file.
    // @param prefixToTrim: the prefix in directory that should be trimmed before zipping.
    //    Example:
    //     The given directory is /tmp/dir/folder/ which contains one file /tmp/dir/folder/file.txt
    //     The given prefixToTrim is /tmp/dir/
    //     Then the zip file created will expand into folder/file.txt
    //    Example:
    //     The given directory is /tmp/dir/folder/ which contains one file /tmp/dir/folder/file.txt
    //     The given prefixToTrim is /tmp/dir/folder
    //     Then the zip file created will expand into file.txt
    // @deprecated no longer used. left for binary compatibility.
    @Deprecated
    public static void zipSource(FilePath workspace, final String directory, final ZipOutputStream out, final String prefixToTrim) throws InvalidInputException, IOException, InterruptedException {
        if (!Paths.get(directory).startsWith(Paths.get(prefixToTrim))) {
            throw new InvalidInputException(zipSourceError + "prefixToTrim: " + prefixToTrim + ", directory: "+ directory);
        }

        FilePath dir = new FilePath(workspace, directory);
        List<FilePath> dirFiles = dir.list();
        if (dirFiles == null) {
            throw new InvalidInputException("Empty or invalid source directory: " + directory);
        }
        byte[] buffer = new byte[1024];
        int bytesRead;

        if (dirFiles.isEmpty()) {
            String path = trimPrefix(dir.getRemote(), prefixToTrim);
            if (!path.isEmpty()) {
                out.putNextEntry(new ZipEntry(path + "/"));
            }
        } else {
            for (int i = 0; i < dirFiles.size(); i++) {
                FilePath f = new FilePath(workspace, dirFiles.get(i).getRemote());
                if (f.isDirectory()) {
                    zipSource(workspace, f.getRemote() + File.separator, out, prefixToTrim);
                } else {
                    InputStream inputStream = f.read();
                    try {
                        String path = trimPrefix(f.getRemote(), prefixToTrim);

                        if(path.startsWith(File.separator)) {
                            path = path.substring(1, path.length());
                        }

                        // Zip files created on the windows file system will not unzip
                        // properly on unix systems. Without this change, no directory structure
                        // is built when unzipping.
                        path = path.replace(File.separator, "/");

                        ZipEntry entry = new ZipEntry(path);
                        out.putNextEntry(entry);
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    } finally {
                        inputStream.close();
                    }
                }
            }
        }
    }

    // Trim a directory prefix from a file path
    // @param path: file path.
    // @param prefixToTrim: the prefix in directory that should be trimmed before zipping.
    // @return path with no prefixToTrim
    //   Example:
    //     The given path is /tmp/dir/folder/file.txt
    //     The given prefixToTrim can be /tmp/dir/ or /tmp/dir
    //     Then the returned path string will be folder/file.txt.
    // @deprecated no longer used. left for binary compatibility.
    @Deprecated
    public static String trimPrefix(final String path, final String prefixToTrim) {
        return Paths.get(prefixToTrim).relativize(Paths.get(path)).toString();
    }


}
