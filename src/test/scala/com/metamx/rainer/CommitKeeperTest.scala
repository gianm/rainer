/*
 * Rainer.
 * Copyright 2014 Metamarkets Group Inc.
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

package com.metamx.rainer

import com.fasterxml.jackson.databind.JsonMappingException
import com.metamx.common.scala.Jackson
import com.simple.simplespec.Spec
import com.twitter.util.Await
import org.junit.Test
import org.scala_tools.time.Imports._

class CommitKeeperTest extends Spec with RainerTests
{

  def TP(s: String) = {
    Some(Jackson.bytes(TestPayload(s)))
  }

  class Basics
  {
    @Test
    def testEmptiness() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              commits.heads must be(Map.empty[Commit.Key, Commit[TestPayload]])
              commits.get("what") must be(None)
          }
      }
    }

    @Test
    def testPublish() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val (c, theMap) = asMap(commits)
              try {
                val theCommit = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                commits.save(theCommit)
                commits.heads must be(Map("what" -> theCommit))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(theCommit))
                }
              } finally {
                Await.result(c.close())
              }
          }
      }
    }

    @Test
    def testUpdates() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val (c, theMap) = asMap(commits)
              try {
                val commit1 = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                val commit2 = Commit.create[TestPayload]("what", 2, TP("yyy"), "nobody", "nothing", new DateTime(1))
                commits.save(commit1)
                commits.save(commit2)
                evaluating {
                  // Attempt to roll back
                  commits.save(commit1)
                } must throwA[ConcurrentCommitException]
                commits.heads must be(Map("what" -> commit2))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit2))
                }
              } finally {
                Await.result(c.close())
              }
          }
      }
    }

    @Test
    def testBadSerialization() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val commitsStrict = new CommitKeeper[TestPayloadStrict](curator, "/hey")
              val (c, theMap) = asMap(commits)
              val (cs, theMapStrict) = asMap(commitsStrict)
              try {
                val commit1 = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                val commit1Strict = Commit.create[TestPayloadStrict]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                val commit2 = Commit.create[TestPayload]("what", 2, TP("xx"), "nobody", "nothing", new DateTime(1))
                val commit2Strict = Commit.create[TestPayloadStrict]("what", 2, TP("xx"), "nobody", "nothing", new DateTime(1))
                commits.save(commit1)
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit1))
                  theMapStrict.get().get("what") must be(Some(commit1Strict))
                }
                commits.get("what") must be(Some(commit1))
                evaluating {
                  commitsStrict.get("what") foreach (_.value.flatMap(_.left.toOption).foreach(throw _))
                } must throwA[JsonMappingException]

                // commitsStrict should be willing to update over this.
                commitsStrict.save(commit2Strict)
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit2))
                  theMapStrict.get().get("what") must be(Some(commit2Strict))
                }
                commits.get("what") must be(Some(commit2))
                commitsStrict.get("what") must be(Some(commit2Strict))
              } finally {
                Await.result(c.close())
                Await.result(cs.close())
              }
          }
      }
    }

    @Test
    def testSameVersionUpdates() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val (c, theMap) = asMap(commits)
              try {
                val commit1 = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                val commit2a = Commit.create[TestPayload]("what", 2, TP("yyy"), "nobody", "nothing", new DateTime(1))
                val commit2b = Commit.create[TestPayload]("what", 2, TP("yyy2"), "nobody", "nothing", new DateTime(1))
                val commit2c = Commit.create[TestPayload]("what", 2, TP("yyy"), "nobody2", "nothing", new DateTime(1))
                commits.save(commit1)
                commits.save(commit2a)
                evaluating {
                  // Attempt to roll back
                  commits.save(commit1)
                } must throwA[ConcurrentCommitException]
                evaluating {
                  // Attempt to publish bogus current versions
                  commits.save(commit2b)
                } must throwA[ConcurrentCommitException]
                evaluating {
                  // Attempt to publish bogus current versions
                  commits.save(commit2c)
                } must throwA[ConcurrentCommitException]
                // Should be able to re-publish the same thing, though
                commits.save(commit2a)
                commits.heads must be(Map("what" -> commit2a))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit2a))
                }
              } finally {
                Await.result(c.close())
              }
          }
      }
    }

    @Test
    def testInvalidValueSaves() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayloadStrict](curator, "/hey")
              val (c, theMap) = asMap(commits)
              try {
                val commit = Commit.create[TestPayloadStrict]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                commit.value.get.isLeft must be(true)
                commits.save(commit)
                commits.heads must be(Map("what" -> commit))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit))
                }
              } finally {
                Await.result(c.close())
              }
          }
      }
    }

    @Test
    def testRemoves() {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val (c, theMap) = asMap(commits)
              try {
                val commit1 = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
                val commit2 = Commit.create[TestPayload]("what", 2, None, "nobody", "nothing", new DateTime(1))
                commits.save(commit1)
                commits.save(commit1)
                commits.heads must be(Map("what" -> commit1))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit1))
                }

                // Update to empty.
                commits.save(commit2)
                commits.heads must be(Map.empty[String, Commit[TestPayload]])
                within(2.seconds) {
                  theMap.get().get("what") must be(None)
                }

                // Rollback should work. This is possibly a misfeature, but it's how things work currently.
                commits.save(commit1)
                commits.heads must be(Map("what" -> commit1))
                within(2.seconds) {
                  theMap.get().get("what") must be(Some(commit1))
                }
              } finally {
                Await.result(c.close())
              }
          }
      }
    }
  }

  class AutoPublisher
  {
    @Test
    def testSimple()
    {
      withCluster {
        cluster =>
          withCurator(cluster) {
            curator =>
              val theCommit = Commit.create[TestPayload]("what", 1, TP("xxx"), "nobody", "nothing", new DateTime(1))
              val commitMap = Map("what" -> theCommit)
              val storage = new CommitStorage[TestPayload] {
                override def start() {}

                override def stop() {}

                override def save(commit: Commit[TestPayload]) {
                  throw new UnsupportedOperationException
                }

                override def get(key: Commit.Key, version: Int) = commitMap.get(key).filter(_.version == version)

                override def get(key: Commit.Key) = commitMap.get(key)

                override def keys = commitMap.keys

                override def heads = commitMap
              }
              val commits = new CommitKeeper[TestPayload](curator, "/hey")
              val autoPublisher = commits.autoPublisher(storage, 1.second, 0.2, delay = false)
              autoPublisher.start()
              try {
                within(2.seconds) {
                  commits.heads must be(commitMap flatMap {
                    case (k, v) =>
                      val p: Option[TestPayload] = v.value.flatMap(_.right.toOption)
                      p map (_ => (k, v))
                  })
                }
              } finally {
                Await.result(autoPublisher.close())
              }
          }
      }
    }
  }

}