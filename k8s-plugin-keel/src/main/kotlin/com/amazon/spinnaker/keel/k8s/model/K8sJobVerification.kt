// Copyright 2021 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.VERIFICATION_K8S_JOB
import com.netflix.spinnaker.keel.api.Verification
import java.security.MessageDigest

data class K8sJobVerification(
    val account: String,
    val manifest: Map<String, Any>
) : Verification {

    override val type = VERIFICATION_K8S_JOB
    // hashcode will be different if field values are different.
    override val id by lazy {
        "$account/${Integer.toHexString(hashCode())}"
    }
}