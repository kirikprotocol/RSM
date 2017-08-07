package mobi.eyeline.rsm.tc8;

import mobi.eyeline.rsm.model.PersistedSession;
import mobi.eyeline.rsm.model.PersistableSession;
import org.apache.catalina.Manager;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Collectors;


public class RedisSession extends StandardSession implements PersistableSession {

  private final Log log = LogFactory.getLog(RedisSession.class);

  private HashMap<String, Object> changedAttributes;
  private boolean dirty;

  RedisSession(Manager manager) {
    super(manager);
    resetDirtyTracking();
  }

  boolean isDirty() {
    return dirty || !changedAttributes.isEmpty();
  }

  void resetDirtyTracking() {
    changedAttributes = new HashMap<>();
    dirty = false;
  }

  @Override
  public void setAttribute(String key, Object value) {
    final Object oldValue = getAttribute(key);
    super.setAttribute(key, value);

    //
    //  Check if changed and persist if necessary.
    //
    if ((value != null || oldValue != null) &&
        (value == null || oldValue == null || !value.getClass().isInstance(oldValue) || !value.equals(oldValue))) {
      if (this.manager instanceof RedisSessionManager
          && ((RedisSessionManager) this.manager).doSaveOnChange()) {
        try {
          ((RedisSessionManager) this.manager).save(this, true);
        } catch (IOException ex) {
          log.error("Error saving session on setAttribute (triggered by saveOnChange=true): " + ex.getMessage());
        }
      } else {
        changedAttributes.put(key, value);
      }
    }
  }

  //
  //  Check if changed and persist if necessary.
  //
  @Override
  public void removeAttribute(String name) {
    super.removeAttribute(name);
    if (this.manager instanceof RedisSessionManager
        && ((RedisSessionManager) this.manager).doSaveOnChange()) {
      try {
        ((RedisSessionManager) this.manager).save(this, true);
      } catch (IOException ex) {
        log.error("Error saving session on setAttribute (triggered by saveOnChange=true): " + ex.getMessage());
      }
    } else {
      dirty = true;
    }
  }

  @Override
  public void setId(String id) {
    // Specifically do not call super(): it's implementation does unexpected things
    // like calling manager.remove(session.id) and manager.add(session).

    this.id = id;
  }

  @Override
  public void setPrincipal(Principal principal) {
    dirty = true;
    super.setPrincipal(principal);
  }

  @Override
  public PersistedSession asPersistedSession() {
    final PersistedSession rc = new PersistedSession();

    rc.creationTime = getCreationTimeInternal();
    rc.lastAccessedTime = getLastAccessedTimeInternal();
    rc.maxInactiveInterval = getMaxInactiveInterval();
    rc.isNew = isNew();
    rc.thisAccessedTime = getThisAccessedTimeInternal();
    rc.isValid = isValidInternal();
    rc.id = getIdInternal();

    final GenericPrincipal principal = (GenericPrincipal) getPrincipal();
    if (principal != null) {
      rc.principalName = principal.getName();
      rc.principalRoles = principal.getRoles();
      rc.userPrincipal = principal.getUserPrincipal();
    }

    if (this.attributes != null) {
      rc.attributes = this.attributes
          .entrySet()
          .stream()
          .filter(attr ->
              attr.getValue() != null &&
                  isAttributeDistributable(attr.getKey(), attr.getValue()) &&
                  !exclude(attr.getKey(), attr.getValue())
          ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    return rc;
  }

  @Override
  public void fromPersistedSession(PersistedSession rc) {
    authType = null;
    creationTime = rc.creationTime;
    lastAccessedTime = rc.lastAccessedTime;
    maxInactiveInterval = rc.maxInactiveInterval;
    isNew = rc.isNew;
    isValid = rc.isValid;
    thisAccessedTime = rc.thisAccessedTime;
    principal = null;
    id = rc.id;

    rc.attributes.forEach((k, v) -> attributes.put(k, v));

    if (listeners == null) {
      listeners = new ArrayList<>();
    }

    if (notes == null) {
      notes = new Hashtable<>();
    }

    if (rc.principalName != null) {
      this.principal = new GenericPrincipal(rc.principalName, null, Arrays.asList(rc.principalRoles));
    } else {
      this.principal = null;
    }

  }

  private void dump(StringBuilder buf) {
    buf.append("ID = [")
        .append(getIdInternal())
        .append("]");

    buf.append(", attributes = [")
        .append(attributes)
        .append("]");
  }

  String dump() {
    final StringBuilder buf = new StringBuilder();
    dump(buf);
    return buf.toString();
  }

  @Override
  public String toString() {
    return "RedisSession{" +
        "id='" + id + '\'' +
        '}';
  }
}
