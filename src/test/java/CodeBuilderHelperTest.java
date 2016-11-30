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

import org.junit.Test;

public class CodeBuilderHelperTest extends CodeBuilderTest {

    @Test
    public void TestGenerateS3URLNull() throws Exception {
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL(null, null, null).isEmpty());
    }

    @Test
    public void TestGenerateS3URLEmpty() throws Exception {
        CodeBuilder cb = createDefaultCodeBuilder();
        assert(cb.generateS3ArtifactURL("", "", "").isEmpty());
    }

    @Test
    public void TestGenerateS3URL() throws Exception {
        String baseURL = "https://url.com/";
        String location = "bucket1";
        String type = "S3";
        CodeBuilder cb = createDefaultCodeBuilder();
        String result = cb.generateS3ArtifactURL(baseURL, location, type);
        assert(result.equals(baseURL + "region=" + cb.getRegion() + "#" +
            "&bucket=bucket1"));
    }

}
