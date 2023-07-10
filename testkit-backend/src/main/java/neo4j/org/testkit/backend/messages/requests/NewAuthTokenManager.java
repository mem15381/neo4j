/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package neo4j.org.testkit.backend.messages.requests;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.Getter;
import lombok.Setter;
import neo4j.org.testkit.backend.AuthTokenUtil;
import neo4j.org.testkit.backend.TestkitState;
import neo4j.org.testkit.backend.messages.responses.AuthTokenManager;
import neo4j.org.testkit.backend.messages.responses.AuthTokenManagerGetAuthRequest;
import neo4j.org.testkit.backend.messages.responses.AuthTokenManagerOnAuthExpiredRequest;
import neo4j.org.testkit.backend.messages.responses.AuthTokenManagerOnAuthExpiredRequest.AuthTokenManagerOnAuthExpiredRequestBody;
import neo4j.org.testkit.backend.messages.responses.TestkitCallback;
import neo4j.org.testkit.backend.messages.responses.TestkitResponse;
import org.neo4j.driver.AuthToken;

@Setter
@Getter
public class NewAuthTokenManager extends AbstractBasicTestkitRequest {
    private NewAuthTokenManagerBody data;

    @Override
    protected TestkitResponse processAndCreateResponse(TestkitState testkitState) {
        var id = testkitState.newId();
        testkitState.addAuthProvider(id, new TestkitAuthTokenManager(id, testkitState));
        return neo4j.org.testkit.backend.messages.responses.AuthTokenManager.builder()
                .data(AuthTokenManager.AuthTokenManagerBody.builder().id(id).build())
                .build();
    }

    private record TestkitAuthTokenManager(String authProviderId, TestkitState testkitState)
            implements org.neo4j.driver.AuthTokenManager {
        @Override
        public CompletionStage<AuthToken> getToken() {
            var callbackId = testkitState.newId();

            var callback = AuthTokenManagerGetAuthRequest.builder()
                    .data(AuthTokenManagerGetAuthRequest.AuthTokenProviderRequestBody.builder()
                            .id(callbackId)
                            .authTokenManagerId(authProviderId)
                            .build())
                    .build();

            var callbackStage = dispatchTestkitCallback(testkitState, callback);
            AuthTokenManagerGetAuthCompleted resolutionCompleted;
            try {
                resolutionCompleted = (AuthTokenManagerGetAuthCompleted)
                        callbackStage.toCompletableFuture().get();
            } catch (Exception e) {
                throw new RuntimeException("Unexpected failure during Testkit callback", e);
            }

            var authToken =
                    AuthTokenUtil.parseAuthToken(resolutionCompleted.getData().getAuth());
            return CompletableFuture.completedFuture(authToken);
        }

        @Override
        public void onExpired(AuthToken authToken) {
            var callbackId = testkitState.newId();

            var callback = AuthTokenManagerOnAuthExpiredRequest.builder()
                    .data(AuthTokenManagerOnAuthExpiredRequestBody.builder()
                            .id(callbackId)
                            .authTokenManagerId(authProviderId)
                            .auth(AuthTokenUtil.parseAuthToken(authToken))
                            .build())
                    .build();

            var callbackStage = dispatchTestkitCallback(testkitState, callback);
            try {
                callbackStage.toCompletableFuture().get();
            } catch (Exception e) {
                throw new RuntimeException("Unexpected failure during Testkit callback", e);
            }
        }

        private CompletionStage<TestkitCallbackResult> dispatchTestkitCallback(
                TestkitState testkitState, TestkitCallback response) {
            var future = new CompletableFuture<TestkitCallbackResult>();
            testkitState.getCallbackIdToFuture().put(response.getCallbackId(), future);
            testkitState.getResponseWriter().accept(response);
            return future;
        }
    }

    @Setter
    @Getter
    public static class NewAuthTokenManagerBody {}
}
