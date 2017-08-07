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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import hudson.model.TaskListener;

public class LoggingHelper {

    public static void log(final TaskListener listener, String message) {
        log(listener, message, null);
    }

    public static void log(final TaskListener listener, String message, String secondary) {
        String completeMessage;
        if(secondary == null || secondary.isEmpty()) {
            completeMessage = "[AWS CodeBuild Plugin] " + message;
        } else {
            completeMessage = "[AWS CodeBuild Plugin] " + message + "\n\t> " + secondary;
        }

        if(listener == null) {
            System.out.println(message);
        } else {
            listener.getLogger().println(completeMessage);
        }
    }
}
