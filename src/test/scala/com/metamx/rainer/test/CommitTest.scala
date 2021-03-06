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
package com.metamx.rainer.test

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.google.common.base.Charsets
import com.metamx.common.scala.Jackson
import com.metamx.rainer.test.helper.{TestPayloadStrict, TestPayload}
import com.metamx.rainer.{CommitMetadata, Commit}
import com.simple.simplespec.Matchers
import org.joda.time.DateTime
import org.junit.Test

class CommitTest extends Matchers
{

  def TP(s: String) = {
    Some(Jackson.bytes(TestPayload(s)))
  }

  @Test
  def testToStringSimple()
  {
    val commit = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    commit.toString must be("Commit(hey,1,[11 bytes],nobody,nothing,1970-01-01T00:00:00.002Z)")
  }

  @Test
  def testToStringSimpleCreatedFromActualObject()
  {
    val commit = Commit.fromValue("hey", 1, Some(TestPayload("lol")), "nobody", "nothing", new DateTime(2))
    commit.toString must be("Commit(hey,1,[11 bytes],nobody,nothing,1970-01-01T00:00:00.002Z)")
  }

  @Test
  def testToStringEmpty()
  {
    val commit = Commit[TestPayload]("hey", 1, None, "nobody", "nothing", new DateTime(2))
    commit.toString must be("Commit(hey,1,[empty],nobody,nothing,1970-01-01T00:00:00.002Z)")
  }

  @Test
  def testEqualitySimple()
  {
    val commit = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2a = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2b = Commit[TestPayload]("hez", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2c = Commit[TestPayload]("hey", 2, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2d = Commit[TestPayload]("hey", 1, TP("loo"), "nobody", "nothing", new DateTime(2))
    val commit2e = Commit[TestPayload]("hey", 1, TP("lol"), "nobodz", "nothing", new DateTime(2))
    val commit2f = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothinz", new DateTime(2))
    val commit2g = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(3))
    val commit2h = Commit[TestPayload]("hey", 1, None, "nobody", "nothing", new DateTime(3))
    commit must be(commit2a)
    commit.hashCode() must be(commit2a.hashCode())
    Seq(commit2b, commit2c, commit2d, commit2e, commit2f, commit2g, commit2h) foreach {
      other =>
        commit must not(be(other))
    }
  }

  @Test
  def testPatternMatchingSimple()
  {
    val commit = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val meta = commit match {
      case Commit(m, p) => m
    }
    val value = commit match {
      case Commit(m, Some(Right(TestPayload(s)))) => s
    }
    meta must be(CommitMetadata("hey", 1, "nobody", "nothing", new DateTime(2), false))
    value must be("lol")
  }

  @Test
  def testSerializationSimple()
  {
    val commit = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2 = Commit.deserializeOrThrow[TestPayload](Commit.serialize(commit))
    commit2 must be(commit)
    commit2.hashCode() must be(commit.hashCode())
    commit2.value.flatMap(_.right.toOption) must be(Some(TestPayload("lol")))
    commit2.valueOption must be(Some(TestPayload("lol")))
  }

  @Test
  def testErrorSerialization()
  {
    val bytes = """cant deserialize!!""".getBytes
    val commit: Commit[TestPayload] =
      Commit[TestPayload]("hey", 1, Some(bytes), "nobody", "nothing", new DateTime(2))
    val serialized = Commit.serialize(commit)
    val commit2 = Commit.deserializeOrThrow[TestPayload](serialized)
    evaluating {
      commit2.value.flatMap(_.left.toOption).foreach(throw _)
    } must throwA[JsonParseException]("""Unrecognized token 'cant'.*""".r)
    commit2.valueOption must be(None)
  }

  @Test
  def testBadSerializedValue()
  {
    val commit = Commit[TestPayload]("hey", 1, TP("lol"), "nobody", "nothing", new DateTime(2))
    val commit2 = Commit.deserializeOrThrow[TestPayloadStrict](Commit.serialize(commit))
    commit2.key must be("hey")
    commit2.version must be(1)
    commit2.payload.get.deep must be("""{"s":"lol"}""".getBytes(Charsets.UTF_8).deep)
    commit2.author must be("nobody")
    commit2.comment must be("nothing")
    commit2.mtime must be(new DateTime(2))
    evaluating {
      commit2.value.flatMap(_.left.toOption).foreach(throw _)
    } must throwAn[JsonMappingException](""".*\bstring length must be even\b.*""".r)
    commit2.valueOption must be(None)
  }

}
