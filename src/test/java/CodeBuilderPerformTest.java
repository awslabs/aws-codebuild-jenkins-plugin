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

import software.amazon.awssdk.services.codebuild.model.*;
import software.amazon.awssdk.core.exception.SdkClientException;
import enums.*;
import hudson.model.ParameterValue;
import hudson.model.Result;
import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

//import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CodeBuilderPerformTest extends CodeBuilderTest {

    @Before
    public void SetUp() throws Exception {
        setUpBuildEnvironment();
    }

    @Test
    public void testConfigAllNull() throws Exception {
        CodeBuilder test = new CodeBuilder(null, null, null, null, null, null, null, null, null, null, null, null,
                                           null, null, null, null, null, null, null, null, null, null, null, null,
                                           null, null, null, null, null, null, null, null, null, null, null, null,
                                           null, null, null, null, null, null, "", null, null, null,
                                           null, null, null, null, null, null, null);
        test.workspaceIncludes = null;
        test.workspaceExcludes = null;

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testConfigAllBlank() throws Exception {
        CodeBuilder test = new CodeBuilder("", "", "", "", "", null, "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        test.workspaceIncludes = "";
        test.workspaceExcludes = "";

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testNoProjectName() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "", "", "", "",
                                           null, "", "us-east-1", "", "", "",
                                           SourceControlType.ProjectSource.toString(), "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.projectRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.projectRequiredError));
    }

    @Test
    public void testNoSourceType() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "", "", "", "",
                                           null, "", "us-east-1", "project", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.sourceControlTypeRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.sourceControlTypeRequiredError));
    }

    @Test
    public void testStartBuildExcepts() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "StartBuild exception";
        doThrow(InvalidInputException.builder().message(error).build()).when(mockClient).startBuild(any(StartBuildRequest.class));
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testGetCBClientExcepts() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "failed to instantiate cb client.";
        doThrow(InvalidInputException.builder().message(error).build()).when(mockFactory).getCodeBuildClient();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testBatchGetBuildsExcepts() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "cannot get build";
        doThrow(InvalidInputException.builder().message(error).build()).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testComputeTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", "invalidComputeType", CacheType.NO_CACHE.toString(), "", "",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidComputeTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidComputeTypeError));
    }

    @Test
    public void testCacheTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), "invalidCacheType", "", "",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidCacheTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidCacheTypeError));
    }

    @Test
    public void testCacheModesOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), "LOCAL", "", "[invalidMode, LOCAL_DOCKER_LAYER_CACHE]",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidCacheModesError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidCacheModesError));
    }

    @Test
    public void testCloudWatchLogsStatusOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "[LOCAL_DOCKER_LAYER_CACHE]",
                                           "invalidCloudWatchLogsStatus", "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidCloudWatchLogsStatusError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidCloudWatchLogsStatusError));
    }

    @Test
    public void testS3LogsStatusOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "[LOCAL_CUSTOM_CACHE]",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", "invalidS3LogsStatus", "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidS3LogsStatusError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidS3LogsStatusError));
    }

    @Test
    public void testSourceTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", "invalidSourceType", "https://1.0.0.0.86/my_repo",
                                           EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "[LOCAL_CUSTOM_CACHE, LOCAL_SOURCE_CACHE]",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidSourceTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidSourceTypeError));
    }

    @Test
    public void testInvalidExistingSourceTypeForJenkinsSource() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey, "",
                                         "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.JenkinsSource.toString(), "", "",
                                         GitCloneDepth.One.toString(), BooleanValue.False.toString(), "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                                         "", BooleanValue.False.toString(), BooleanValue.False.toString(), "", "[{k, v}]", "[{k, p}]",
                                         "buildspec.yml", "5", "", "",
                                         EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "",
                                         LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                         "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");

        Project mockProject = Project.builder().source(ProjectSource.builder().type(SourceType.BITBUCKET).build()).build();
        when(mockClient.batchGetProjects(any(BatchGetProjectsRequest.class))).thenReturn(BatchGetProjectsResponse.builder().projects(mockProject).build());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Unexpected log contents: " + log.toString(), log.toString().contains(CodeBuilder.jenkinsSourceProjectSourceTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.jenkinsSourceProjectSourceTypeError));
    }

    @Test
    public void testInvalidSourceTypeOverrideForJenkinsSource() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey, "",
                                         "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.JenkinsSource.toString(), "", "",
                                         GitCloneDepth.One.toString(), BooleanValue.False.toString(), "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                                         "", BooleanValue.False.toString(), BooleanValue.False.toString(), "", "[{k, v}]", "[{k, p}]",
                                         "buildspec.yml", "5", SourceType.GITHUB.toString(), "",
                                         EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "",
                                         LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                         "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");

        Project mockProject = Project.builder().source(ProjectSource.builder().type(SourceType.BITBUCKET).build()).build();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Unexpected log contents: " + log.toString(), log.toString().contains(CodeBuilder.jenkinsSourceOverrideError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.jenkinsSourceOverrideError));
    }

    @Test
    public void testEnvironmentTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                                           "", "us-east-1", "existingProject", "sourceVersion", "",
                                           SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                           "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                           "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                           "invalidEnvironmentType", "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "",
                                           LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                           "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilderValidation.invalidEnvironmentTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilderValidation.invalidEnvironmentTypeError));
    }

    @Test
    public void testBuildParameters() throws Exception {
        envVars.put("foo", "bar");
        envVars.put("foo2", "bar2");
        envVars.put("foo3", "bar3");

        CodeBuilder cb = new CodeBuilder("keys", "id123", "host", "60", "a",
                                         awsSecretKey, "", "us-east-1", "$foo", "$foo2-$foo3", "",
                                         SourceControlType.ProjectSource.toString(), "", "", GitCloneDepth.One.toString(), BooleanValue.False.toString(),
                                         "", "", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(), "",
                                         "[{k, v}]", "", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                                         EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "", "",
                                         LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "", "location",
                                         "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString(), BooleanValue.False.toString(), "", "DISABLED", "");

        cb.perform(build, ws, launcher, listener, mockStepContext);

        assertEquals(envVars.get("foo"), cb.getParameterized(cb.getProjectName()));
        assertEquals(envVars.get("foo2") + "-" + envVars.get("foo3"), cb.getParameterized(cb.getSourceVersion()));
    }

    @Test
    public void testEarlyFactoryFailureWithUninitializedResultSurfacesRealError() throws Exception {
        String underlyingError = "Unable to load AWS credentials from any provider in the chain";

        awsClientFactoryConstruction.close();
        awsClientFactoryConstruction = null;

        CodeBuilder test = createDefaultCodeBuilder();

        java.lang.reflect.Field resultField = CodeBuilder.class.getDeclaredField("codeBuildResult");
        resultField.setAccessible(true);
        resultField.set(test, null);

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        try (MockedConstruction<AWSClientFactory> throwingConstruction = mockConstruction(AWSClientFactory.class,
                (constructed, context) -> {
                    throw SdkClientException.builder().message(underlyingError).build();
                })) {
            test.perform(build, ws, launcher, listener, mockStepContext);
        }

        verify(build).setResult(savedResult.capture());
        assertEquals(Result.FAILURE, savedResult.getValue());
        assertTrue("Unexpected NullPointerException in log: " + log.toString(),
                   !log.toString().contains("NullPointerException"));
        assertTrue("Missing authorization error in log: " + log.toString(),
                   log.toString().contains(CodeBuilder.authorizationError));
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue("Result error message missing authorization error: " + result.getErrorMessage(),
                   result.getErrorMessage().startsWith(CodeBuilder.authorizationError));
        assertTrue("Underlying error detail was swallowed: " + result.getErrorMessage(),
                   result.getErrorMessage().contains("\n\t> "));
    }

    @Test
    public void testPollExceptionWithNullMessage() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        doThrow(new RuntimeException((String) null)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(Result.FAILURE, savedResult.getValue());
        assertTrue("Unexpected NullPointerException in log: " + log.toString(),
                   !log.toString().contains("NullPointerException"));
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    private class Parameter extends ParameterValue {
        @Getter @Setter String value;

        protected Parameter(String name, String description) {
            super(name, description);
        }
    }
}
