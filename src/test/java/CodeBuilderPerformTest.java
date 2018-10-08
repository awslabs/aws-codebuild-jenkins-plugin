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

import com.amazonaws.services.codebuild.model.*;
import enums.*;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.util.Secret;
import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({CodeBuilder.class, Secret.class})
public class CodeBuilderPerformTest extends CodeBuilderTest {

    @Before
    public void SetUp() throws Exception {
        setUpBuildEnvironment();
    }

    @Test
    public void testConfigAllNull() throws Exception {
        CodeBuilder test = new CodeBuilder(null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);

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
        CodeBuilder test = new CodeBuilder("", "", "", "", "",
                null, "", "", "", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "");

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
                null, "", CodeBuildRegions.IAD.toString(), "", "", "",
                SourceControlType.ProjectSource.toString(), "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.projectRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(Validation.projectRequiredError));
    }

    @Test
    public void testNoSourceType() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "", "", "", "",
                null, "", CodeBuildRegions.IAD.toString(), "project", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "", "",
                "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.sourceControlTypeRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(Validation.sourceControlTypeRequiredError));
    }

    @Test
    public void testStartBuildExcepts() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "StartBuild exception";
        doThrow(new InvalidInputException(error)).when(mockClient).startBuild(any(StartBuildRequest.class));
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
        doThrow(new InvalidInputException(error)).when(mockFactory).getCodeBuildClient();
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
        doThrow(new InvalidInputException(error)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
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
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", "invalidComputeType", CacheType.NO_CACHE.toString(), "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidComputeTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidComputeTypeError));
    }

    @Test
    public void testCacheTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), "invalidCacheType", "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidCacheTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidCacheTypeError));
    }

    @Test
    public void testCloudWatchLogsStatusOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "",
                "invalidCloudWatchLogsStatus", "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidCloudWatchLogsStatusError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidCloudWatchLogsStatusError));
    }

    @Test
    public void testS3LogsStatusOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", "invalidS3LogsStatus", "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidS3LogsStatusError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidS3LogsStatusError));
    }

    @Test
    public void testSourceTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", "invalidSourceType", "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidSourceTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidSourceTypeError));
    }

    @Test
    public void testEnvironmentTypeOverrideException() throws Exception {
        CodeBuilder test = new CodeBuilder("keys", "id123", "host", "60", "a", awsSecretKey,
                "", CodeBuildRegions.IAD.toString(), "existingProject", "sourceVersion", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                "invalidEnvironmentType", "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidEnvironmentTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidEnvironmentTypeError));
    }

    @Test
    public void testBuildParameters() throws Exception {
        envVars.put("foo", "bar");
        envVars.put("foo2", "bar2");
        envVars.put("foo3", "bar3");

        CodeBuilder cb = new CodeBuilder("keys", "id123", "host", "60", "a",
                awsSecretKey, "", "us-east-1", "$foo", "$foo2-$foo3", "",
                SourceControlType.ProjectSource.toString(), GitCloneDepth.One.toString(), BooleanValue.False.toString(), ArtifactsType.NO_ARTIFACTS.toString(), "", "",
                "", "", "", BooleanValue.False.toString(), BooleanValue.False.toString(),
                "[{k, v}]", "", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(), "https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(), "aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(), CacheType.NO_CACHE.toString(), "",
                LogsConfigStatusType.ENABLED.toString(), "group", "stream", LogsConfigStatusType.ENABLED.toString(), "location",
                "arn:aws:s3:::my_bucket/certificate.pem", "my_service_role", BooleanValue.False.toString(), BooleanValue.False.toString());

        cb.perform(build, ws, launcher, listener, mockStepContext);

        assertEquals(envVars.get("foo"), cb.getParameterized(cb.getProjectName()));
        assertEquals(envVars.get("foo2") + "-" + envVars.get("foo3"), cb.getParameterized(cb.getSourceVersion()));
    }

    private class Parameter extends ParameterValue {
        @Getter @Setter String value;

        protected Parameter(String name, String description) {
            super(name, description);
        }
    }
}
