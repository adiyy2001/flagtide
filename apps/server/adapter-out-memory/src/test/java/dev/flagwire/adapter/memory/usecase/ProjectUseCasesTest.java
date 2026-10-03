package dev.flagwire.adapter.memory.usecase;

import static dev.flagwire.adapter.memory.usecase.Flagwire.DEV;
import static dev.flagwire.adapter.memory.usecase.Flagwire.PROD;
import static dev.flagwire.adapter.memory.usecase.Flagwire.PROJECT;
import static dev.flagwire.adapter.memory.usecase.Flagwire.STAGING;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.port.out.AuditQuery;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.CommandResult;
import dev.flagwire.application.usecase.CreateEnvironment;
import dev.flagwire.application.usecase.CreateProject;
import dev.flagwire.application.usecase.ListApiKeys;
import dev.flagwire.application.usecase.ProvisionedEnvironment;
import dev.flagwire.application.usecase.ProvisionedProject;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.audit.AuditEntry;
import dev.flagwire.domain.audit.EntityType;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.event.DomainEvent;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.project.Environment;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectUseCasesTest {

  private final Flagwire app = new Flagwire();

  @Test
  void creatingAProjectProvisionsThreeEnvironmentsWithAnAdminAndAnSdkKeyEach() {
    ProvisionedProject provisioned = this.app.provisioned;

    assertThat(provisioned.project().environmentKeys())
        .containsExactlyInAnyOrder(DEV, STAGING, PROD);
    assertThat(provisioned.keys()).hasSize(6);
    assertThat(provisioned.keys())
        .extracting(issued -> issued.key().kind())
        .containsOnly(ApiKeyKind.ADMIN, ApiKeyKind.SDK);
    assertThat(provisioned.keys()).extracting(issued -> issued.secret()).doesNotHaveDuplicates();
  }

  @Test
  void creatingAProjectEmitsExactlyOneProjectCreatedEvent() {
    CommandResult<ProvisionedProject> result =
        this.app.createProject.execute(
            new CreateProject.Command(new ProjectKey("blog"), "Blog", "ann"));

    assertThat(result.events()).hasSize(1);
    assertThat(result.events().get(0)).isInstanceOf(DomainEvent.ProjectCreated.class);
    assertThat(result.events().get(0).stamp().author()).isEqualTo("ann");
  }

  @Test
  void creatingAProjectWritesAnAuditEntryWithoutAnEnvironment() {
    Principal admin = this.app.admin(DEV);

    List<AuditEntry> entries =
        this.app.readAuditLog.execute(admin, AuditQuery.forProject(PROJECT, 10));

    assertThat(entries).hasSize(1);
    AuditEntry entry = entries.get(0);
    assertThat(entry.entityType()).isEqualTo(EntityType.PROJECT);
    assertThat(entry.entityKey()).isEqualTo("shop");
    assertThat(entry.author()).isEqualTo("founder");
    assertThat(entry.environment()).isEmpty();
    assertThat(entry.before()).isEmpty();
    assertThat(entry.after()).isPresent();
    assertThat(entry.at()).isEqualTo(this.app.time.now());
  }

  @Test
  void creatingTheSameProjectTwiceConflicts() {
    FlagwireError error =
        this.app.failure(
            () ->
                this.app.createProject.execute(
                    new CreateProject.Command(PROJECT, "Again", "founder")));

    assertThat(error).isInstanceOf(FlagwireError.Conflict.class);
  }

  @Test
  void authenticatesAdminAndSdkSecrets() {
    Principal admin = this.app.authenticate.execute(this.app.secret(PROD, ApiKeyKind.ADMIN));
    Principal sdk = this.app.authenticate.execute(this.app.secret(PROD, ApiKeyKind.SDK));

    assertThat(admin.kind()).isEqualTo(ApiKeyKind.ADMIN);
    assertThat(admin.environment()).isEqualTo(PROD);
    assertThat(admin.project()).isEqualTo(PROJECT);
    assertThat(sdk.kind()).isEqualTo(ApiKeyKind.SDK);
    assertThat(sdk.environmentRef().environment()).isEqualTo(PROD);
  }

  @Test
  void rejectsUnknownMalformedAndCrossKindSecrets() {
    String adminSecret = this.app.secret(DEV, ApiKeyKind.ADMIN);
    String swapped = "fws_" + adminSecret.substring("fwa_".length());

    assertThat(this.app.failure(() -> this.app.authenticate.execute("nonsense")))
        .isInstanceOf(FlagwireError.Unauthorized.class);
    assertThat(this.app.failure(() -> this.app.authenticate.execute("fwa_unknown")))
        .isInstanceOf(FlagwireError.Unauthorized.class);
    assertThat(this.app.failure(() -> this.app.authenticate.execute(swapped)))
        .isInstanceOf(FlagwireError.Unauthorized.class);
  }

  @Test
  void storesAdminKeysOnlyAsHashes() {
    String secret = this.app.secret(DEV, ApiKeyKind.ADMIN);

    this.app.listApiKeys.execute(this.app.admin(DEV), DEV).stream()
        .filter(view -> view.kind() == ApiKeyKind.ADMIN)
        .forEach(view -> assertThat(view.sdkKey()).isEmpty());
    assertThat(this.app.adapters.apiKeys().findByLookup(secret)).isEmpty();
  }

  @Test
  void listsKeysOfAnEnvironmentAndRevealsOnlySdkKeys() {
    List<ListApiKeys.KeyView> views = this.app.listApiKeys.execute(this.app.admin(DEV), DEV);

    assertThat(views).hasSize(2);
    assertThat(views)
        .filteredOn(view -> view.kind() == ApiKeyKind.SDK)
        .singleElement()
        .satisfies(
            view -> assertThat(view.sdkKey()).contains(this.app.secret(DEV, ApiKeyKind.SDK)));
    assertThat(this.app.failure(() -> this.app.listApiKeys.execute(this.app.sdk(DEV), DEV)))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void anAdminReadsItsProjectAndAnSdkKeyCannot() {
    assertThat(this.app.getProject.execute(this.app.admin(DEV)).key()).isEqualTo(PROJECT);
    assertThat(this.app.failure(() -> this.app.getProject.execute(this.app.sdk(DEV))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void addingAnEnvironmentExtendsEveryExistingFlagWithItsOwnSalt() {
    Flag created = Fixtures.createFlag(this.app, "checkout");
    EnvironmentKey qa = new EnvironmentKey("qa");

    CommandResult<ProvisionedEnvironment> result =
        this.app.createEnvironment.execute(
            this.app.admin(DEV), new CreateEnvironment.Command(qa, "QA"));

    Flag extended = this.app.getFlag.execute(this.app.admin(DEV), created.key());
    assertThat(extended.environment(qa)).isPresent();
    assertThat(extended.requireEnvironment(qa).enabled()).isFalse();
    assertThat(extended.requireEnvironment(qa).salt())
        .isNotEqualTo(extended.requireEnvironment(DEV).salt());
    assertThat(extended.revision()).isEqualTo(created.revision().next());
    assertThat(result.value().environment()).isEqualTo(new Environment(qa, "QA"));
    assertThat(result.value().keys()).hasSize(2);
    assertThat(result.events()).singleElement().isInstanceOf(DomainEvent.EnvironmentCreated.class);
    assertThat(this.app.getProject.execute(this.app.admin(DEV)).environmentKeys()).contains(qa);
  }

  @Test
  void keysOfANewEnvironmentAuthenticateAndStayInThatEnvironment() {
    EnvironmentKey qa = new EnvironmentKey("qa");
    CommandResult<ProvisionedEnvironment> result =
        this.app.createEnvironment.execute(
            this.app.admin(DEV), new CreateEnvironment.Command(qa, "QA"));

    Principal qaAdmin =
        result.value().keys().stream()
            .filter(issued -> issued.key().kind() == ApiKeyKind.ADMIN)
            .map(issued -> this.app.authenticate.execute(issued.secret()))
            .findFirst()
            .orElseThrow();

    assertThat(qaAdmin.environment()).isEqualTo(qa);
    assertThat(this.app.listApiKeys.execute(qaAdmin, qa)).hasSize(2);
  }

  @Test
  void addingAnEnvironmentTwiceConflictsAndNeedsAnAdmin() {
    assertThat(
            this.app.failure(
                () ->
                    this.app.createEnvironment.execute(
                        this.app.admin(DEV), new CreateEnvironment.Command(STAGING, "Again"))))
        .isInstanceOf(FlagwireError.Conflict.class);
    assertThat(
            this.app.failure(
                () ->
                    this.app.createEnvironment.execute(
                        this.app.sdk(DEV),
                        new CreateEnvironment.Command(new EnvironmentKey("qa"), "QA"))))
        .isInstanceOf(FlagwireError.Forbidden.class);
  }

  @Test
  void aFailedEnvironmentCreationLeavesNothingBehind() {
    Fixtures.createFlag(this.app, "checkout");
    long keysBefore = this.app.listApiKeys.execute(this.app.admin(DEV), DEV).size();

    this.app.failure(
        () ->
            this.app.createEnvironment.execute(
                this.app.admin(DEV), new CreateEnvironment.Command(STAGING, "Again")));

    assertThat(this.app.listApiKeys.execute(this.app.admin(DEV), DEV)).hasSize((int) keysBefore);
    assertThat(this.app.getFlag.execute(this.app.admin(DEV), new FlagKey("checkout")).revision())
        .isEqualTo(Revision.FIRST);
  }
}
