package org.totschnig.myexpenses.sync;

import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;

import com.annimon.stream.Exceptional;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.drive.model.File;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.sync.json.AccountMetaData;
import org.totschnig.myexpenses.sync.json.ChangeSet;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.io.StreamReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class GoogleDriveBackendProvider extends AbstractSyncBackendProvider {
  public static final String KEY_GOOGLE_ACCOUNT_EMAIL = "googleAccountEmail";
  private static final String ACCOUNT_METADATA_CURRENCY_KEY = "accountMetadataCurrency";
  private static final String ACCOUNT_METADATA_COLOR_KEY = "accountMetadataColor";
  private static final String ACCOUNT_METADATA_UUID_KEY = "accountMetadataUuid";
  private static final String ACCOUNT_METADATA_OPENING_BALANCE_KEY = "accountMetadataOpeningBalance";
  private static final String ACCOUNT_METADATA_DESCRIPTION_KEY = "accountMetadataDescription";
  private static final String ACCOUNT_METADATA_TYPE_KEY = "accountMetadataType";
  private static final String LOCK_TOKEN_KEY = KEY_LOCK_TOKEN;
  private static final String IS_BACKUP_FOLDER = "isBackupFolder";
  public static final String IS_SYNC_FOLDER = "isSyncFolder";
  private String folderId;
  private File baseFolder, accountFolder;

  private DriveServiceHelper driveServiceHelper;

  GoogleDriveBackendProvider(Context context, android.accounts.Account account, AccountManager accountManager) throws SyncParseException {
    super(context);
    folderId = accountManager.getUserData(account, GenericAccountService.KEY_SYNC_PROVIDER_URL);
    if (folderId == null) {
      throw new SyncParseException("Drive folder not set");
    }
    try {
      driveServiceHelper = new DriveServiceHelper(context, accountManager.getUserData(account, KEY_GOOGLE_ACCOUNT_EMAIL));
    } catch (Exception e) {
      throw new SyncParseException(e);
    }
  }

  @NonNull
  @Override
  protected String getSharedPreferencesName() {
    return "google_drive_backend";
  }

  @Override
  protected String readEncryptionToken() throws IOException {
    requireBaseFolder();
    try {
      return new StreamReader(getInputStream(baseFolder, ENCRYPTION_TOKEN_FILE_NAME)).read();
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public Exceptional<Void> setUp(String authToken, String encryptionPassword, boolean create) {
    final Exceptional<Void> result = super.setUp(authToken, encryptionPassword, create);
    final Throwable exception = result.getException();
    if (exception instanceof UserRecoverableAuthIOException) {
      //User has been signed out from Google account, he needs to log in again
      //Simply launching the intent provided by UserRecoverableAuthException,
      //prompts user to sign in, but not to recreate the account
      return Exceptional.of(new SyncParseException(((UserRecoverableAuthIOException) exception).getCause()));
    }
    return result;
  }

  @Override
  protected boolean isEmpty() throws IOException {
    return driveServiceHelper.listChildren(baseFolder).isEmpty();
  }

  @NonNull
  @Override
  protected InputStream getInputStreamForPicture(String relativeUri) throws IOException {
    return getInputStream(accountFolder, relativeUri);
  }

  @Override
  public InputStream getInputStreamForBackup(String backupFile) throws IOException {
    final File backupFolder = getBackupFolder(false);
    if (backupFolder != null) {
      return getInputStream(backupFolder, backupFile);
    } else {
      throw new IOException("No backup folder found");
    }
  }

  private InputStream getInputStream(File folder, String title) throws IOException {
    return driveServiceHelper.downloadFile(folder, title);
  }

  @Override
  protected void saveUriToAccountDir(String fileName, Uri uri) throws IOException {
    saveUriToFolder(fileName, uri, accountFolder, true);
  }

  private void saveUriToFolder(String fileName, Uri uri, File driveFolder, boolean maybeEncrypt) throws IOException {
    InputStream in = MyApplication.getInstance().getContentResolver().openInputStream(uri);
    if (in == null) {
      throw new IOException("Could not read " + uri.toString());
    }
    saveInputStream(fileName, maybeEncrypt ? maybeEncrypt(in) : in, getMimeType(fileName), driveFolder);
    in.close();
  }

  @Override
  public void storeBackup(Uri uri, String fileName) throws IOException {
    saveUriToFolder(fileName, uri, getBackupFolder(true), false);
  }

  @NonNull
  @Override
  public List<String> getStoredBackups() throws IOException {
    List<String> result = new ArrayList<>();
    final File backupFolder = getBackupFolder(false);
    if (backupFolder != null) {
      result = Stream.of(driveServiceHelper.listChildren(backupFolder))
          .map(File::getName)
          .toList();
    }
    return result;
  }

  @Override
  protected SequenceNumber getLastSequence(SequenceNumber start) throws IOException {
    final Comparator<File> resourceComparator = (o1, o2) -> Utils.compare(getSequenceFromFileName(o1.getName()), getSequenceFromFileName(o2.getName()));

    Optional<File> lastShardOptional =
        Stream.of(driveServiceHelper.listFolders(accountFolder))
            .filter(file -> isAtLeastShardDir(start.shard, file.getName()))
            .max(resourceComparator);

    List<File> lastShard;
    int lastShardInt, reference;
    if (lastShardOptional.isPresent()) {
      lastShard = driveServiceHelper.listChildren(lastShardOptional.get());
      lastShardInt = getSequenceFromFileName(lastShardOptional.get().getName());
      reference = lastShardInt == start.shard ? start.number : 0;
    } else {
      if (start.shard > 0) return start;
      lastShard = driveServiceHelper.listChildren(accountFolder);
      lastShardInt = 0;
      reference = start.number;
    }
    SequenceNumber result = Stream.of(lastShard)
        .filter(metadata -> isNewerJsonFile(reference, metadata.getName()))
        .max(resourceComparator)
        .map(metadata -> new SequenceNumber(lastShardInt, getSequenceFromFileName(metadata.getName())))
        .orElse(start);
    return result;
  }

  @Override
  void saveFileContentsToBase(String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    saveFileContents(baseFolder, fileName, fileContents, mimeType, maybeEncrypt);
  }

  @Override
  void saveFileContentsToAccountDir(String folder, String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    File driveFolder;
    if (folder == null) {
      driveFolder = accountFolder;
    } else {
      driveFolder = getSubFolder(folder);
      if (driveFolder == null) {
        driveFolder = driveServiceHelper.createFolder(accountFolder.getId(), folder, null);
      }
    }
    saveFileContents(driveFolder, fileName, fileContents, mimeType, maybeEncrypt);
  }

  private void saveFileContents(File driveFolder, String fileName, String fileContents, String mimeType, boolean maybeEncrypt) throws IOException {
    InputStream contents = toInputStream(fileContents, maybeEncrypt);
    saveInputStream(fileName, contents, mimeType, driveFolder);
    contents.close();
  }

  @Override
  protected String getExistingLockToken() {
    final Map<String, String> appProperties = accountFolder.getAppProperties();
    return appProperties != null ? appProperties.get(LOCK_TOKEN_KEY) : null;
  }

  @Override
  protected void writeLockToken(String lockToken) throws IOException {
    driveServiceHelper.setMetadataProperty(accountFolder.getId(), LOCK_TOKEN_KEY, lockToken);
  }

  private void saveInputStream(String fileName, InputStream contents, String mimeType, File driveFolder) throws IOException {
    File file = driveServiceHelper.createFile(driveFolder.getId(), fileName, mimeType, null);
    driveServiceHelper.saveFile(file.getId(), mimeType, contents);
  }

  @Override
  public void withAccount(Account account) throws IOException {
    setAccountUuid(account);
    writeAccount(account, false);
  }

  @Override
  protected void writeAccount(Account account, boolean update) throws IOException {
    accountFolder = getExistingAccountFolder(account.uuid);
    if (update || accountFolder == null ) {
      if (accountFolder == null) {
        accountFolder = driveServiceHelper.createFolder(baseFolder.getId(), accountUuid, null);
        createWarningFile();
      }
      saveFileContentsToAccountDir(null, getAccountMetadataFilename(), buildMetadata(account), getMimetypeForData(), true);
    }
  }

  @Override
  public Optional<AccountMetaData> readAccountMetaData() {
    return getAccountMetaDataFromDriveMetadata(accountFolder);
  }

  @Override
  public void resetAccountData(String uuid) throws IOException {
    File existingAccountFolder = getExistingAccountFolder(uuid);
    if (existingAccountFolder != null) {
      driveServiceHelper.delete(existingAccountFolder.getId());
    }
  }

  @Override
  @NonNull
  public Optional<ChangeSet> getChangeSetSince(SequenceNumber sequenceNumber, Context context) throws IOException {
    File shardFolder;
    if (sequenceNumber.shard == 0) {
      shardFolder = accountFolder;
    } else {
      shardFolder = getSubFolder("_" + sequenceNumber.shard);
      if (shardFolder == null) throw new IOException("shard folder not found");
    }
    List<File> fileList = driveServiceHelper.listChildren(shardFolder);

    log().i("Getting data from shard %d", sequenceNumber.shard);
    List<ChangeSet> changeSetList = new ArrayList<>();
    for (File metadata : fileList) {
      if (isNewerJsonFile(sequenceNumber.number, metadata.getName())) {
        log().i("Getting data from file %s", metadata.getName());
        changeSetList.add(getChangeSetFromMetadata(sequenceNumber.shard, metadata));
      }
    }
    int nextShard = sequenceNumber.shard + 1;
    while (true) {
      File nextShardFolder = getSubFolder("_" + nextShard);
      if (nextShardFolder != null) {
        fileList = driveServiceHelper.listChildren(nextShardFolder);
        log().i("Getting data from shard %d", nextShard);
        for (File metadata : fileList) {
          if (isNewerJsonFile(0, metadata.getName())) {
            log().i("Getting data from file %s", metadata.getName());
            changeSetList.add(getChangeSetFromMetadata(nextShard, metadata));
          }
        }
        nextShard++;
      } else {
        break;
      }
    }
    return merge(changeSetList);
  }

  @Nullable
  private File getSubFolder(String shard) throws IOException {
    return driveServiceHelper.getFileByNameAndParent(accountFolder, shard);
  }

  private ChangeSet getChangeSetFromMetadata(int shard, File metadata) throws IOException {
    return getChangeSetFromInputStream(new SequenceNumber(shard, getSequenceFromFileName(metadata.getName())),
        driveServiceHelper.read(metadata.getId()));
  }

  @Override
  public void unlock() throws IOException {
    driveServiceHelper.setMetadataProperty(accountFolder.getId(), LOCK_TOKEN_KEY, null);
  }

  @NonNull
  @Override
  public Stream<AccountMetaData> getRemoteAccountList() throws IOException {
    requireBaseFolder();
    List<File> fileList = driveServiceHelper.listChildren(baseFolder);
    return Stream.of(fileList)
        .map(this::getAccountMetaDataFromDriveMetadata)
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  private Optional<AccountMetaData> getAccountMetaDataFromDriveMetadata(File metadata) {
    if (!driveServiceHelper.isFolder(metadata)) {
      return Optional.empty();
    }
    File accountMetadata;
    try {
      accountMetadata = driveServiceHelper.getFileByNameAndParent(metadata, getAccountMetadataFilename());
    } catch (IOException e) {
      return Optional.empty();
    }
    if (accountMetadata != null) {
      try (InputStream inputStream = driveServiceHelper.read(accountMetadata.getId())) {
        return getAccountMetaDataFromInputStream(inputStream);
      } catch (IOException e) {
        return Optional.empty();
      }
    }

    //legacy
    final Map<String, String> appProperties = metadata.getAppProperties();
    if (appProperties == null) {
      return Optional.empty();
    }
    String uuid = appProperties.get(ACCOUNT_METADATA_UUID_KEY);
    if (uuid == null) {
      Timber.d("UUID property not set");
      return Optional.empty();
    }
    return Optional.of(AccountMetaData.builder()
        .setType(getPropertyWithDefault(appProperties, ACCOUNT_METADATA_TYPE_KEY, AccountType.CASH.name()))
        .setOpeningBalance(getPropertyWithDefault(appProperties, ACCOUNT_METADATA_OPENING_BALANCE_KEY, 0L))
        .setDescription(getPropertyWithDefault(appProperties, ACCOUNT_METADATA_DESCRIPTION_KEY, ""))
        .setColor(getPropertyWithDefault(appProperties, ACCOUNT_METADATA_COLOR_KEY, Account.DEFAULT_COLOR))
        .setCurrency(getPropertyWithDefault(appProperties, ACCOUNT_METADATA_CURRENCY_KEY,
            Utils.getHomeCurrency().code()))
        .setUuid(uuid)
        .setLabel(metadata.getName()).build());
  }

  private String getPropertyWithDefault(Map<String, String> metadata,
                                        String key,
                                        String defaultValue) {
    String result = metadata.get(key);
    return result != null ? result : defaultValue;
  }

  private long getPropertyWithDefault(Map<String, String> metadata,
                                      String key,
                                      long defaultValue) {
    String result = metadata.get(key);
    return result != null ? Long.parseLong(result) : defaultValue;
  }

  private int getPropertyWithDefault(Map<String, String> metadata,
                                     String key,
                                     int defaultValue) {
    String result = metadata.get(key);
    return result != null ? Integer.parseInt(result) : defaultValue;
  }

  private boolean getPropertyWithDefault(Map<String, String> metadata,
                                         String key,
                                         boolean defaultValue) {
    String result = metadata.get(key);
    return result != null ? Boolean.valueOf(result) : defaultValue;
  }

  @Nullable
  private File getBackupFolder(boolean require) throws IOException {
    requireBaseFolder();
    File file = driveServiceHelper.getFileByNameAndParent(baseFolder, BACKUP_FOLDER_NAME);
    if (file != null && file.getAppProperties() != null && getPropertyWithDefault(file.getAppProperties(), IS_BACKUP_FOLDER, false)) {
      return file;
    }
    if (require) {
      Map<String, String> properties = new HashMap<>();
      properties.put(IS_BACKUP_FOLDER, "true");
      return driveServiceHelper.createFolder(baseFolder.getId(), BACKUP_FOLDER_NAME, properties);
    }
    return null;
  }

  private File getExistingAccountFolder(String uuid) throws IOException {
    requireBaseFolder();
    return driveServiceHelper.getFileByNameAndParent(baseFolder, uuid);

  }

  private void requireBaseFolder() throws IOException {
    if (baseFolder == null) {
      baseFolder = driveServiceHelper.getFile(folderId);
    }
  }
}
