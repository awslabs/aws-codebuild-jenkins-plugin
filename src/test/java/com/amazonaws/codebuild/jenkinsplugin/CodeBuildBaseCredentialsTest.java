/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */
package com.amazonaws.codebuild.jenkinsplugin;

import com.cloudbees.plugins.credentials.CredentialsScope;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CodeBuildBaseCredentialsTest {


    @Test
    public void truncateKeepsFirst178Characters() {
        String msg = "x".repeat(300);
        String out = CodeBuildBaseCredentials.DescriptorImpl.truncateErrorMessage(msg);
        assertEquals("must cap the message at 178 chars, keeping the FIRST 178", 178, out.length());
        assertEquals(msg.substring(0, 178), out);
    }

    @Test
    public void truncateNullMessageDoesNotThrow() {
        assertNotNull(CodeBuildBaseCredentials.DescriptorImpl.truncateErrorMessage(null));
    }


    @Test(timeout = 5000)
    public void resolveCredentialsIsThreadSafe() throws Exception {
        final CodeBuildBaseCredentials creds = new CodeBuildBaseCredentials(
                CredentialsScope.GLOBAL, "id", "desc", "AKIAEXAMPLE", "secretExample",
                "", "", "arn:aws:iam::123456789012:role/role", "");

        Field roleCredentialsField = CodeBuildBaseCredentials.class.getDeclaredField("roleCredentials");
        roleCredentialsField.setAccessible(true);
        roleCredentialsField.set(creds, Credentials.builder()
                .accessKeyId("AK").secretAccessKey("SK").sessionToken("ST")
                .expiration(Instant.now().plusSeconds(1800))
                .build());

        final int threads = 8;
        final int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Future<?>[] futures = new Future<?>[threads];
        for (int t = 0; t < threads; t++) {
            futures[t] = pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    try {
                        AwsCredentials c = creds.resolveCredentials();
                        if (c == null) {
                            throw new AssertionError("resolveCredentials returned null");
                        }
                    } catch (Throwable e) {
                        firstFailure.compareAndSet(null, e);
                        return;
                    }
                }
            });
        }
        for (Future<?> f : futures) {
            f.get(4, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        if (firstFailure.get() != null) {
            throw new AssertionError("resolveCredentials failed under concurrency", firstFailure.get());
        }
        assertTrue(true);
    }
}
