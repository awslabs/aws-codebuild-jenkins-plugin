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

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.codec.binary.Base64.encodeBase64;


public class ZipSourceCallable extends MasterToSlaveFileCallable<String> {

    final FilePath workspace;

    public static final String zipSourceError = "zipSource usage: prefixToTrim must be contained in the given directory.";

    public ZipSourceCallable(FilePath workspace) {
        this.workspace = workspace;
    }

    @Override
    public String invoke(File f, VirtualChannel channel) throws IOException {
        String sourceFilePath = workspace.getRemote();

        // Create a temp file to zip into so we do not zip ourselves
        File tempFile = File.createTempFile(f.getName(), null, null);
        try(OutputStream zipFileOutputStream = new FileOutputStream(tempFile)) {
            try(ZipOutputStream out = new ZipOutputStream(zipFileOutputStream)) {
                zipSource(workspace, sourceFilePath, out, sourceFilePath);
            }
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

        String zipFileMD5;

        // Build MD5 checksum before returning
        try(InputStream zipFileInputStream = new FileInputStream(f)) {
            zipFileMD5 = new String(encodeBase64(DigestUtils.md5(zipFileInputStream)), Charsets.UTF_8);
            return zipFileMD5;
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
    public static void zipSource(FilePath workspace, final String directory, final ZipOutputStream out, final String prefixToTrim) throws Exception {
        if (!Paths.get(directory).startsWith(Paths.get(prefixToTrim))) {
            throw new Exception(zipSourceError + "prefixToTrim: " + prefixToTrim + ", directory: "+ directory);
        }

        FilePath dir = new FilePath(workspace, directory);
        List<FilePath> dirFiles = dir.list();
        if (dirFiles == null) {
            throw new Exception("Empty or invalid source directory: " + directory + ". Did you download any source as part of your build?");
        }
        byte[] buffer = new byte[1024];
        int bytesRead;

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

    // Trim a directory prefix from a file path
    // @param path: file path.
    // @param prefixToTrim: the prefix in directory that should be trimmed before zipping.
    // @return path with no prefixToTrim
    //   Example:
    //     The given path is /tmp/dir/folder/file.txt
    //     The given prefixToTrim can be /tmp/dir/ or /tmp/dir
    //     Then the returned path string will be folder/file.txt.
    public static String trimPrefix(final String path, final String prefixToTrim) {
        return Paths.get(prefixToTrim).relativize(Paths.get(path)).toString();
    }
}
