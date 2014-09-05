package net.onrc.onos.core.datastore.ramcloud;

import net.onrc.onos.core.datastore.IKVTable;
import net.onrc.onos.core.datastore.IKVTableID;
import net.onrc.onos.core.datastore.ObjectDoesntExistException;
import net.onrc.onos.core.datastore.ObjectExistsException;
import net.onrc.onos.core.datastore.WrongVersionException;
import net.onrc.onos.core.datastore.utils.ByteArrayUtil;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to represent a Table in RAMCloud.
 */
public class RCTable implements IKVTable {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(RCTable.class);

    public static class Entry implements IKVEntry {
        final byte[] key;
        byte[] value;
        long version;

        public Entry(final byte[] key, final byte[] value, final long version) {
            this.key = key.clone();
            this.setValue(value);
            this.setVersion(version);
        }

        public Entry(final byte[] key) {
            this(key, null, RCClient.VERSION_NONEXISTENT);
        }

        @Override
        public byte[] getKey() {
            return key.clone();
        }

        @Override
        public byte[] getValue() {
            return ArrayUtils.clone(value);
        }

        @Override
        public long getVersion() {
            return version;
        }

        void setValue(byte[] value) {
            this.value = ArrayUtils.clone(value);
        }

        void setVersion(long version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return "[Entry key=" + ByteArrayUtil.toHexStringBuilder(key, ":") + ", value="
                    + ByteArrayUtil.toHexStringBuilder(value, ":") + ", version=" + version + "]";
        }
    }

    private final RCTableID rcTableId;

    /**
     * {@code rcTableName} must be unique cluster wide.
     *
     * @param rcTableName RAMCloud table name
     */
    RCTable(final String rcTableName) {
        this.rcTableId = new RCTableID(rcTableName);

        // Trigger RAMCloud ID allocation. If lazy allocation is OK, remove.
        this.rcTableId.getTableID();
    }

    @Override
    public IKVTableID getTableId() {
        return this.rcTableId;
    }

    public String getTableName() {
        return this.rcTableId.getTableName();
    }

    @Override
    public long create(final byte[] key, final byte[] value)
            throws ObjectExistsException {

        return RCClient.getClient().create(this.rcTableId, key, value);
    }

    @Override
    public long forceCreate(final byte[] key, final byte[] value) {
        return RCClient.getClient().forceCreate(rcTableId, key, value);
    }

    @Override
    public IKVEntry read(final byte[] key) throws ObjectDoesntExistException {
        return RCClient.getClient().read(rcTableId, key);
    }

    @Override
    public long update(final byte[] key, final byte[] value, final long version)
            throws ObjectDoesntExistException, WrongVersionException {

        return RCClient.getClient().update(rcTableId, key, value, version);
    }

    @Override
    public long update(final byte[] key, final byte[] value)
            throws ObjectDoesntExistException {

        return RCClient.getClient().update(rcTableId, key, value);
    }

    @Override
    public long delete(final byte[] key, final long version)
            throws ObjectDoesntExistException, WrongVersionException {

        return RCClient.getClient().delete(rcTableId, key, version);
    }

    @Override
    public long forceDelete(final byte[] key) {
        return RCClient.getClient().forceDelete(rcTableId, key);
    }

    @Override
    public Iterable<IKVEntry> getAllEntries() {
        return RCClient.getClient().getAllEntries(this.getTableId());
    }

    @Override
    public long getVersionNonexistant() {
        return RCClient.VERSION_NONEXISTENT;
    }

}
