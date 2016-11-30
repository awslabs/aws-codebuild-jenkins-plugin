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

import com.amazonaws.services.codebuild.model.InvalidInputException;
import org.junit.Test;

public class AWSClientFactoryTest {

    private static final String region = "us-east-1";

    @Test(expected=InvalidInputException.class)
    public void testInvalidConfigNull() {
        new AWSClientFactory(null, null, null, null, null);
    }

    @Test(expected=InvalidInputException.class)
    public void testInvalidConfigEmptyKeys() {
        new AWSClientFactory("", "", "", "", "region");
    }

    @Test(expected=InvalidInputException.class)
    public void testInvalidProxyPort() {
        new AWSClientFactory("host", "-2", "", "", region);
    }

}
