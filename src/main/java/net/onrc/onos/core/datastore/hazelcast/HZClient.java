package net.onrc.onos.core.datastore.hazelcast;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;

import net.onrc.onos.core.datagrid.HazelcastDatagrid;
import net.onrc.onos.core.datastore.IKVClient;
import net.onrc.onos.core.datastore.IKVTable;
import net.onrc.onos.core.datastore.IKVTable.IKVEntry;
import net.onrc.onos.core.datastore.IKVTableID;
import net.onrc.onos.core.datastore.IMultiEntryOperation;
import net.onrc.onos.core.datastore.IMultiEntryOperation.OPERATION;
import net.onrc.onos.core.datastore.IMultiEntryOperation.STATUS;
import net.onrc.onos.core.datastore.ObjectDoesntExistException;
import net.onrc.onos.core.datastore.ObjectExistsException;
import net.onrc.onos.core.datastore.WrongVersionException;
import net.onrc.onos.core.datastore.hazelcast.HZTable.VersionedValue;
import net.onrc.onos.core.datastore.internal.IModifiableMultiEntryOperation;
import net.onrc.onos.core.datastore.utils.ByteArrayUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;

/**
 * Hazelcast implementation of datastore IKVClient.
 */
public final class HZClient implements IKVClient {
    private static final Logger log = LoggerFactory.getLogger(HZClient.class);

    static final long VERSION_NONEXISTENT = 0L;

    private static final String MAP_PREFIX = "datastore://";

    private static final String BASE_CONFIG_FILENAME = System.getProperty("net.onrc.onos.core.datastore.hazelcast.baseConfig", "conf/hazelcast.xml");
    private static final String HAZELCAST_DEFAULT_XML = "conf/hazelcast.default.xml";

    // XXX Remove this mode at some point
    private static boolean useClientMode = Boolean.parseBoolean(System.getProperty("net.onrc.onos.core.datastore.hazelcast.clientMode", "true"));

    // Note: xml configuration will overwrite this value if present
    private static int backupCount = Integer.parseInt(System.getProperty("net.onrc.onos.core.datastore.hazelcast.backupCount", "3"));

    private final HazelcastInstance hazelcastInstance;

    private static final HZClient THE_INSTANCE = new HZClient();

    /**
     * Get DataStoreClient implemented on Hazelcast.
     *
     * @return HZClient
     */
    public static HZClient getClient() {
        return THE_INSTANCE;
    }

    /**
     * Default constructor.
     * <p/>
     * Get or create the Hazelcast Instance to use for datastore.
     */
    private HZClient() {
        // Try to get the existing HZ instance in JVM if possible.
        HazelcastInstance instance = Hazelcast.getHazelcastInstanceByName(HazelcastDatagrid.ONOS_HAZELCAST_INSTANCE);
        if (instance == null) {
            log.error("Failed to get the Hazelcast instance in JVM. "
                    + "Probably DataStoreClient was requested before "
                    + "IDatagridService was started "
                    + "or running as part of unit tests. "
                    + "Creating instance on it's own.");
            instance = getFallbackHZinstance(BASE_CONFIG_FILENAME);
        }
        hazelcastInstance = instance;
    }

    /**
     * Get or create the hazelcast instance to use for datastore, when existing
     * Hazelcast instance cannot be retrieved.
     * <p/>
     *
     * @param hazelcastConfigFileName Hazelcast configuration to use when creating a
     * @return HazelcastInstance to use for datastore
     */
    private static HazelcastInstance getFallbackHZinstance(final String hazelcastConfigFileName) {
        Config baseHzConfig = null;
        try {
            baseHzConfig = new FileSystemXmlConfig(hazelcastConfigFileName);
        } catch (FileNotFoundException e) {
            log.error("Error opening Hazelcast XML configuration. File not found: " + hazelcastConfigFileName, e);
            // Fallback mechanism to support running unit test without setup.
            log.error("Falling back to default Hazelcast XML {}", HAZELCAST_DEFAULT_XML);
            try {
                baseHzConfig = new FileSystemXmlConfig(HAZELCAST_DEFAULT_XML);
            } catch (FileNotFoundException e2) {
                log.error("Error opening fall back Hazelcast XML configuration. "
                        + "File not found: " + HAZELCAST_DEFAULT_XML, e2);

                // intentionally throwing Exception "e" thrown from non-fallback
                // Hazelcast configuration loading.
                throw new Error("Cannot find Hazelcast configuration: " + hazelcastConfigFileName, e);
            }
        }

        // use xml config if present, if not use System.property
        MapConfig mapConfig = baseHzConfig.getMapConfigs().get(MAP_PREFIX + "*");
        if (mapConfig != null) {
            backupCount = mapConfig.getBackupCount();
        }

        HazelcastInstance instance = null;
        // TODO Client mode should be removed at some point.
        // we can get HZ instance used by ONOS using getHazelcastInstanceByName
        if (useClientMode) {
            log.info("Configuring Hazelcast datastore as Client mode");
            ClientConfig clientConfig = new ClientConfig();
            final int port = baseHzConfig.getNetworkConfig().getPort();

            String server = System.getProperty("net.onrc.onos.core.datastore.hazelcast.client.server", "localhost");
            clientConfig.addAddress(server + ":" + port);

            // client mode connection limit.
            // set to 0 for fast fall back to Instance mode.
            String sAttempts = System.getProperty("net.onrc.onos.core.datastore.hazelcast.client.attemptLimit");
            if (sAttempts != null) {
                clientConfig.setConnectionAttemptLimit(Integer.parseInt(sAttempts));
            }

            // copy group config from base Hazelcast configuration
            clientConfig.getGroupConfig().setName(baseHzConfig.getGroupConfig().getName());
            clientConfig.getGroupConfig().setPassword(baseHzConfig.getGroupConfig().getPassword());

            // TODO We probably need to figure out what else need to be
            // derived from baseConfig

            registerSerializer(clientConfig.getSerializationConfig());

            log.info("Starting Hazelcast datastore client for [{}]", clientConfig.getAddressList());

            try {
                instance = HazelcastClient.newHazelcastClient(clientConfig);
                if (!instance.getCluster().getMembers().isEmpty()) {
                    log.debug("Members in cluster: " + instance.getCluster().getMembers());
                    return instance;
                }
                log.info("Failed to find cluster member, falling back to Instance mode");
            } catch (IllegalStateException e) {
                log.info("Failed to initialize HazelcastClient, falling back to Instance mode");
            }
            useClientMode = false;
            instance = null;
        }
        log.info("Configuring Hazelcast datastore as Instance mode");

        // To run 2 Hazelcast instance in 1 JVM,
        // we probably need to something like below
        //int port = hazelcastConfig.getNetworkConfig().getPort();
        //hazelcastConfig.getNetworkConfig().setPort(port+1);

        registerSerializer(baseHzConfig.getSerializationConfig());

        return Hazelcast.newHazelcastInstance(baseHzConfig);
    }

    /**
     * Register serializer for VersionedValue class used to imitate value version.
     *
     * @param config SerializationConfig to add VersionedValueSerializableFactory.
     */
    private static void registerSerializer(final SerializationConfig config) {
        // TODO remove this function at some point.
        // This method is no longer required, if equibalent to the following
        // is defined in hazelcast.xml
        config.addDataSerializableFactoryClass(
                VersionedValueSerializableFactory.FACTORY_ID,
                VersionedValueSerializableFactory.class);
    }

    @Override
    public IKVTable getTable(final String tableName) {
        IMap<byte[], VersionedValue> map = hazelcastInstance.getMap(MAP_PREFIX + tableName);

        if (!useClientMode) {
            // config only available in Instance Mode
            // Client Mode must rely on hazelcast.xml to be properly configured.
            MapConfig config = hazelcastInstance.getConfig().getMapConfig(MAP_PREFIX + tableName);
            // config for this map to be strong consistent
            if (config.isReadBackupData()) {
                config.setReadBackupData(false);
            }
            if (config.isNearCacheEnabled()) {
                config.getNearCacheConfig().setMaxSize(0);
            }

            if (config.getBackupCount() != backupCount) {
                config.setAsyncBackupCount(0);
                config.setBackupCount(backupCount);
            }
        }

        return new HZTable(tableName, map);
    }

    @Override
    public void dropTable(final IKVTable table) {
        ((HZTable) table).getBackendMap().clear();
    }

    @Override
    public long create(final IKVTableID tableId, final byte[] key, final byte[] value)
            throws ObjectExistsException {
        IKVTable table = (IKVTable) tableId;
        return table.create(key, value);
    }

    @Override
    public long forceCreate(final IKVTableID tableId, final byte[] key, final byte[] value) {
        IKVTable table = (IKVTable) tableId;
        return table.forceCreate(key, value);
    }

    @Override
    public IKVEntry read(final IKVTableID tableId, final byte[] key)
            throws ObjectDoesntExistException {
        IKVTable table = (IKVTable) tableId;
        return table.read(key);
    }

    @Override
    public long update(final IKVTableID tableId, final byte[] key, final byte[] value,
                       final long version) throws ObjectDoesntExistException,
            WrongVersionException {
        IKVTable table = (IKVTable) tableId;
        return table.update(key, value, version);
    }

    @Override
    public long update(final IKVTableID tableId, final byte[] key, final byte[] value)
            throws ObjectDoesntExistException {
        IKVTable table = (IKVTable) tableId;
        return table.update(key, value);
    }

    @Override
    public long delete(final IKVTableID tableId, final byte[] key, final long version)
            throws ObjectDoesntExistException, WrongVersionException {
        IKVTable table = (IKVTable) tableId;
        return table.delete(key, version);
    }

    @Override
    public long forceDelete(final IKVTableID tableId, final byte[] key) {
        IKVTable table = (IKVTable) tableId;
        return table.forceDelete(key);
    }

    @Override
    public Iterable<IKVEntry> getAllEntries(final IKVTableID tableId) {
        IKVTable table = (IKVTable) tableId;
        return table.getAllEntries();
    }

    @Override
    public IMultiEntryOperation createOp(final IKVTableID tableId, final byte[] key,
                                         final byte[] value) {
        return new HZMultiEntryOperation((HZTable) tableId, key, value, HZClient.VERSION_NONEXISTENT, OPERATION.CREATE);
    }

    @Override
    public IMultiEntryOperation forceCreateOp(final IKVTableID tableId, final byte[] key,
                                              final byte[] value) {
        return new HZMultiEntryOperation((HZTable) tableId, key, value, HZClient.VERSION_NONEXISTENT, OPERATION.FORCE_CREATE);
    }

    @Override
    public IMultiEntryOperation readOp(final IKVTableID tableId, final byte[] key) {
        return new HZMultiEntryOperation((HZTable) tableId, key, OPERATION.READ);
    }

    @Override
    public IMultiEntryOperation updateOp(final IKVTableID tableId, final byte[] key,
                                         final byte[] value, final long version) {
        return new HZMultiEntryOperation((HZTable) tableId, key, value, version, OPERATION.UPDATE);
    }

    @Override
    public IMultiEntryOperation deleteOp(final IKVTableID tableId, final byte[] key,
                                         final byte[] value, final long version) {
        return new HZMultiEntryOperation((HZTable) tableId, key, value, version, OPERATION.DELETE);
    }

    @Override
    public IMultiEntryOperation forceDeleteOp(final IKVTableID tableId, final byte[] key) {
        return new HZMultiEntryOperation((HZTable) tableId, key, OPERATION.FORCE_DELETE);
    }

    @Override
    public boolean multiDelete(final Collection<IMultiEntryOperation> ops) {
        boolean failExists = false;
        for (IMultiEntryOperation op : ops) {
            HZMultiEntryOperation mop = (HZMultiEntryOperation) op;
            switch (mop.getOperation()) {
                case DELETE:
                    try {
                        final long version = delete(mop.getTableId(), mop.getKey(), mop.getVersion());
                        mop.setVersion(version);
                        mop.setStatus(STATUS.SUCCESS);
                    } catch (ObjectDoesntExistException | WrongVersionException e) {
                        log.error(mop + " failed.", e);
                        mop.setStatus(STATUS.FAILED);
                        failExists = true;
                    }
                    break;
                case FORCE_DELETE:
                    final long version = forceDelete(mop.getTableId(), mop.getKey());
                    mop.setVersion(version);
                    mop.setStatus(STATUS.SUCCESS);
                    break;
                default:
                    throw new UnsupportedOperationException(mop.toString());
            }
        }
        return failExists;
    }

    @Override
    public boolean multiWrite(final List<IMultiEntryOperation> ops) {
        // there may be room to batch to improve performance
        boolean failExists = false;
        for (IMultiEntryOperation op : ops) {
            IModifiableMultiEntryOperation mop = (IModifiableMultiEntryOperation) op;
            switch (mop.getOperation()) {
                case CREATE:
                    try {
                        long version = create(mop.getTableId(), mop.getKey(), mop.getValue());
                        mop.setVersion(version);
                        mop.setStatus(STATUS.SUCCESS);
                    } catch (ObjectExistsException e) {
                        log.error(mop + " failed.", e);
                        mop.setStatus(STATUS.FAILED);
                        failExists = true;
                    }
                    break;
                case FORCE_CREATE: {
                    final long version = forceCreate(mop.getTableId(), mop.getKey(), mop.getValue());
                    mop.setVersion(version);
                    mop.setStatus(STATUS.SUCCESS);
                    break;
                }
                case UPDATE:
                    try {
                        long version = update(mop.getTableId(), mop.getKey(), mop.getValue(), mop.getVersion());
                        mop.setVersion(version);
                        mop.setStatus(STATUS.SUCCESS);
                    } catch (ObjectDoesntExistException | WrongVersionException e) {
                        log.error(mop + " failed.", e);
                        mop.setStatus(STATUS.FAILED);
                        failExists = true;
                    }
                    break;
                default:
                    throw new UnsupportedOperationException(mop.toString());
            }
        }
        return failExists;
    }

    @Override
    public boolean multiRead(final Collection<IMultiEntryOperation> ops) {
        boolean failExists = false;
        for (IMultiEntryOperation op : ops) {
            IModifiableMultiEntryOperation mop = (IModifiableMultiEntryOperation) op;
            HZTable table = (HZTable) op.getTableId();
            ((HZMultiEntryOperation) mop.getActualOperation()).setFuture(table.getBackendMap().getAsync(op.getKey()));
        }
        for (IMultiEntryOperation op : ops) {
            IModifiableMultiEntryOperation mop = (IModifiableMultiEntryOperation) op;
            if (!mop.hasSucceeded()) {
                failExists = true;
            }
        }

        return failExists;
    }

    @Override
    public long getVersionNonexistant() {
        return VERSION_NONEXISTENT;
    }

    private String getCounterName(final IKVTableID tableId, final byte[] key) {
        StringBuilder buf = new StringBuilder(tableId.getTableName());
        buf.append('@');
        ByteArrayUtil.toHexStringBuilder(key, ":", buf);
        return buf.toString();
    }

    private IAtomicLong getAtomicLong(final IKVTableID tableId, final byte[] key) {
        // TODO we probably want to implement some sort of caching
        return hazelcastInstance.getAtomicLong(getCounterName(tableId, key));
    }

    /**
     * {@inheritDoc}
     * <p />
     * Warning: The counter is a different object from {@code key} entry on
     * IKVTable with {@code tableId}. You cannot use table API to read/write
     * counters.
     *
     * @param tableId Only getTableName() will be used.
     * @param key tableId + key will be used as Counter name
     */
    @Override
    public void createCounter(final IKVTableID tableId,
                              final byte[] key, final long initialValue)
                                      throws ObjectExistsException {

        IAtomicLong counter = getAtomicLong(tableId, key);
        // Assumption here is that AtomicLong is initialized to 0L
        final boolean success = counter.compareAndSet(0L, initialValue);
        if (!success) {
            throw new ObjectExistsException("Atomic counter "
                    + getCounterName(tableId, key)
                    + " already exist with value:" + counter.get());
        }
    }

    @Override
    public void setCounter(final IKVTableID tableId,
                           final byte[] key, final long value) {

        IAtomicLong counter = getAtomicLong(tableId, key);
        counter.set(value);
    }

    @Override
    public long incrementCounter(final IKVTableID tableId,
                                 final byte[] key, final long incrementValue) {

        IAtomicLong counter = getAtomicLong(tableId, key);
        return counter.addAndGet(incrementValue);
    }

    @Override
    public void destroyCounter(final IKVTableID tableId, final byte[] key) {
        IAtomicLong counter = getAtomicLong(tableId, key);
        counter.destroy();

    }

    @Override
    public long getCounter(final IKVTableID tableId, final byte[] key)
            throws ObjectDoesntExistException {

        IAtomicLong counter = getAtomicLong(tableId, key);
        return counter.get();
    }

}
