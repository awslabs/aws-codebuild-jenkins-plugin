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

import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectFactoryTest {

    AWSCodeBuildClient mockCBClient = mock(AWSCodeBuildClient.class);
    ListProjectsResult mockLPResult = mock(ListProjectsResult.class);

    @Test
    public void testCreateProjectUpdate() throws Exception {
        List<String> firstList = new ArrayList<String>();
        firstList.add("project1");
        firstList.add("project2");

        when(mockLPResult.getProjects()).thenReturn(firstList);
        when(mockCBClient.listProjects(new ListProjectsRequest())).thenReturn(mockLPResult);

        doThrow(new InvalidInputException("bad")).when(mockCBClient).createProject(any(CreateProjectRequest.class));
        when(mockCBClient.updateProject(any(UpdateProjectRequest.class)))
                .thenReturn(new UpdateProjectResult().withProject(new Project()));

        ProjectFactory f = new ProjectFactory(mockCBClient);
        f.createProject("project1", "", null, null, null, "", "", "");
    }

    @Test
    public void testCreateProjectUpdateListMultipleProjects() throws Exception {
        List<String> firstList = new ArrayList<String>();
        firstList.add("project1");
        firstList.add("project2");
        List<String> secondList = new ArrayList<String>();
        secondList.add("project3");
        secondList.add("project4");

        ListProjectsResult mockLPResult2 = mock(ListProjectsResult.class);
        when(mockLPResult2.getProjects()).thenReturn(secondList);

        String t = "token";
        when(mockLPResult.getProjects()).thenReturn(firstList);
        when(mockLPResult.getNextToken()).thenReturn(t);
        when(mockCBClient.listProjects(new ListProjectsRequest())).thenReturn(mockLPResult);
        when(mockCBClient.listProjects(new ListProjectsRequest().withNextToken(t))).thenReturn(mockLPResult2);

        doThrow(new InvalidInputException("bad")).when(mockCBClient).createProject(any(CreateProjectRequest.class));
        when(mockCBClient.updateProject(any(UpdateProjectRequest.class)))
                .thenReturn(new UpdateProjectResult().withProject(new Project()));

        ProjectFactory f = new ProjectFactory(mockCBClient);
        f.createProject("project4", "", null, null, null, "", "", "");
    }

    @Test
    public void testCreateProjectNew() throws Exception {
        List<String> firstList = new ArrayList<String>();
        firstList.add("project1");
        firstList.add("project2");

        when(mockLPResult.getProjects()).thenReturn(firstList);
        when(mockCBClient.listProjects(new ListProjectsRequest())).thenReturn(mockLPResult);

        doThrow(new InvalidInputException("bad")).when(mockCBClient).updateProject(any(UpdateProjectRequest.class));
        when(mockCBClient.createProject(any(CreateProjectRequest.class)))
                .thenReturn(new CreateProjectResult().withProject(new Project()));

        ProjectFactory f = new ProjectFactory(mockCBClient);
        f.createProject("project3", "", null, null, null, "", "", "");
    }
}
