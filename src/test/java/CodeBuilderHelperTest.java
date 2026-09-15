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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import software.amazon.awssdk.services.codebuild.model.EnvironmentVariable;
import software.amazon.awssdk.services.codebuild.model.EnvironmentVariableType;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.codebuild.model.Build;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CodeBuilderHelperTest extends CodeBuilderTest {

    EnvironmentVariableType evType = EnvironmentVariableType.PLAINTEXT;

    @Test
    public void TestGenerateS3URLNull() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL(null, null).isEmpty());
    }

    @Test
    public void TestGenerateS3URLEmpty() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL("", "").isEmpty());
    }

    @Test
    public void TestGenerateS3URLNoArtifacts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL("NO_ARTIFACTS", "").isEmpty());
    }

    @Test
    public void TestGenerateS3URL() throws Exception {
        setUpBuildEnvironment();
        String location = "bucket1";
        String type = "S3";
        CodeBuilder cb = createDefaultCodeBuilder();
        String result = cb.generateS3ArtifactURL(type, location);
        assert(result.equals("https://s3.console.aws.amazon.com/s3/buckets/" + location));
    }

    @Test
    public void TestGenerateS3URLNoOverride() throws Exception {
        setUpBuildEnvironment();
        String location = "bucket1";
        CodeBuilder cb = createDefaultCodeBuilder();
        String result = cb.generateS3ArtifactURL("", location);
        assert(result.equals("https://s3.console.aws.amazon.com/s3/buckets/" + location));
    }

    @Test
    public void TestMapEnvVarsEmpty() throws InvalidInputException {
        String evs = "";
        CodeBuilder.mapEnvVariables(evs, evType);
    }

    @Test
    public void TestMapEnvVarsNull() throws InvalidInputException {
        CodeBuilder.mapEnvVariables(null, evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsEmptyBrackets() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsNestedEmptyBrackets() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsNestedEmptyBracketsWithComma() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{,}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsSingleNameEmpty() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{,value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsSingleValueEmpty() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name,}]", evType);
    }

    @Test
    public void TestMapEnvVarsSingle() throws InvalidInputException {
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{name, value}]", evType);
        assert(result.size() == 1);
        List<EnvironmentVariable> evs = new ArrayList<>(result);
        assert(evs.get(0).name().equals("name"));
        assert(evs.get(0).value().equals("value"));
    }

    @Test
    public void TestMapEnvVarsSingleWithWhitespace() throws InvalidInputException {
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("  [{   name, value \n} \t] ", evType);
        assert(result.size() == 1);
        List<EnvironmentVariable> evs = new ArrayList<>(result);
        assert(evs.get(0).name().equals("name"));
        assert(evs.get(0).value().equals("value"));
    }

    @Test
    public void TestMapEnvVarsTwo() throws InvalidInputException {
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{name, value}, {name2, value2}]", evType);
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name("name").value("value").type(evType).build();
        EnvironmentVariable ev2 = EnvironmentVariable.builder().name("name2").value("value2").type(evType).build();
        assert(result.size() == 2);
        assert(result.contains(ev1));
        assert(result.contains(ev2));
    }

    @Test
    public void TestMapEnvVarsMultiple() throws InvalidInputException {
        Collection<EnvironmentVariable> result =
                CodeBuilder.mapEnvVariables("[{name, value}, {name2, value2}, {key, val}, {k2, v2}]", evType);
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name("name").value("value").type(evType).build();
        EnvironmentVariable ev2 = EnvironmentVariable.builder().name("name2").value("value2").type(evType).build();
        EnvironmentVariable ev3 = EnvironmentVariable.builder().name("key").value("val").type(evType).build();
        EnvironmentVariable ev4 = EnvironmentVariable.builder().name("k2").value("v2").type(evType).build();
        assert(result.size() == 4);
        assert(result.contains(ev1));
        assert(result.contains(ev2));
        assert(result.contains(ev3));
        assert(result.contains(ev4));
    }

    @Test
    public void TestMapEnvVarsMultipleWhitespace() throws InvalidInputException {
        Collection<EnvironmentVariable> result =
                CodeBuilder.mapEnvVariables("\n [{ name   , value}    , { name2\t, value2}  ,{  key, val ue},  {ke y, value }]", evType);
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name("name").value("value").type(evType).build();
        EnvironmentVariable ev2 = EnvironmentVariable.builder().name("name2").value("value2").type(evType).build();
        EnvironmentVariable ev3 = EnvironmentVariable.builder().name("key").value("val ue").type(evType).build();
        EnvironmentVariable ev4 = EnvironmentVariable.builder().name("ke y").value("value").type(evType).build();
        assert(result.size() == 4);
        assert(result.contains(ev1));
        assert(result.contains(ev2));
        assert(result.contains(ev3));
        assert(result.contains(ev4));
    }

    @Test
    public void TestMapEnvVarWithWhitespaceInKeyAndValue() throws InvalidInputException {
        Collection<EnvironmentVariable> result =
                CodeBuilder.mapEnvVariables("[{ n a m e   , v a l u e }]", evType);
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name("n a m e").value("v a l u e").type(evType).build();
        assert(result.size() == 1);
        assert(result.contains(ev1));
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value}, bad{name2, value2}, {key, val}, {k2, v2}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid2() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[name, value]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid3() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[name{name,, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid4() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, anem, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid5() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, {name, value}, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid8() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value} name, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid6() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value} {name, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid7() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value},,{name, value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid9() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value},{name, value, }]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid10() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value},}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid11() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, value,}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid12() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{name, ,value}]", evType);
    }

    @Test(expected=InvalidInputException.class)
    public void TestMapEnvVarsInvalid13() throws InvalidInputException {
        CodeBuilder.mapEnvVariables("[{,name value,}]", evType);
    }

    @Test
    public void TestMapEnvVarsComma1() throws InvalidInputException {
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name("na,me").value("value").type(evType).build();
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{na\\,me, value}]", evType);
        assert(result.size() == 1);
        assert(result.contains(ev1));
    }

    @Test
    public void TestMapEnvVarsComma2() throws InvalidInputException {
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name(",").value("value").type(evType).build();
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{\\,, value}]", evType);
        assert(result.size() == 1);
        assert(result.contains(ev1));
    }

    @Test
    public void TestMapEnvVarsComma3() throws InvalidInputException {
        EnvironmentVariable ev1 = EnvironmentVariable.builder().name(",").value(",").type(evType).build();
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{\\,, \\,}]", evType);
        assert(result.size() == 1);
        assert(result.contains(ev1));
    }

    @Test
    public void TestCheckJenkinsSourceOverrides() throws InvalidInputException {
        assert(CodeBuilderValidation.checkJenkinsSourceOverrides("S3", "location"));
        assert(!CodeBuilderValidation.checkJenkinsSourceOverrides("type", "location"));
        assert(!CodeBuilderValidation.checkJenkinsSourceOverrides("S3", ""));
        assert(!CodeBuilderValidation.checkJenkinsSourceOverrides("", "location"));
        assert(!CodeBuilderValidation.checkJenkinsSourceOverrides("", ""));
    }

    @Test
    public void TestUpdateDashboardNullArtifacts() throws Exception {
        CodeBuilder cb = createDefaultCodeBuilder();
        CloudWatchMonitor monitor = mock(CloudWatchMonitor.class);
        CodeBuildAction action = mock(CodeBuildAction.class);
        when(action.getCloudWatchLogsURL()).thenReturn("");
        Build b = Build.builder().build();
        cb.updateDashboard(b, action, monitor, listener);
    }

}
