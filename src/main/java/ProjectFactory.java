/*
 *     Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 */

import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.*;

import java.util.ArrayList;
import java.util.List;

public class ProjectFactory {

    private AWSCodeBuildClient cbClient;

    public ProjectFactory(AWSCodeBuildClient cbClient) {
        this.cbClient = cbClient;
    }

    public String createProject(String projectName, String description,
                                ProjectSource source,
                                ProjectArtifacts artifacts,
                                ProjectEnvironment environment, String serviceIAMRole, String timeout,
                                String encryptionKey) throws Exception {

        ListProjectsRequest lpRequest;
        ListProjectsResult lpResult;

        List<String> projects = new ArrayList<String>();
        String nextToken = null;
        do {
            lpRequest = new ListProjectsRequest().withNextToken(nextToken);
            lpResult = cbClient.listProjects(lpRequest);
            nextToken = lpResult.getNextToken();
            projects.addAll(lpResult.getProjects());
        } while(nextToken != null);

        if(projects.contains(projectName)) {
            UpdateProjectResult upResult = cbClient.updateProject(new UpdateProjectRequest()
                    .withName(projectName)
                    .withDescription(description)
                    .withSource(source)
                    .withArtifacts(artifacts)
                    .withEnvironment(environment)
                    .withServiceRole(serviceIAMRole)
                    .withTimeoutInMinutes(Validation.parseInt(timeout))
                    .withEncryptionKey(encryptionKey));

            return upResult.getProject().getName();
        } else {
            CreateProjectResult cpResult = cbClient.createProject(new CreateProjectRequest()
                    .withName(projectName)
                    .withDescription(description)
                    .withSource(source)
                    .withArtifacts(artifacts)
                    .withEnvironment(environment)
                    .withServiceRole(serviceIAMRole)
                    .withTimeoutInMinutes(Validation.parseInt(timeout))
                    .withEncryptionKey(encryptionKey));

            return cpResult.getProject().getName();
        }
    }

}
