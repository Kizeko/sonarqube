/*
 * SonarQube
 * Copyright (C) 2009-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.alm.client.bitbucketserver;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.alm.client.ConstantTimeoutConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

public class BitbucketServerRestClientTest {
  private final MockWebServer server = new MockWebServer();
  private BitbucketServerRestClient underTest;

  @Before
  public void prepare() throws IOException {
    server.start();

    underTest = new BitbucketServerRestClient(new ConstantTimeoutConfiguration(500));
  }

  @After
  public void stopServer() throws IOException {
    server.shutdown();
  }

  @Test
  public void get_repos() {
    server.enqueue(new MockResponse()
      .setHeader("Content-Type", "application/json;charset=UTF-8")
      .setBody("{\n" +
        "  \"isLastPage\": true,\n" +
        "  \"values\": [\n" +
        "    {\n" +
        "      \"slug\": \"banana\",\n" +
        "      \"id\": 2,\n" +
        "      \"name\": \"banana\",\n" +
        "      \"project\": {\n" +
        "        \"key\": \"HOY\",\n" +
        "        \"id\": 2,\n" +
        "        \"name\": \"hoy\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"slug\": \"potato\",\n" +
        "      \"id\": 1,\n" +
        "      \"name\": \"potato\",\n" +
        "      \"project\": {\n" +
        "        \"key\": \"HEY\",\n" +
        "        \"id\": 1,\n" +
        "        \"name\": \"hey\"\n" +
        "      }\n" +
        "    }\n" +
        "  ]\n" +
        "}"));

    RepositoryList gsonBBSRepoList = underTest.getRepos(server.url("/").toString(), "token", "", "");
    assertThat(gsonBBSRepoList.isLastPage()).isTrue();
    assertThat(gsonBBSRepoList.getValues()).hasSize(2);
    assertThat(gsonBBSRepoList.getValues()).extracting(Repository::getId, Repository::getName, Repository::getSlug,
      g -> g.getProject().getId(), g -> g.getProject().getKey(), g -> g.getProject().getName())
      .containsExactlyInAnyOrder(
        tuple(2L, "banana", "banana", 2L, "HOY", "hoy"),
        tuple(1L, "potato", "potato", 1L, "HEY", "hey"));
  }

  @Test
  public void get_repo() {
    server.enqueue(new MockResponse()
      .setHeader("Content-Type", "application/json;charset=UTF-8")
      .setBody(
        "    {" +
          "      \"slug\": \"banana-slug\"," +
          "      \"id\": 2,\n" +
          "      \"name\": \"banana\"," +
          "      \"project\": {\n" +
          "        \"key\": \"HOY\"," +
          "        \"id\": 3,\n" +
          "        \"name\": \"hoy\"" +
          "      }" +
          "    }"));

    Repository repository = underTest.getRepo(server.url("/").toString(), "token", "", "");
    assertThat(repository.getId()).isEqualTo(2L);
    assertThat(repository.getName()).isEqualTo("banana");
    assertThat(repository.getSlug()).isEqualTo("banana-slug");
    assertThat(repository.getProject())
      .extracting(Project::getId, Project::getKey, Project::getName)
      .contains(3L, "HOY", "hoy");
  }

  @Test
  public void get_projects() {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json;charset=UTF-8")
        .setBody("{\n" +
            "  \"isLastPage\": true,\n" +
            "  \"values\": [\n" +
            "    {\n" +
            "      \"key\": \"HEY\",\n" +
            "      \"id\": 1,\n" +
            "      \"name\": \"hey\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"key\": \"HOY\",\n" +
            "      \"id\": 2,\n" +
            "      \"name\": \"hoy\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"));

    final ProjectList gsonBBSProjectList = underTest.getProjects(server.url("/").toString(), "token");
    assertThat(gsonBBSProjectList.getValues()).hasSize(2);
    assertThat(gsonBBSProjectList.getValues()).extracting(Project::getId, Project::getKey, Project::getName)
        .containsExactlyInAnyOrder(
            tuple(1L, "HEY", "hey"),
            tuple(2L, "HOY", "hoy"));
  }

  @Test
  public void invalid_url() {
    assertThatThrownBy(() -> BitbucketServerRestClient.buildUrl("file://wrong-url", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("url must start with http:// or https://");
  }

  @Test
  public void malformed_json() {
    server.enqueue(new MockResponse()
      .setHeader("Content-Type", "application/json;charset=UTF-8")
      .setBody(
        "I'm malformed JSON"));

    String serverUrl = server.url("/").toString();
    assertThatThrownBy(() -> underTest.getRepo(serverUrl, "token", "", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unable to contact Bitbucket server, got an unexpected response");
  }

  @Test
  public void error_handling() {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json;charset=UTF-8")
        .setResponseCode(400)
        .setBody("{\n" +
            "  \"errors\": [\n" +
            "    {\n" +
            "      \"context\": null,\n" +
            "      \"message\": \"Bad message\",\n" +
            "      \"exceptionName\": \"com.atlassian.bitbucket.auth.BadException\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"));

    String serverUrl = server.url("/").toString();
    assertThatThrownBy(() -> underTest.getRepo(serverUrl, "token", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to contact Bitbucket server");
  }

  @Test
  public void unauthorized_error() {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json;charset=UTF-8")
        .setResponseCode(401)
        .setBody("{\n" +
            "  \"errors\": [\n" +
            "    {\n" +
            "      \"context\": null,\n" +
            "      \"message\": \"Bad message\",\n" +
            "      \"exceptionName\": \"com.atlassian.bitbucket.auth.BadException\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"));

    String serverUrl = server.url("/").toString();
    assertThatThrownBy(() -> underTest.getRepo(serverUrl, "token", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid personal access token");
  }

}
