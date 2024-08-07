// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.testing.DatabaseHelper.createTld;
import static google.registry.testing.DatabaseHelper.persistResource;
import static google.registry.testing.DatabaseHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHost;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomain;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHistoryEntry;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarPocs;
import static google.registry.testing.GsonSubject.assertAboutJson;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import google.registry.model.contact.Contact;
import google.registry.model.domain.Domain;
import google.registry.model.domain.Period;
import google.registry.model.host.Host;
import google.registry.model.registrar.Registrar;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.tld.Tld;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.SearchType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import google.registry.testing.FullFieldsTestEntityHelper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RdapDomainAction}.
 *
 * <p>TODO(b/26872828): The next time we do any work on RDAP, consider adding the APNIC RDAP
 * conformance checker to the unit test suite.
 */
class RdapDomainActionTest extends RdapActionBaseTestCase<RdapDomainAction> {

  RdapDomainActionTest() {
    super(RdapDomainAction.class);
  }

  @BeforeEach
  void beforeEach() {
    // lol
    createTld("lol");
    Registrar registrarLol = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarPocs(registrarLol));
    Contact registrantLol =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrarLol);
    Contact adminContactLol =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrarLol);
    Contact techContactLol =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-TRL", "The Raven", "bog@cat.lol", clock.nowUtc().minusYears(3), registrarLol);
    Host host1 = makeAndPersistHost("ns1.cat.lol", "1.2.3.4", null, clock.nowUtc().minusYears(1));
    Host host2 =
        makeAndPersistHost(
            "ns2.cat.lol", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    persistResource(
        makeDomain(
                "cat.lol",
                registrantLol,
                adminContactLol,
                techContactLol,
                host1,
                host2,
                registrarLol)
            .asBuilder()
            .setCreationTimeForTest(clock.nowUtc().minusYears(3))
            .setCreationRegistrarId("TheRegistrar")
            .build());

    // deleted domain in lol
    Host hostDodo2 =
        makeAndPersistHost(
            "ns2.dodo.lol", "bad:f00d:cafe:0:0:0:15:beef", clock.nowUtc().minusYears(2));
    Domain domainDeleted =
        persistResource(
            makeDomain(
                    "dodo.lol",
                    FullFieldsTestEntityHelper.makeAndPersistContact(
                        "5372808-ERL",
                        "Goblin Market",
                        "lol@cat.lol",
                        clock.nowUtc().minusYears(1),
                        registrarLol),
                    FullFieldsTestEntityHelper.makeAndPersistContact(
                        "5372808-IRL",
                        "Santa Claus",
                        "BOFH@cat.lol",
                        clock.nowUtc().minusYears(2),
                        registrarLol),
                    FullFieldsTestEntityHelper.makeAndPersistContact(
                        "5372808-TRL",
                        "The Raven",
                        "bog@cat.lol",
                        clock.nowUtc().minusYears(3),
                        registrarLol),
                    host1,
                    hostDodo2,
                    registrarLol)
                .asBuilder()
                .setCreationTimeForTest(clock.nowUtc().minusYears(3))
                .setCreationRegistrarId("TheRegistrar")
                .setDeletionTime(clock.nowUtc().minusDays(1))
                .build());
    // cat.みんな
    createTld("xn--q9jyb4c");
    Registrar registrarIdn =
        persistResource(makeRegistrar("idnregistrar", "IDN Registrar", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarPocs(registrarIdn));
    Contact registrantIdn =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrarIdn);
    Contact adminContactIdn =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrarIdn);
    Contact techContactIdn =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-TRL", "The Raven", "bog@cat.lol", clock.nowUtc().minusYears(3), registrarIdn);
    persistResource(
        makeDomain(
                "cat.みんな",
                registrantIdn,
                adminContactIdn,
                techContactIdn,
                host1,
                host2,
                registrarIdn)
            .asBuilder()
            .setCreationTimeForTest(clock.nowUtc().minusYears(3))
            .setCreationRegistrarId("TheRegistrar")
            .build());

    // 1.tld
    createTld("1.tld");
    Registrar registrar1Tld = persistResource(
        makeRegistrar("1tldregistrar", "Multilevel Registrar", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarPocs(registrar1Tld));
    Contact registrant1Tld =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-ERL",
            "Goblin Market",
            "lol@cat.lol",
            clock.nowUtc().minusYears(1),
            registrar1Tld);
    Contact adminContact1Tld =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-IRL",
            "Santa Claus",
            "BOFH@cat.lol",
            clock.nowUtc().minusYears(2),
            registrar1Tld);
    Contact techContact1Tld =
        FullFieldsTestEntityHelper.makeAndPersistContact(
            "5372808-TRL", "The Raven", "bog@cat.lol", clock.nowUtc().minusYears(3), registrar1Tld);
    persistResource(
        makeDomain(
                "cat.1.tld",
                registrant1Tld,
                adminContact1Tld,
                techContact1Tld,
                host1,
                host2,
                registrar1Tld)
            .asBuilder()
            .setCreationTimeForTest(clock.nowUtc().minusYears(3))
            .setCreationRegistrarId("TheRegistrar")
            .build());

    // history entries
    persistResource(
        makeHistoryEntry(
            domainDeleted,
            HistoryEntry.Type.DOMAIN_DELETE,
            Period.create(1, Period.Unit.YEARS),
            "deleted",
            clock.nowUtc().minusMonths(6)));
  }

  private JsonObject addBoilerplate(JsonObject obj) {
    RdapTestHelper.addDomainBoilerplateNotices(obj, "https://example.tld/rdap/");
    return obj;
  }

  private void assertProperResponseForCatLol(String queryString, String expectedOutputFile) {
    assertAboutJson()
        .that(generateActualJson(queryString))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("cat.lol", "C-LOL")
                    .addContact("4-ROID")
                    .addContact("6-ROID")
                    .addContact("2-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.cat.lol", "A-ROID")
                    .addRegistrar("Yes Virginia <script>")
                    .load(expectedOutputFile)));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testInvalidDomain_returns400() {
    assertAboutJson()
        .that(generateActualJson("invalid/domain/name"))
        .isEqualTo(
            generateExpectedJsonError(
                "invalid/domain/name is not a valid domain name: Domain names can only contain a-z,"
                    + " 0-9, '.' and '-'",
                400));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testUnknownDomain_returns400() {
    assertAboutJson()
        .that(generateActualJson("missingdomain.com"))
        .isEqualTo(
            generateExpectedJsonError(
                "missingdomain.com is not a valid domain name: Domain name is under tld com which"
                    + " doesn't exist",
                400));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testValidDomain_works() {
    login("evilregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  void testValidDomain_asAdministrator_works() {
    loginAsAdmin();
    assertProperResponseForCatLol("cat.lol", "rdap_domain.json");
  }

  @Test
  void testValidDomain_notLoggedIn_noContacts() {
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testValidDomain_notLoggedIn_contactsShowRedacted_evenWhenRegistrantDoesntExist() {
    // Even though the registrant is empty on this domain, it still shows a full set of REDACTED
    // fields through RDAP.
    persistResource(
        loadByForeignKey(Domain.class, "cat.lol", clock.nowUtc())
            .get()
            .asBuilder()
            .setRegistrant(Optional.empty())
            .build());
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testValidDomain_notLoggedIn_contactsShowRedacted_whenNoContactsExist() {
    // Even though the domain has no contacts, it still shows a full set of REDACTED fields through
    // RDAP.
    persistResource(
        loadByForeignKey(Domain.class, "cat.lol", clock.nowUtc())
            .get()
            .asBuilder()
            .setRegistrant(Optional.empty())
            .setContacts(ImmutableSet.of())
            .build());
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts_exist_with_remark.json");
  }

  @Test
  void testValidDomain_loggedInAsOtherRegistrar_noContacts() {
    login("idnregistrar");
    assertProperResponseForCatLol("cat.lol", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testUpperCase_ignored() {
    assertProperResponseForCatLol("CaT.lOl", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testTrailingDot_ignored() {
    assertProperResponseForCatLol("cat.lol.", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testQueryParameter_ignored() {
    assertProperResponseForCatLol("cat.lol?key=value", "rdap_domain_no_contacts_with_remark.json");
  }

  @Test
  void testIdnDomain_works() {
    login("idnregistrar");
    assertAboutJson()
        .that(generateActualJson("cat.みんな"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("cat.みんな", "1D-Q9JYB4C")
                    .addContact("19-ROID")
                    .addContact("1B-ROID")
                    .addContact("17-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.cat.lol", "A-ROID")
                    .addRegistrar("IDN Registrar")
                    .load("rdap_domain_unicode.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testIdnDomainWithPercentEncoding_works() {
    login("idnregistrar");
    assertAboutJson()
        .that(generateActualJson("cat.%E3%81%BF%E3%82%93%E3%81%AA"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("cat.みんな", "1D-Q9JYB4C")
                    .addContact("19-ROID")
                    .addContact("1B-ROID")
                    .addContact("17-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.cat.lol", "A-ROID")
                    .addRegistrar("IDN Registrar")
                    .load("rdap_domain_unicode.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testPunycodeDomain_works() {
    login("idnregistrar");
    assertAboutJson()
        .that(generateActualJson("cat.xn--q9jyb4c"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("cat.みんな", "1D-Q9JYB4C")
                    .addContact("19-ROID")
                    .addContact("1B-ROID")
                    .addContact("17-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.cat.lol", "A-ROID")
                    .addRegistrar("IDN Registrar")
                    .load("rdap_domain_unicode.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testMultilevelDomain_works() {
    login("1tldregistrar");
    assertAboutJson()
        .that(generateActualJson("cat.1.tld"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("cat.1.tld", "25-1_TLD")
                    .addContact("21-ROID")
                    .addContact("23-ROID")
                    .addContact("1F-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.cat.lol", "A-ROID")
                    .addRegistrar("Multilevel Registrar")
                    .load("rdap_domain.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  // todo (b/27378695): reenable or delete this test
  @Disabled
  @Test
  void testDomainInTestTld_notFound() {
    persistResource(Tld.get("lol").asBuilder().setTldType(Tld.TldType.TEST).build());
    generateActualJson("cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void testDeletedDomain_notFound() {
    assertAboutJson()
        .that(generateActualJson("dodo.lol"))
        .isEqualTo(generateExpectedJsonError("dodo.lol not found", 404));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void testDeletedDomain_notFound_includeDeletedSetFalse() {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void testDeletedDomain_notFound_notLoggedIn() {
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void testDeletedDomain_notFound_loggedInAsDifferentRegistrar() {
    login("1tldregistrar");
    action.includeDeletedParam = Optional.of(true);
    generateActualJson("dodo.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void testDeletedDomain_works_loggedInAsCorrectRegistrar() {
    login("evilregistrar");
    action.includeDeletedParam = Optional.of(true);
    assertAboutJson()
        .that(generateActualJson("dodo.lol"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("dodo.lol", "15-LOL")
                    .addContact("11-ROID")
                    .addContact("13-ROID")
                    .addContact("F-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.dodo.lol", "D-ROID")
                    .addRegistrar("Yes Virginia <script>")
                    .load("rdap_domain_deleted.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testDeletedDomain_works_loggedInAsAdmin() {
    loginAsAdmin();
    action.includeDeletedParam = Optional.of(true);
    assertAboutJson()
        .that(generateActualJson("dodo.lol"))
        .isEqualTo(
            addBoilerplate(
                jsonFileBuilder()
                    .addDomain("dodo.lol", "15-LOL")
                    .addContact("11-ROID")
                    .addContact("13-ROID")
                    .addContact("F-ROID")
                    .addNameserver("ns1.cat.lol", "8-ROID")
                    .addNameserver("ns2.dodo.lol", "D-ROID")
                    .addRegistrar("Yes Virginia <script>")
                    .load("rdap_domain_deleted.json")));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void testMetrics() {
    generateActualJson("cat.lol");
    verify(rdapMetrics)
        .updateMetrics(
            RdapMetrics.RdapMetricInformation.builder()
                .setEndpointType(EndpointType.DOMAIN)
                .setSearchType(SearchType.NONE)
                .setWildcardType(WildcardType.INVALID)
                .setPrefixLength(0)
                .setIncludeDeleted(false)
                .setRegistrarSpecified(false)
                .setRole(RdapAuthorization.Role.PUBLIC)
                .setRequestMethod(Action.Method.GET)
                .setStatusCode(200)
                .setIncompletenessWarningType(IncompletenessWarningType.COMPLETE)
                .build());
  }
}
