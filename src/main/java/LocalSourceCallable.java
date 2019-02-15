/*
 *  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
import org.apache.commons.io.FileUtils;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;

@RequiredArgsConstructor
public class LocalSourceCallable extends MasterToSlaveFileCallable<String> {

    private final FilePath workspace;
    private final String remoteFile;

    @Override
    public String invoke(File localFile, VirtualChannel channel) throws IOException {
        File remoteSource = new File(workspace.child(remoteFile).getRemote());
        FileUtils.copyFile(remoteSource, localFile);
        return S3DataManager.getZipMD5(localFile);
    }
}
