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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors. Copyright 2004-2011 Oracle Corporation. Copyright 2010 Srikanth Reddy Lingala.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.codebuild.model.ProjectArtifacts;
import software.amazon.awssdk.services.codebuild.model.ProjectSource;
import software.amazon.awssdk.services.codebuild.model.ProjectSourceVersion;
import software.amazon.awssdk.services.codebuild.model.SourceAuth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    /*
        Returns the bucket name given S3 source location informaton.
        The given string can be in ARN format or <bucket>/<key> format, so handle both.
     */
    public static String getS3BucketFromObjectArn(String s3ObjectString) {
        Matcher stringRegex = Pattern.compile("(arn:(aws|aws-cn):s3:::)?([^/]+)(/.*)*").matcher(s3ObjectString);
        stringRegex.find();
        return stringRegex.group(3);
    }

    public static String getS3KeyFromObjectArn(String s3ObjectArn) {
        int index = s3ObjectArn.indexOf('/');
        if (index < 0) {
            return "";
        }

        return s3ObjectArn.substring(index + 1);
    }

    public static String formatStringWithEllipsis(String s, int length) {
        return s.substring(0, length) + "...";
    }

    public static List parseDataList(String json, Class dataType) {
        if(json == null || json.isEmpty()) {
            return Collections.emptyList();
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            if (dataType == ProjectSourceVersion.class) {
                List<ProjectSourceVersionMirror> mirrors = mapper.readValue(json,
                        mapper.getTypeFactory().constructCollectionType(List.class, ProjectSourceVersionMirror.class));
                List<ProjectSourceVersion> result = new ArrayList<>();
                for (ProjectSourceVersionMirror m : mirrors) {
                    result.add(m.toModel());
                }
                return result;
            } else if (dataType == ProjectSource.class) {
                List<ProjectSourceMirror> mirrors = mapper.readValue(json,
                        mapper.getTypeFactory().constructCollectionType(List.class, ProjectSourceMirror.class));
                List<ProjectSource> result = new ArrayList<>();
                for (ProjectSourceMirror m : mirrors) {
                    result.add(m.toModel());
                }
                return result;
            } else if (dataType == ProjectArtifacts.class) {
                List<ProjectArtifactsMirror> mirrors = mapper.readValue(json,
                        mapper.getTypeFactory().constructCollectionType(List.class, ProjectArtifactsMirror.class));
                List<ProjectArtifacts> result = new ArrayList<>();
                for (ProjectArtifactsMirror m : mirrors) {
                    result.add(m.toModel());
                }
                return result;
            } else {
                return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, dataType));
            }
        } catch (IOException e) {
            throw InvalidInputException.builder().message(e.getMessage()).build();
        }
    }

    public static void ensureFileExists(File file) throws IOException {
        File dir = file.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory " + dir.getAbsolutePath());
            }
        }
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException("Failed to create file " + file.getAbsolutePath());
            }
        }
    }


    private static final class ProjectSourceVersionMirror {
        public String sourceIdentifier;
        public String sourceVersion;

        ProjectSourceVersion toModel() {
            return ProjectSourceVersion.builder()
                    .sourceIdentifier(sourceIdentifier)
                    .sourceVersion(sourceVersion)
                    .build();
        }
    }

    private static final class SourceAuthMirror {
        public final String type;
        public final String resource;

        @JsonCreator
        SourceAuthMirror(@JsonProperty("type") String type,
                         @JsonProperty("resource") String resource) {
            this.type = type;
            this.resource = resource;
        }

        SourceAuth toModel() {
            return SourceAuth.builder().type(type).resource(resource).build();
        }
    }

    private static final class ProjectSourceMirror {
        public String type;
        public String location;
        public Integer gitCloneDepth;
        public String buildspec;
        public Boolean reportBuildStatus;
        public Boolean insecureSsl;
        public String sourceIdentifier;
        public SourceAuthMirror auth;

        ProjectSource toModel() {
            return ProjectSource.builder()
                    .type(type)
                    .location(location)
                    .gitCloneDepth(gitCloneDepth)
                    .buildspec(buildspec)
                    .reportBuildStatus(reportBuildStatus)
                    .insecureSsl(insecureSsl)
                    .sourceIdentifier(sourceIdentifier)
                    .auth(auth == null ? null : auth.toModel())
                    .build();
        }
    }

    private static final class ProjectArtifactsMirror {
        public String type;
        public String location;
        public String path;
        public String namespaceType;
        public String name;
        public String packaging;
        public Boolean overrideArtifactName;
        public Boolean encryptionDisabled;
        public String artifactIdentifier;

        ProjectArtifacts toModel() {
            return ProjectArtifacts.builder()
                    .type(type)
                    .location(location)
                    .path(path)
                    .namespaceType(namespaceType)
                    .name(name)
                    .packaging(packaging)
                    .overrideArtifactName(overrideArtifactName)
                    .encryptionDisabled(encryptionDisabled)
                    .artifactIdentifier(artifactIdentifier)
                    .build();
        }
    }
}
