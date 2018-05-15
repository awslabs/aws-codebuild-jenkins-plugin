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

import com.amazonaws.services.codebuild.model.ArtifactsType;
import com.amazonaws.services.codebuild.model.SourceType;
import com.amazonaws.services.codebuild.model.CacheType;
import com.amazonaws.services.codebuild.model.ComputeType;
import com.amazonaws.services.codebuild.model.EnvironmentType;
import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import enums.SourceControlType;
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
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

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
                null, "", "", "", "", "", "", "", "",
                "","","","","",     "", "", "", "", "" , "",
                "", "", "", "", "",
                "","","","");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testNoProjectName() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "", "", "", "", null, "",
                "us-east-1", "", "", "", SourceControlType.ProjectSource.toString(),
                "", "", "", "", "", "",
                "","", "", "", "", "","",
                "","", "", "", "",
                "","","","");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

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
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "", "", "", "", null,"",
                "us-east-1", "project", "", "", "",
                "", "", "", "", "", "",
                "","", "", "", "", "","",
                "","", "","","",
                "","","","");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

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

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testGetCBClientExcepts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "failed to instantiate cb client.";
        doThrow(new InvalidInputException(error)).when(mockFactory).getCodeBuildClient();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testBatchGetBuildsExcepts() throws Exception {
        setUpBuildEnvironment();
        String error = "cannot get build";
        doThrow(new InvalidInputException(error)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testComputeTypeOverrideException() throws Exception {
        setUpBuildEnvironment();

        CodeBuilder test = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.ProjectSource.toString(),
                "1", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(),"https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(),"aws/codebuild/openjdk-8", "invalidComputeType",CacheType.NO_CACHE.toString(),"",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidComputeTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidComputeTypeError));
    }

    @Test
    public void testCacheTypeOverrideException() throws Exception {
        setUpBuildEnvironment();

        CodeBuilder test = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.ProjectSource.toString(),
                "1", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(),"https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(),"aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(),"invalidtype","",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidCacheTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidCacheTypeError));
    }

    @Test
    public void testSourceTypeOverrideException() throws Exception {
        setUpBuildEnvironment();

        CodeBuilder test = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.ProjectSource.toString(),
                "1", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "[{k, p}]", "buildspec.yml", "5", "invalidsource","https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(),"aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(),CacheType.NO_CACHE.toString(),"",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.invalidSourceTypeError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(Validation.invalidSourceTypeError));
    }

    @Test
    public void testEnvironmentTypeOverrideException() throws Exception {
        setUpBuildEnvironment();

        CodeBuilder test = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "existingProject", "sourceVersion", "", SourceControlType.ProjectSource.toString(),
                "1", ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "[{k, p}]", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(),"https://1.0.0.0.86/my_repo",
                "invalidtype","aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(),CacheType.NO_CACHE.toString(),"",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

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

        CodeBuilder cb = new CodeBuilder("keys", "id123","host", "60", "a", awsSecretKey, "",
                "us-east-1", "$foo", "$foo2-$foo3", "", SourceControlType.ProjectSource.toString(), "1",
                ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "", "buildspec.yml", "5", SourceType.GITHUB_ENTERPRISE.toString(),"https://1.0.0.0.86/my_repo",
                EnvironmentType.LINUX_CONTAINER.toString(),"aws/codebuild/openjdk-8", ComputeType.BUILD_GENERAL1_SMALL.toString(),CacheType.NO_CACHE.toString(),"",
                "arn:aws:s3:::my_bucket/certificate.pem","my_service_role","false","false");

        cb.perform(build, ws, launcher, listener);

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
