/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.mifos.anubis.api.v1.domain.AllowedOperation;
import io.mifos.anubis.api.v1.domain.Signature;
import io.mifos.core.api.context.AutoUserContext;
import io.mifos.core.api.util.NotFoundException;
import io.mifos.core.lang.security.RsaKeyPairFactory;
import io.mifos.identity.api.v1.PermittableGroupIds;
import io.mifos.identity.api.v1.domain.Permission;
import io.mifos.identity.api.v1.events.ApplicationPermissionEvent;
import io.mifos.identity.api.v1.events.ApplicationSignatureEvent;
import io.mifos.identity.api.v1.events.EventConstants;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * @author Myrle Krantz
 */
public class TestApplications extends AbstractComponentTest {
  private String createTestApplicationName()
  {
    return "test" + RandomStringUtils.randomNumeric(3) + "-v1";
  }

  @Test
  public void testSetApplicationSignature() throws InterruptedException {
    try (final AutoUserContext ignored
                 = tenantApplicationSecurityEnvironment.createAutoSeshatContext()) {
      final ApplicationSignatureEvent appPlusSig = setApplicationSignature();

      final List<String> foundApplications = getTestSubject().getApplications();
      Assert.assertTrue(foundApplications.contains(appPlusSig.getApplicationIdentifier()));

      getTestSubject().getApplicationSignature(
              appPlusSig.getApplicationIdentifier(),
              appPlusSig.getKeyTimestamp());
    }
  }

  @Test
  public void testCreateAndDeleteApplicationPermission() throws InterruptedException {
    try (final AutoUserContext ignored
                 = tenantApplicationSecurityEnvironment.createAutoSeshatContext()) {
      final ApplicationSignatureEvent appPlusSig = setApplicationSignature();

      final Permission permission = new Permission();
      permission.setPermittableEndpointGroupIdentifier(PermittableGroupIds.IDENTITY_MANAGEMENT);
      permission.setAllowedOperations(Collections.singleton(AllowedOperation.READ));

      getTestSubject().createApplicationPermission(appPlusSig.getApplicationIdentifier(), permission);
      Assert.assertTrue(eventRecorder.wait(EventConstants.OPERATION_POST_APPLICATION_PERMISSION,
              new ApplicationPermissionEvent(appPlusSig.getApplicationIdentifier(), PermittableGroupIds.IDENTITY_MANAGEMENT)));

      {
        final List<Permission> applicationPermissions = getTestSubject().getApplicationPermissions(appPlusSig.getApplicationIdentifier());
        Assert.assertTrue(applicationPermissions.contains(permission));
      }

      getTestSubject().deleteApplicationPermission(appPlusSig.getApplicationIdentifier(), permission.getPermittableEndpointGroupIdentifier());
      Assert.assertTrue(eventRecorder.wait(EventConstants.OPERATION_DELETE_APPLICATION_PERMISSION,
              new ApplicationPermissionEvent(appPlusSig.getApplicationIdentifier(), PermittableGroupIds.IDENTITY_MANAGEMENT)));

      {
        final List<Permission> applicationPermissions = getTestSubject().getApplicationPermissions(appPlusSig.getApplicationIdentifier());
        Assert.assertFalse(applicationPermissions.contains(permission));
      }
    }
  }

  @Test
  public void testDeleteApplication() throws InterruptedException {
    try (final AutoUserContext ignored
                 = tenantApplicationSecurityEnvironment.createAutoSeshatContext()) {
      final ApplicationSignatureEvent appPlusSig = setApplicationSignature();

      getTestSubject().deleteApplication(appPlusSig.getApplicationIdentifier());

      Assert.assertTrue(eventRecorder.wait(EventConstants.OPERATION_DELETE_APPLICATION, appPlusSig.getApplicationIdentifier()));

      final List<String> foundApplications = getTestSubject().getApplications();
      Assert.assertFalse(foundApplications.contains(appPlusSig.getApplicationIdentifier()));

      try {
        getTestSubject().getApplicationSignature(
                appPlusSig.getApplicationIdentifier(),
                appPlusSig.getKeyTimestamp());
        Assert.fail("Shouldn't find app sig after app was deleted.");
      }
      catch (final NotFoundException ignored2) {

      }
    }
  }

  private ApplicationSignatureEvent setApplicationSignature() throws InterruptedException {
    final String testApplicationName = createTestApplicationName();
    final RsaKeyPairFactory.KeyPairHolder keyPair = RsaKeyPairFactory.createKeyPair();
    final Signature signature = new Signature(keyPair.getPublicKeyMod(), keyPair.getPublicKeyExp());

    getTestSubject().setApplicationSignature(testApplicationName, keyPair.getTimestamp(), signature);

    final ApplicationSignatureEvent event = new ApplicationSignatureEvent(testApplicationName, keyPair.getTimestamp());
    Assert.assertTrue(eventRecorder.wait(EventConstants.OPERATION_PUT_APPLICATION_SIGNATURE, event));
    return event;
  }
}
