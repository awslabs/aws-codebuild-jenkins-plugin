/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors. Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.*;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import enums.SourceControlType;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeBuilderTest {

    AWSClientFactory mockFactory = mock(AWSClientFactory.class);
    ProjectFactory mockProjectFactory = mock(ProjectFactory.class);
    AWSCodeBuildClient mockClient = mock(AWSCodeBuildClient.class);
    AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
    S3DataManager mockDataManager = mock(S3DataManager.class);
    AWSLogsClient mockLogsClient = mock(AWSLogsClient.class);
    CloudWatchMonitor mockMonitor = mock(CloudWatchMonitor.class);
    CodeBuildAction mockAction = mock(CodeBuildAction.class);
    Answer mockInterruptedException = new Answer() {
        public Object answer(InvocationOnMock invocation) throws InterruptedException {
            throw new InterruptedException();
        }
    };

    BatchGetProjectsResult mockBGPResult = mock(BatchGetProjectsResult.class);
    StartBuildResult mockStartBuildResult = mock(StartBuildResult.class);
    BatchGetBuildsResult mockGetBuildsResult = mock(BatchGetBuildsResult.class);
    Build mockBuild = mock(Build.class);

    Run build = mock(Run.class);
    FilePath ws = new FilePath(new File("/path/to"));
    Launcher launcher = mock(Launcher.class);
    TaskListener listener = mock(TaskListener.class);
    Secret awsSecretKey = PowerMockito.mock(Secret.class);
    EnvVars envVars = new EnvVars();

    //mock console log
    protected ByteArrayOutputStream log;

    //creates a CodeBuilder with mock parameters that reflect a typical use case.
    protected CodeBuilder createDefaultCodeBuilder() {
        CodeBuilder cb = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.ProjectSource.toString(),
                "1", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(),"https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(),"aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(),CacheType.NO_CACHE.toString(),"",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");

            // It will be a mock factory during testing.
        return cb;

    }

    //sets up a basic mock environment for calling perform()
    protected void setUpBuildEnvironment() throws Exception {
        PowerMockito.whenNew(AWSClientFactory.class).withAnyArguments().thenReturn(mockFactory);
        when(awsSecretKey.getPlainText()).thenReturn("s");

        ProjectArtifacts artifacts = new ProjectArtifacts();
        artifacts.setLocation("artifactBucket");
        artifacts.setType("S3");
        ProjectSource source = new ProjectSource();
        source.setLocation("arn:aws:s3:::my_corporate_bucket/exampleobject.png");
        source.setType("S3");
        Project project = new Project();
        project.setArtifacts(artifacts);
        project.setSource(source);

        ArrayList<Project> projects = new ArrayList<Project>();
        projects.add(project);

        when(mockBuild.getBuildStatus()).thenReturn(StatusType.SUCCEEDED.toString().toUpperCase());
        when(mockBuild.getStartTime()).thenReturn(new Date(0));
        when(mockBGPResult.getProjects()).thenReturn(projects);
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockFactory.getS3Client()).thenReturn(mockS3Client);
        when(mockFactory.getCloudWatchLogsClient()).thenReturn(mockLogsClient);
        when(mockClient.startBuild(any(StartBuildRequest.class))).thenReturn(mockStartBuildResult);
        when(mockStartBuildResult.getBuild()).thenReturn(new Build());
        when(mockClient.batchGetProjects(any(BatchGetProjectsRequest.class))).thenReturn(mockBGPResult);
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class))).thenReturn(mockGetBuildsResult);
        when(mockGetBuildsResult.getBuilds()).thenReturn(Arrays.asList(mockBuild));
        when(build.getFullDisplayName()).thenReturn("job #1234");
        when(build.getEnvironment(any(TaskListener.class))).thenReturn(envVars);
        PowerMockito.mockStatic(Thread.class);
        Thread.sleep(anyLong());
        when(awsSecretKey.getPlainText()).thenReturn("secretKey");
    }

    @Before
    public void setUp() throws FileNotFoundException {
        //set the CodeBuilder instance to write its messages to the log here so
        //we can read what it prints.
        log = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(log);
        when(listener.getLogger()).thenReturn(p);
    }
}
