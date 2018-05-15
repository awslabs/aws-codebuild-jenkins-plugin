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

import com.amazonaws.services.codebuild.model.EnvironmentVariable;
import com.amazonaws.services.codebuild.model.EnvironmentVariableType;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import hudson.util.Secret;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({CodeBuilder.class, Secret.class})
public class CodeBuilderHelperTest extends CodeBuilderTest {

    EnvironmentVariableType evType = EnvironmentVariableType.PLAINTEXT;

    @Test
    public void TestGenerateS3URLNull() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL(null, null, null).isEmpty());
    }

    @Test
    public void TestGenerateS3URLEmpty() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL("", "", "").isEmpty());
    }

    @Test
    public void TestGenerateS3URL() throws Exception {
        setUpBuildEnvironment();
        String baseURL = "https://url.com/";
        String location = "bucket1";
        String type = "S3";
        CodeBuilder cb = createDefaultCodeBuilder();
        String result = cb.generateS3ArtifactURL(baseURL, location, type);
        assert(result.equals(baseURL + location));
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
        assert(evs.get(0).getName().equals("name"));
        assert(evs.get(0).getValue().equals("value"));
    }

    @Test
    public void TestMapEnvVarsSingleWithWhitespace() throws InvalidInputException {
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("  [{   name, value \n} \t] ", evType);
        assert(result.size() == 1);
        List<EnvironmentVariable> evs = new ArrayList<>(result);
        assert(evs.get(0).getName().equals("name"));
        assert(evs.get(0).getValue().equals("value"));
    }

    @Test
    public void TestMapEnvVarsTwo() throws InvalidInputException {
        Collection<EnvironmentVariable> result = CodeBuilder.mapEnvVariables("[{name, value}, {name2, value2}]", evType);
        EnvironmentVariable ev1 = new EnvironmentVariable().withName("name").withValue("value").withType(evType);
        EnvironmentVariable ev2 = new EnvironmentVariable().withName("name2").withValue("value2").withType(evType);
        assert(result.size() == 2);
        assert(result.contains(ev1));
        assert(result.contains(ev2));
    }

    @Test
    public void TestMapEnvVarsMultiple() throws InvalidInputException {
        Collection<EnvironmentVariable> result =
                CodeBuilder.mapEnvVariables("[{name, value}, {name2, value2}, {key, val}, {k2, v2}]", evType);
        EnvironmentVariable ev1 = new EnvironmentVariable().withName("name").withValue("value").withType(evType);
        EnvironmentVariable ev2 = new EnvironmentVariable().withName("name2").withValue("value2").withType(evType);
        EnvironmentVariable ev3 = new EnvironmentVariable().withName("key").withValue("val").withType(evType);
        EnvironmentVariable ev4 = new EnvironmentVariable().withName("k2").withValue("v2").withType(evType);
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
        EnvironmentVariable ev1 = new EnvironmentVariable().withName("name").withValue("value").withType(evType);
        EnvironmentVariable ev2 = new EnvironmentVariable().withName("name2").withValue("value2").withType(evType);
        EnvironmentVariable ev3 = new EnvironmentVariable().withName("key").withValue("val ue").withType(evType);
        EnvironmentVariable ev4 = new EnvironmentVariable().withName("ke y").withValue("value").withType(evType);
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
        EnvironmentVariable ev1 = new EnvironmentVariable().withName("n a m e").withValue("v a l u e").withType(evType);
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

}
