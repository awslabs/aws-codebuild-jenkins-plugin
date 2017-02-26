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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class CodeBuilderConfigurationTest extends CodeBuilderTest {

    @Test
    public void testConfigAllNull() throws IOException, ExecutionException, InterruptedException {
        CodeBuilder test = new CodeBuilder(null, null, null, null, null, null, null, null);
        test.perform(build, ws, launcher, listener);
    }

    @Test
    public void testConfigAllBlank() throws IOException, ExecutionException, InterruptedException {
        CodeBuilder test = new CodeBuilder("", "", "", "", "", "", "", "");

        test.setAwsClientInitFailureMessage(""); //hide failure from trying to initialize client factory.
        test.perform(build, ws, launcher, listener);
        assert(log.toString().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testInvalidAWSCredentials() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new com.amazonaws.AmazonServiceException("The security token included in " +
                        "the request is invalid"));

        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
    }

    @Test
    public void testInvalidGitLocation() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("service only supports https protocol for GIT endpoints"));

        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
    }

    @Test
    public void testInvalidEnvARN() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("The provided ARN(" + "123" + ") is invalid."));

        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
    }
}
