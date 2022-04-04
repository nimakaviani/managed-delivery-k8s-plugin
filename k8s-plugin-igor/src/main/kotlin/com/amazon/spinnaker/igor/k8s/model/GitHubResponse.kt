// Copyright 2022 Amazon.com, Inc.
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

package com.amazon.spinnaker.igor.k8s.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubTagResponse(
    var name: String = "",
    var zipball_url: String = "",
    var tarball_url: String = "",
    var node_id: String = "",
    var commit: GitHubCommit = GitHubCommit()
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubCommit(
    var sha: String = "",
    var url: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubCommitResponse(
    var sha: String = "",
    var commit: Commit = Commit(),
    var html_url: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Commit (
    var author: CommitAuthor = CommitAuthor(),
    var message: String  = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
class CommitAuthor (
    var name: String = "",
    var email: String = "",
    var date: String = ""
)