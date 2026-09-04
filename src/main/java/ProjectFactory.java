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

import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;

import java.util.ArrayList;
import java.util.List;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;

public class ProjectFactory {

    private CodeBuildClient cbClient;

    public ProjectFactory(CodeBuildClient cbClient) {
        this.cbClient = cbClient;
    }

    public String createProject(String projectName, String description,
                                ProjectSource source,
                                ProjectArtifacts artifacts,
                                ProjectEnvironment environment, String serviceIAMRole, String timeout,
                                String encryptionKey) throws Exception {

        ListProjectsRequest lpRequest;
        ListProjectsResponse lpResult;

        List<String> projects = new ArrayList<String>();
        String nextToken = null;
        do {
            lpRequest = ListProjectsRequest.builder().nextToken(nextToken).build();
            lpResult = cbClient.listProjects(lpRequest);
            nextToken = lpResult.nextToken();
            projects.addAll(lpResult.projects());
        } while(nextToken != null);

        if(projects.contains(projectName)) {
            UpdateProjectResponse upResult = cbClient.updateProject(UpdateProjectRequest.builder()
                    .name(projectName)
                    .description(description)
                    .source(source)
                    .artifacts(artifacts)
                    .environment(environment)
                    .serviceRole(serviceIAMRole)
                    .timeoutInMinutes(parseInt(timeout))
                    .encryptionKey(encryptionKey)
                    .build());

            return upResult.project().name();
        } else {
            CreateProjectResponse cpResult = cbClient.createProject(CreateProjectRequest.builder()
                    .name(projectName)
                    .description(description)
                    .source(source)
                    .artifacts(artifacts)
                    .environment(environment)
                    .serviceRole(serviceIAMRole)
                    .timeoutInMinutes(parseInt(timeout))
                    .encryptionKey(encryptionKey)
                    .build());

            return cpResult.project().name();
        }
    }

}
