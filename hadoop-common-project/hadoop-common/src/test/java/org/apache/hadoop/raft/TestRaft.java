/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.raft;

import org.apache.hadoop.raft.client.RaftClient;
import org.apache.hadoop.raft.protocol.Message;
import org.apache.hadoop.raft.server.RaftConstants;
import org.apache.hadoop.raft.server.RaftLog;
import org.apache.hadoop.raft.server.RaftServer;
import org.apache.hadoop.raft.server.RequestHandler;
import org.apache.hadoop.raft.server.protocol.Entry;
import org.apache.hadoop.test.GenericTestUtils;
import org.apache.log4j.Level;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintStream;

public class TestRaft {
  {
    GenericTestUtils.setLogLevel(RaftServer.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RaftLog.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RequestHandler.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RaftClient.LOG, Level.DEBUG);
  }
  static final PrintStream out = System.out;

  @Test
  public void testBasicLeaderElection() throws Exception {
    final MiniRaftCluster cluster = new MiniRaftCluster(5);
    Assert.assertNull(cluster.getLeader());
    cluster.start();

    waitAndKillLeader(cluster, true);
    waitAndKillLeader(cluster, true);
    waitAndKillLeader(cluster, true);
    waitAndKillLeader(cluster, false);
  }

  static RaftServer waitForLeader(MiniRaftCluster cluster)
      throws InterruptedException {
    cluster.printServers(out);
    Thread.sleep(RaftConstants.ELECTION_TIMEOUT_MAX_MS + 100);
    cluster.printServers(out);
    return cluster.getLeader();
  }

  static void waitAndKillLeader(MiniRaftCluster cluster, boolean expectLeader)
      throws InterruptedException {
    final RaftServer leader = waitForLeader(cluster);
    if (!expectLeader) {
      Assert.assertNull(leader);
    } else {
      Assert.assertNotNull(leader);
      out.println("killing leader = " + leader);
      cluster.killServer(leader.getId());
    }
  }

  @Test
  public void testBasicAppendEntries() throws Exception {
    final MiniRaftCluster cluster = new MiniRaftCluster(5);
    cluster.start();
    RaftServer leader = waitForLeader(cluster);
    final long term = leader.getState().getCurrentTerm();
    final String killed = cluster.getFollowers().get(3).getId();
    cluster.killServer(killed);
    cluster.printServers(out);

    final SimpleMessage[] messages = new SimpleMessage[10];
    final RaftClient client = cluster.createClient("client", null);
    for(int i = 0; i < messages.length; i++) {
      messages[i] = new SimpleMessage("m" + i);
      client.send(messages[i]);
    }

    Thread.sleep(RaftConstants.ELECTION_TIMEOUT_MAX_MS + 100);
    cluster.printAllLogs(out);

    for(RaftServer s : cluster.getServers()) {
      if (s.isRunning()) {
        assertLogEntries(s.getState().getLog().getEntries(1), 1, term, messages);
      }
    }
  }

  static void assertLogEntries(Entry[] entries, long startIndex,
      long expertedTerm, SimpleMessage... expectedMessages) {
    Assert.assertEquals(expectedMessages.length, entries.length);
    for(int i = 0; i < entries.length; i++) {
      final Entry e = entries[i];
      Assert.assertEquals(expertedTerm, e.getTerm());
      Assert.assertEquals(startIndex + i, e.getIndex());
      Assert.assertEquals(expectedMessages[i], e.getMessage());
    }
  }

  static class SimpleMessage implements Message {
    final String messageId;

    SimpleMessage(final String messageId) {
      this.messageId = messageId;
    }

    @Override
    public String toString() {
      return messageId;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      } else if (obj == null || !(obj instanceof SimpleMessage)) {
        return false;
      } else {
        final SimpleMessage that = (SimpleMessage)obj;
        return this.messageId.equals(that.messageId);
      }
    }
  }
}
