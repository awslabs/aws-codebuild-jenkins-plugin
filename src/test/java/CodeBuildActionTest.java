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

import com.amazonaws.services.codebuild.model.BuildPhase;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;

public class CodeBuildActionTest {

    private Run<?, ?> build = mock(AbstractBuild.class);
    CodeBuildAction action;

    @Before
    public void SetUp() {
        action = new CodeBuildAction(build);
    }

    @Test
    public void testFormatPhaseHistoryOneTransition() {
        List<BuildPhase> l = new ArrayList<BuildPhase>();
        l.add(new BuildPhase().withPhaseType("p")
            .withStartTime(new Date(0)));

        action.setPhases(l);
        List<BuildPhase> r = action.getPhases();
        assert(r.size() == 1);
        assert(r.get(0).getPhaseType().equals("p"));
        assert(r.get(0).getPhaseStatus().equals("IN PROGRESS"));
        assert(r.get(0).getDurationInSeconds().equals(0L));
    }

    @Test
    public void testFormatPhaseHistoryMultipleTransition() {
        List<BuildPhase> l = new ArrayList<BuildPhase>();
        l.add(new BuildPhase().withPhaseType("p").withPhaseStatus("s")
                .withStartTime(new Date(0)).withDurationInSeconds(2L));
        l.add(new BuildPhase().withPhaseType("b")
                .withStartTime(new Date(2)));

        action.setPhases(l);
        List<BuildPhase> r = action.getPhases();
        assert(r.size() == 2);
        assert(r.get(0).getPhaseType().equals("p"));
        assert(r.get(0).getPhaseStatus().equals("s"));
        assert(r.get(0).getDurationInSeconds().equals(2L));
        assert(r.get(1).getPhaseType().equals("b"));
        assert(r.get(1).getPhaseStatus().equals("IN PROGRESS"));
        assert(r.get(1).getDurationInSeconds().equals(0L));
    }

    @Test
    public void testFormatPhaseHistoryFinalTransition() {
        List<BuildPhase> l = new ArrayList<BuildPhase>();
        l.add(new BuildPhase().withPhaseType("p").withPhaseStatus("s")
                .withStartTime(new Date(0)).withDurationInSeconds(2L));
        l.add(new BuildPhase().withPhaseType("b").withPhaseStatus("s")
                .withStartTime(new Date(2)).withDurationInSeconds(2L));
        l.add(new BuildPhase().withPhaseType("COMPLETED")
                .withStartTime(new Date(4)));

        action.setPhases(l);
        List<BuildPhase> r = action.getPhases();
        assert(r.size() == 3);
        assert(r.get(0).getPhaseType().equals("p"));
        assert(r.get(0).getPhaseStatus().equals("s"));
        assert(r.get(0).getDurationInSeconds().equals(2L));
        assert(r.get(1).getPhaseType().equals("b"));
        assert(r.get(1).getPhaseStatus().equals("s"));
        assert(r.get(1).getDurationInSeconds().equals(2L));
        assert(r.get(2).getPhaseType().equals("COMPLETED"));
        assert(r.get(2).getPhaseStatus().equals("SUCCEEDED"));
        assert(r.get(2).getDurationInSeconds().equals(0L));
    }
}
