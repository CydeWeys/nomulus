// Copyright 2020 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model.history;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ImmutableObjectSubject.assertAboutImmutableObjects;
import static google.registry.persistence.transaction.TransactionManagerFactory.jpaTm;
import static google.registry.testing.DatabaseHelper.insertInDb;
import static google.registry.testing.DatabaseHelper.loadByEntity;
import static google.registry.testing.DatabaseHelper.newHostResource;
import static google.registry.testing.DatabaseHelper.newHostResourceWithRoid;
import static java.nio.charset.StandardCharsets.UTF_8;

import google.registry.model.EntityTestCase;
import google.registry.model.eppcommon.Trid;
import google.registry.model.host.HostBase;
import google.registry.model.host.HostHistory;
import google.registry.model.host.HostResource;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.DatabaseHelper;
import google.registry.util.SerializeUtils;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Tests for {@link HostHistory}. */
public class HostHistoryTest extends EntityTestCase {

  HostHistoryTest() {
    super(JpaEntityCoverageCheck.ENABLED);
  }

  @Test
  void testPersistence() {
    HostResource host = newHostResourceWithRoid("ns1.example.com", "host1");
    insertInDb(host);
    HostResource hostFromDb = loadByEntity(host);
    HostHistory hostHistory = createHostHistory(hostFromDb);
    insertInDb(hostHistory);
    jpaTm()
        .transact(
            () -> {
              HostHistory fromDatabase = jpaTm().loadByKey(hostHistory.createVKey());
              assertHostHistoriesEqual(fromDatabase, hostHistory);
              assertThat(fromDatabase.getParentVKey()).isEqualTo(hostHistory.getParentVKey());
            });
  }

  @Test
  void testSerializable() {
    HostResource host = newHostResourceWithRoid("ns1.example.com", "host1");
    insertInDb(host);
    HostResource hostFromDb = loadByEntity(host);
    HostHistory hostHistory = createHostHistory(hostFromDb);
    insertInDb(hostHistory);
    HostHistory fromDatabase = jpaTm().transact(() -> jpaTm().loadByKey(hostHistory.createVKey()));
    assertThat(SerializeUtils.serializeDeserialize(fromDatabase)).isEqualTo(fromDatabase);
  }

  @Test
  void testLegacyPersistence_nullHostBase() {
    HostResource host = newHostResourceWithRoid("ns1.example.com", "host1");
    insertInDb(host);

    HostResource hostFromDb = loadByEntity(host);
    HostHistory hostHistory = createHostHistory(hostFromDb).asBuilder().setHost(null).build();
    insertInDb(hostHistory);

    jpaTm()
        .transact(
            () -> {
              HostHistory fromDatabase = jpaTm().loadByKey(hostHistory.createVKey());
              assertHostHistoriesEqual(fromDatabase, hostHistory);
              assertThat(fromDatabase.getParentVKey()).isEqualTo(hostHistory.getParentVKey());
            });
  }

  @Test
  void testBeforeSqlSave_afterHostPersisted() {
    HostResource hostResource = newHostResource("ns1.example.tld");
    HostHistory hostHistory =
        new HostHistory.Builder()
            .setType(HistoryEntry.Type.HOST_CREATE)
            .setXmlBytes("<xml></xml>".getBytes(UTF_8))
            .setModificationTime(fakeClock.nowUtc())
            .setRegistrarId("TheRegistrar")
            .setTrid(Trid.create("ABC-123", "server-trid"))
            .setBySuperuser(false)
            .setReason("reason")
            .setRequestedByRegistrar(true)
            .setHostRepoId(hostResource.getRepoId())
            .build();
    jpaTm()
        .transact(
            () -> {
              jpaTm().put(hostResource);
              hostHistory.beforeSqlSaveOnReplay();
              jpaTm().put(hostHistory);
            });
    jpaTm()
        .transact(
            () ->
                assertAboutImmutableObjects()
                    .that(jpaTm().loadByEntity(hostResource))
                    .hasFieldsEqualTo(jpaTm().loadByEntity(hostHistory).getHostBase().get()));
  }

  @Test
  void testBeforeSqlSave_canonicalNameUncapitalized() throws Exception {
    Field hostNameField = HostBase.class.getDeclaredField("fullyQualifiedHostName");
    // reflection hacks to get around visibility issues
    hostNameField.setAccessible(true);
    HostResource hostResource = newHostResource("ns1.example.tld");
    hostNameField.set(hostResource, "NS1.EXAMPLE.TLD");
    HostHistory hostHistory =
        new HostHistory.Builder()
            .setType(HistoryEntry.Type.HOST_CREATE)
            .setXmlBytes("<xml></xml>".getBytes(UTF_8))
            .setModificationTime(fakeClock.nowUtc())
            .setRegistrarId("TheRegistrar")
            .setTrid(Trid.create("ABC-123", "server-trid"))
            .setBySuperuser(false)
            .setReason("reason")
            .setRequestedByRegistrar(true)
            .setHostRepoId(hostResource.getRepoId())
            .build();

    DatabaseHelper.putInDb(hostResource, hostHistory);
    jpaTm().transact(hostHistory::beforeSqlSaveOnReplay);

    assertThat(hostHistory.getHostBase().get().getHostName()).isEqualTo("ns1.example.tld");
  }

  @Test
  void testBeforeSqlSave_canonicalNameUtf8() throws Exception {
    Field hostNameField = HostBase.class.getDeclaredField("fullyQualifiedHostName");
    // reflection hacks to get around visibility issues
    hostNameField.setAccessible(true);
    HostResource hostResource = newHostResource("ns1.example.tld");
    hostNameField.set(hostResource, "ns1.kittyçat.tld");
    HostHistory hostHistory =
        new HostHistory.Builder()
            .setType(HistoryEntry.Type.HOST_CREATE)
            .setXmlBytes("<xml></xml>".getBytes(UTF_8))
            .setModificationTime(fakeClock.nowUtc())
            .setRegistrarId("TheRegistrar")
            .setTrid(Trid.create("ABC-123", "server-trid"))
            .setBySuperuser(false)
            .setReason("reason")
            .setRequestedByRegistrar(true)
            .setHostRepoId(hostResource.getRepoId())
            .build();

    DatabaseHelper.putInDb(hostResource, hostHistory);
    jpaTm().transact(hostHistory::beforeSqlSaveOnReplay);

    assertThat(hostHistory.getHostBase().get().getHostName()).isEqualTo("ns1.xn--kittyat-yxa.tld");
  }

  private void assertHostHistoriesEqual(HostHistory one, HostHistory two) {
    assertAboutImmutableObjects().that(one).isEqualExceptFields(two, "hostBase");
    assertAboutImmutableObjects()
        .that(one.getHostBase().orElse(null))
        .isEqualExceptFields(two.getHostBase().orElse(null), "repoId");
  }

  private HostHistory createHostHistory(HostBase hostBase) {
    return new HostHistory.Builder()
        .setType(HistoryEntry.Type.HOST_CREATE)
        .setXmlBytes("<xml></xml>".getBytes(UTF_8))
        .setModificationTime(fakeClock.nowUtc())
        .setRegistrarId("TheRegistrar")
        .setTrid(Trid.create("ABC-123", "server-trid"))
        .setBySuperuser(false)
        .setReason("reason")
        .setRequestedByRegistrar(true)
        .setHost(hostBase)
        .setHostRepoId(hostBase.getRepoId())
        .build();
  }
}
