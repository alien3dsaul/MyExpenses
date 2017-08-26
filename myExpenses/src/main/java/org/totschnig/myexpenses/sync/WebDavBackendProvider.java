package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.webkit.MimeTypeMap;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;
import org.totschnig.myexpenses.sync.webdav.CertificateHelper;
import org.totschnig.myexpenses.sync.webdav.HttpException;
import org.totschnig.myexpenses.sync.webdav.InvalidCertificateException;
import org.totschnig.myexpenses.sync.webdav.LockableDavResource;
import org.totschnig.myexpenses.sync.webdav.WebDavClient;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import static org.totschnig.myexpenses.util.FileCopyUtils.toByteArray;

public class WebDavBackendProvider extends AbstractSyncBackendProvider {

  public static final String KEY_WEB_DAV_CERTIFICATE = "webDavCertificate";
  public static final String KEY_WEB_DAV_FALLBACK_TO_CLASS1 = "fallbackToClass1";
  private final MediaType MIME_JSON = MediaType.parse(MIMETYPE_JSON + "; charset=utf-8");
  private static final String FALLBACK_LOCK_FILENAME = ".lock";

  private WebDavClient webDavClient;
  private boolean fallbackToClass1 = false;
  /**
   * this holds the uuid of the db account which data is currently synced
   */
  private String accountUuid;

  WebDavBackendProvider(Context context, android.accounts.Account account, AccountManager accountManager) throws SyncParseException {
    super(context);
    String url = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL);
    String userName = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_USERNAME);
    String password = accountManager.getPassword(account);

    fallbackToClass1 = accountManager.getUserData(account, KEY_WEB_DAV_FALLBACK_TO_CLASS1) != null;

    X509Certificate certificate = null;
    if (accountManager.getUserData(account, KEY_WEB_DAV_CERTIFICATE) != null) {
      try {
        certificate = CertificateHelper.fromString(accountManager.getUserData(account, KEY_WEB_DAV_CERTIFICATE));
      } catch (CertificateException e) {
        throw new SyncParseException(e);
      }
    }
    try {
      webDavClient = new WebDavClient(url, userName, password, certificate);
    } catch (InvalidCertificateException e) {
      throw new SyncParseException(e);
    }
  }

  @Override
  public boolean withAccount(Account account) {
    accountUuid = account.uuid;
    try {
      webDavClient.mkCol(accountUuid);
      LockableDavResource metaData = webDavClient.getResource(accountUuid, ACCOUNT_METADATA_FILENAME);
      if (!metaData.exists()) {
        metaData.put(RequestBody.create(MIME_JSON, buildMetadata(account)), null, false);
        createWarningFile();
      }
    } catch (at.bitfire.dav4android.exception.HttpException | IOException e) {
      return false;
    }
    return true;
  }

  @Override
  public boolean resetAccountData(String uuid) {
    try {
      for (DavResource davResource : webDavClient.getFolderMembers(uuid)) {
        davResource.delete(null);
      }
    } catch (IOException | at.bitfire.dav4android.exception.HttpException e) {
      return false;
    }
    return true;
  }


  @Override
  protected String getExistingLockToken() throws IOException {
    LockableDavResource lockfile = getLockFile();
      if (lockfile.exists()) {
        try {
          return lockfile.get("text/plain").string();
        } catch (at.bitfire.dav4android.exception.HttpException | DavException e) {
          throw new IOException(e);
        }
      } else {
        return null;
      }
  }

  @Override
  protected boolean writeLockToken(String lockToken) throws IOException {
    LockableDavResource lockfile = getLockFile();
    try {
      lockfile.put(RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), lockToken), null, false);
      return true;
    } catch (at.bitfire.dav4android.exception.HttpException e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean lock() {
    if (fallbackToClass1) {
      return super.lock();
    } else {
      return webDavClient.lock(accountUuid);
    }
  }

  private LockableDavResource getLockFile() {
    return webDavClient.getResource(accountUuid, FALLBACK_LOCK_FILENAME);
  }

  @NonNull
  @Override
  public ChangeSet getChangeSetSince(long sequenceNumber, Context context) throws IOException {
    return merge(filterDavResources(sequenceNumber).map(this::getChangeSetFromDavResource))
        .orElse(ChangeSet.empty(sequenceNumber));
  }

  private ChangeSet getChangeSetFromDavResource(DavResource davResource) {
    try {
      return getChangeSetFromInputStream(getSequenceFromFileName(davResource.fileName()),
          davResource.get(MIMETYPE_JSON).byteStream());
    } catch (IOException | at.bitfire.dav4android.exception.HttpException | DavException e) {
      return ChangeSet.failed;
    }
  }

  private Stream<DavResource> filterDavResources(long sequenceNumber) throws IOException {
    return Stream.of(webDavClient.getFolderMembers(accountUuid))
        .filter(davResource -> isNewerJsonFile(sequenceNumber, davResource.fileName()));
  }

  @NonNull
  @Override
  protected String getSharedPreferencesName() {
    return "webdav_backend";
  }

  @NonNull
  @Override
  protected InputStream getInputStreamForPicture(String relativeUri) throws IOException {
    return getInputStream(accountUuid, relativeUri);
  }

  @Override
  public InputStream getInputStreamForBackup(String backupFile) throws IOException {
    return getInputStream(BACKUP_FOLDER_NAME, backupFile);
  }

  private InputStream getInputStream(String folderName, String resourceName) throws IOException {
    try {
      return webDavClient.getResource(folderName, resourceName).get("*/*").byteStream();
    } catch (at.bitfire.dav4android.exception.HttpException | DavException e) {
      throw new IOException(e);
    }
  }

  @Override
  protected void saveUriToAccountDir(String fileName, Uri uri) throws IOException {
    saveUriToFolder(fileName, uri, accountUuid);
  }

  private void saveUriToFolder(String fileName, Uri uri, String folder) throws IOException {
    InputStream in = MyApplication.getInstance().getContentResolver()
        .openInputStream(uri);
    if (in == null) {
      throw new IOException("Could not read " + uri.toString());
    }
    webDavClient.upload(folder, fileName, toByteArray(in),
        MediaType.parse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            getFileExtension(fileName))));
  }

  @Override
  public void storeBackup(Uri uri) throws IOException {
    webDavClient.mkCol(BACKUP_FOLDER_NAME);
    saveUriToFolder(uri.getLastPathSegment(), uri, BACKUP_FOLDER_NAME);
  }

  @NonNull
  @Override
  public List<String> getStoredBackups() throws IOException {
    return Stream.of(webDavClient.getFolderMembers(BACKUP_FOLDER_NAME))
        .map(DavResource::fileName)
        .toList();
  }

  @Override
  protected long getLastSequence(long start) throws IOException {
    return filterDavResources(start)
        .map(davResource -> getSequenceFromFileName(davResource.fileName()))
        .max(this::compareInt)
        .orElse(start);
  }

  @Override
  void saveFileContents(String fileName, String fileContents, String mimeType) throws IOException {
    try {
      webDavClient.upload(accountUuid, fileName, fileContents,
          MediaType.parse(mimeType + "; charset=utf-8"));
    } catch (HttpException e) {
      throw e.getCause() instanceof IOException ? ((IOException) e.getCause()) : new IOException(e);
    }
  }

  @Override
  public boolean unlock() {
    if (fallbackToClass1) {
      try {
        getLockFile().delete(null);
        return true;
      } catch (IOException | at.bitfire.dav4android.exception.HttpException e) {
        return false;
      }
    } else {
      return webDavClient.unlock(accountUuid);
    }
  }

  @NonNull
  @Override
  public Stream<AccountMetaData> getRemoteAccountList() throws IOException {
    return Stream.of(webDavClient.getFolderMembers(null))
        .filter(LockableDavResource::isCollection)
        .map(davResource -> webDavClient.getResource(davResource.location, ACCOUNT_METADATA_FILENAME))
        .filter(davResoure -> {
          try {
            return davResoure.exists();
          } catch (HttpException e) {
            return false;
          }
        })
        .map(this::getAccountMetaDataFromDavResource)
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  private Optional<AccountMetaData> getAccountMetaDataFromDavResource(LockableDavResource lockableDavResource) {
    try {
      return getAccountMetaDataFromInputStream(lockableDavResource.get(MIMETYPE_JSON).byteStream());
    } catch (DavException | at.bitfire.dav4android.exception.HttpException | IOException e) {
      return Optional.empty();
    }
  }
}
