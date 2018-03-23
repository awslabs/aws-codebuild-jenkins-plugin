/*
 *     Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package enums;

public enum CodeBuildRegions {

    IAD("us-east-1"),
    CMH("us-east-2"),
    SFO("us-west-1"),
    PDX("us-west-2"),
    YUL("ca-central-1"),
    NRT("ap-northeast-1"),
    ICN("ap-northeast-2"),
    SIN("ap-southeast-1"),
    SYD("ap-southeast-2"),
    BOM("ap-south-1"),
    DUB("eu-west-1"),
    LHR("eu-west-2"),
    CDG("eu-west-3"),
    FRA("eu-central-1"),
    GRU("sa-east-1");

    private String value;

    CodeBuildRegions(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value;
    }
}
